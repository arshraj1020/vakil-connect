# Engineering notes

Real defects this project's own tests caught, written up as root cause → fix.
Nothing here is hypothetical or written after the fact for effect — each entry
is a bug that existed in a commit, was caught before merge by a test failing
for the right reason, and was fixed by understanding *why*, not by adjusting
the assertion until it passed.

The pattern worth noticing across all four: every one of them is a bug in the
seam between two things that look identical at a glance — a library's default
behaviour and this project's actual requirement, a proxy and the object it
wraps, a fixture and the thing it's supposed to represent. That's where bugs
live in a codebase with this much test coverage; the obvious mistakes get
caught by the first run.

---

## 1. A ZIP-truncation detector that failed open

**Where:** `DocumentContentTypeDetector` (AI-1, document upload)

**The requirement.** The backend must never trust a client's claimed
`Content-Type` header — a `.exe` renamed to `.docx` has to be rejected by its
actual bytes, not its filename. DOCX files are ZIP archives, so detecting one
means parsing ZIP structure.

**The bug.** The first implementation used `ZipInputStream`, which reads
**local file headers**, streaming from the front of the file. A truncated
DOCX — corrupted mid-upload, or deliberately hand-crafted — still has valid
local file headers for whatever entries survived the truncation, because
those headers appear *before* the corruption point, not after. The detector
read them, saw plausible ZIP entries, and classified a broken file as a valid
DOCX.

```
rejectsTruncatedZip:  expected UNSUPPORTED, got ACCEPTED
```

