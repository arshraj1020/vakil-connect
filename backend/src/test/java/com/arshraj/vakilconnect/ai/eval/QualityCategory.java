package com.arshraj.vakilconnect.ai.eval;

/**
 * The fixed set of quality properties AI-6 checks for every structured-JSON
 * parser in this codebase (AI-4's {@code AnalysisJsonParser}, AI-5's
 * {@code ComparisonJsonParser}, and any later one).
 *
 * THIS ENUM IS THE COVERAGE CONTRACT, NOT JUST A LABEL. QualityHarnessTest
 * asserts that every parser registers at least one case in EVERY category
 * here - not just that the cases it happens to have all pass. That is the
 * property a normal unit-test suite does not give you: a new parser that
 * forgets to test, say, identity-forgery containment still passes every one
 * of ITS OWN tests, because the missing test simply does not exist. The
 * harness catches that omission by checking coverage against this fixed
 * list, not by trusting that whoever wrote the parser also wrote the right
 * tests for it.
 *
 * Adding a category here is a deliberate decision to require it of every
 * current and future parser - it is not free to add one lightly.
 */
public enum QualityCategory {

    /** A well-formed reply with every field populated parses cleanly. */
    VALID_PARSE,

    /** Empty lists are accepted as a legitimate answer, not an error. */
    EMPTY_LIST_ACCEPTED,

    /** Malformed / non-JSON / trailing-token replies are refused, never salvaged. */
    MALFORMED_REJECTED,

    /** A required field that is absent (or explicit null) is refused, never defaulted. */
    MISSING_FIELD_REJECTED,

    /** A reply that tries to supply document identity is parsed with that identity discarded. */
    IDENTITY_FORGERY_CONTAINED,

    /** A runaway list is clipped to the configured ceiling, keeping model order. */
    LIST_BOUNDED,

    /** An over-long entry is truncated rather than dropped or left unbounded. */
    ENTRY_TRUNCATED,

    /** No rejection or parsed output ever lets the model's raw text leak into a fixed message. */
    NO_CONTENT_LEAK
}