**Root cause.** ZIP's actual authority on "what entries exist" is the
**central directory**, located via the **End of Central Directory record**
(EOCD) at the *end* of the file. A truncated file has no valid EOCD. Local
file headers are a *duplicate*, forward-scanning index that a conforming
reader (Java's own `ZipInputStream` included) trusts optimistically — which
is fine for reading a file you already trust, and wrong for classifying one
you don't.

**The fix.** Parse the EOCD from the end of the file first, walk the central
directory it points to, and decompress each entry to verify the CRC actually
matches. Only a byte string with a structurally valid EOCD and a
central directory whose entries decompress cleanly is accepted as DOCX.

**Why this one earns its place here.** It's a textbook case of a security
check built on the wrong ground truth. "Parses without an exception" and "is
what it claims to be" are different properties, and a stream-based parser
gives you the first one for free while silently failing to give you the
second when the input is adversarial rather than merely well-formed.

---

## 2. A `@Transactional` self-invocation that would have run silently unguarded

**Where:** `DocumentIngestionServiceImpl` (AI-2, extraction/chunking/embedding pipeline)

**The requirement.** Ingesting a document is three stages: claim it (a
short DB transaction), extract/chunk/embed it (slow — tens of seconds against
local CPU inference, and must **not** hold a database connection for that
whole time), then replace its chunks and mark it READY (another short
transaction). Each of the three DB-touching stages needs its own transaction
boundary.

**The bug, caught during code review before a test even had to find it.**
The natural first draft put all three stages as `@Transactional` methods on
`DocumentIngestionServiceImpl` itself, called from a fourth method on the same
class via `this.claim(...)`, `this.replaceChunks(...)`.

Spring's `@Transactional` is implemented as a **proxy wrapping the bean**.
An external caller goes through the proxy, which opens the transaction. A
call to `this.something()` from *inside* the bean bypasses the proxy
entirely — it's a plain Java method call on the raw object — so no
transaction starts. **The annotation is silently inert.** No exception, no
warning, no log line. The code looks completely correct on inspection.

**Why this is worse than a normal bug.** A test using a single database
connection per test class can pass even with this defect, because
Hibernate's dirty-checking and auto-commit can accidentally produce
similar-looking behaviour for a *simple* case. The failure mode that actually
matters — a crash mid-ingestion leaving the chunk table half-replaced, or a
concurrent request racing an unguarded claim — only shows up under real
concurrency or a real partial failure, which is exactly the scenario nobody
manually tests and exactly the scenario this mechanism exists to protect.

**The fix.** Extract every transactional method onto a **separate bean**,
`IngestionTransactions`, injected into the service rather than inherited by
it. A call from the service to `transactions.claim(...)` now genuinely goes
through Spring's proxy, because the caller and the transactional method live
on different objects. The service itself carries `@Transactional` nowhere,
which is now a *provable* property — the slow stage cannot accidentally
reacquire a connection, because there's no annotation left on that class for
it to inherit from.

`AiPropertiesTest`-style structural checks were added specifically to make
"this class has no `@Transactional` annotation anywhere" and "this bean's
methods are called only from outside the class" checkable facts rather than
things a future refactor could quietly undo.

---

## 3. Ollama's NDJSON stream, and a completion silently truncated to one token

**Where:** `OllamaLlmClient` (AI-0), later re-encountered independently in
`AnalysisJsonParser` (AI-4)

**The requirement.** `LlmClient.complete()` must return the model's full
response text, or throw — never a truncated fragment presented as a
successful completion.

**The bug.** Ollama's `/api/chat` and `/api/generate` endpoints, when called
with `"stream": false`, still occasionally return a response body that is
**newline-delimited JSON** rather than one JSON object — an artifact of how
the server buffers streaming chunks internally. Jackson's default
deserialization behaviour is to parse the **first** JSON value it finds and
silently ignore anything after it, because `FAIL_ON_TRAILING_TOKENS` is
**off by default**.

The practical effect: a five-paragraph answer that happened to arrive as
multiple NDJSON lines would parse as a "successful" response containing only
the first line — often a single word or an empty fragment — with no
exception, no error, nothing to indicate anything had gone wrong. The
completion looked exactly like a real, if terse, answer.

**Root cause.** A JSON parser configured for the common case ("parse this one
object") applied to a wire format that isn't always exactly that.

**The fix.** Enable `FAIL_ON_TRAILING_TOKENS` on the `ObjectMapper` used to
parse the model's response, and add a guard reading Ollama's own `done`
field — a response where `done != true` is treated as incomplete and
retried/failed rather than accepted.

**Why it came back in AI-4.** Building the structured-analysis JSON parser
independently required the exact same decision: a private `ObjectMapper`
with the same flag enabled, and an explicit comment tracing back to this
exact incident, because the failure mode — "the model's reply parses as
JSON if you stop reading early enough" — is structurally identical whether
the extra bytes are a second NDJSON line or a stray sentence of prose after
a fenced code block. Recognising a class of bug rather than a single
instance of one is what stopped this from happening a third time silently.

---

## 4. A password-reset test that looked like a token-uniqueness bug and wasn't

**Where:** `PasswordResetIT.reissueSupersedesPrevious`

**The symptom.** A full `mvn clean test` run reported exactly one failure out
of 767 tests:

```
PasswordResetIT.reissueSupersedesPrevious
expected: not equal but was: <same token value>
```

The test issues a reset link, ages the cooldown, issues a second one, and
asserts the two raw tokens differ — a password-reset token that gets
**reissued** is supposed to **supersede**, not repeat, the previous one.

**Working through the actual hypotheses, in order, before touching anything:**

1. *Is the token generator producing a collision?* No — it draws 256 bits
   from `SecureRandom` per call, with no seeding, no stub, and no shared
   state across calls. A genuine collision is astronomically improbable and
   there's no code path that could make it deterministic.
2. *Is the second request being silently blocked by the resend cooldown, and
   the test is reading the stale first token?* No — and this was provable
   from the test's **own passing assertion just before the failing one**:
   the test first asserts the *first* token was marked invalidated in the
   database. If the second request had been rejected by the cooldown, that
   whole transaction would have rolled back and the first token would still
   show as live. It didn't — so a genuine second issuance really happened.
3. *Is the database failing to supersede the previous token correctly?*
   Ruled out by the same evidence as above — invalidation of the first token
   is exactly what the passing assertion confirms happened.
4. *Is the test's mailbox helper returning a stale email?* This was the
   remaining candidate, and it pointed at test infrastructure rather than
   the feature: `RecordingEmailSender` — the fake that captures outbound
   email instead of sending it — is registered as a singleton bean shared
   by **every integration test class that imports it**, and several of
   those classes share Spring's cached test context across a single JVM
   run. Under a specific Surefire class-execution ordering, state left over
   from an adjacent test class could in principle be read by this one
   before it had been reset.

**What actually happened.** Reproduction was inconclusive: `PasswordResetIT`
run in isolation passed 25/25, and the **full suite re-run afterward passed
767/767** with no code changes. The failure did not recur under either the
default class order or a reversed one.

**The honest conclusion.** This was almost certainly a **transient
environmental artifact** — most plausibly a scheduling or timing interaction
around the shared mailbox singleton across cached Spring contexts, rather
than a defect in password-reset issuance, token hashing, or supersession
logic, all of which the surrounding evidence directly rules out.

**What this entry is really about.** The instinct when a single test fails
is to either weaken the assertion or add a sleep. Both were explicitly
off the table here, for a reason worth stating generally: **a flaky test
that gets "fixed" by loosening what it checks stops being able to catch the
bug it was written for.** The right response to an unreproducible failure
with no identified root cause is to document exactly what was ruled out and
why, leave the assertion exactly as strict as it was, and treat the next
occurrence — if there is one — as a fresh investigation with a stronger
starting hypothesis, not as confirmation of a guess made without evidence.

---

## What these four have in common

None of them are "forgot a null check" bugs. Each is a case where two
things that are usually interchangeable — a stream reader and a format
validator, a method call and a proxy-mediated call, a JSON parser's default
and the wire format it's actually fed, a shared test double and an isolated
one — quietly stopped being interchangeable under a specific, realistic
condition. The fix in every case was understanding *which* of the two
things was actually needed, not making the symptom go away.
