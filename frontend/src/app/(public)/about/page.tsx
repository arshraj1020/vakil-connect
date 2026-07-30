import {
  BadgeCheck,
  CalendarCheck,
  KeyRound,
  Lock,
  Search,
  ShieldCheck,
  Star,
  UserCheck,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

/**
 * `/about`.
 *
 * A server component with no data fetching and no client JavaScript. The FAQ
 * uses native <details>/<summary> rather than a Radix accordion: the browser
 * gives correct keyboard handling, screen-reader semantics and open/close state
 * for free, and adding a dependency to expand six paragraphs would ship JS to
 * every visitor for something HTML already does.
 *
 * EVERY CLAIM HERE DESCRIBES BEHAVIOUR THAT EXISTS. Verification is a real admin
 * action; the double-booking guarantee is a database constraint; reviews really
 * are restricted to completed appointments. Nothing is aspirational, and there
 * are no invented statistics, testimonials or partner logos - which would be the
 * easy way to fill an About page and the wrong one.
 */

export const metadata: Metadata = {
  title: "About | VakilConnect",
  description:
    "How VakilConnect connects clients with verified lawyers: search, booking, bar council verification, and how we handle your data.",
};

const STEPS: Array<{ icon: LucideIcon; title: string; body: string }> = [
  {
    icon: Search,
    title: "Find the right lawyer",
    body: "Search by practice area, city, years of experience, consultation fee or rating. Only verified lawyers appear in results, so there is nothing to filter out.",
  },
  {
    icon: CalendarCheck,
    title: "Book a real slot",
    body: "Each lawyer publishes their weekly consulting hours. You pick a time inside those hours and send a request — online or in person, whichever suits.",
  },
  {
    icon: UserCheck,
    title: "The lawyer responds",
    body: "They accept or decline. An accepted appointment is confirmed; you can cancel any appointment that has not already been completed.",
  },
  {
    icon: Star,
    title: "Leave an honest review",
    body: "Once a consultation is marked complete you can rate it. That rating feeds the lawyer's public average and helps the next person choose.",
  },
];

const VERIFICATION: Array<{ title: string; body: string }> = [
  {
    title: "They register with their bar council number",
    body: "Every lawyer account is created with a bar council registration number, which must be unique across the platform. Two accounts cannot claim the same registration.",
  },
  {
    title: "The profile stays invisible until it is checked",
    body: "A new lawyer does not appear in search and cannot receive appointment requests. Attempting to book an unverified lawyer is refused by the server, not merely hidden in the interface.",
  },
  {
    title: "An administrator reviews the registration",
    body: "A person on our team examines the submitted details against the bar council record before approving the profile.",
  },
  {
    title: "The profile goes live",
    body: "Once approved, the lawyer becomes searchable and can start accepting consultations. A verified badge is shown on their profile.",
  },
];

const PRIVACY: Array<{ icon: LucideIcon; title: string; body: string }> = [
  {
    icon: KeyRound,
    title: "Passwords are never stored",
    body: "We keep a one-way BCrypt hash. Nobody at VakilConnect can read, recover or tell you your password — only reset it.",
  },
  {
    icon: Lock,
    title: "Your session is scoped to you",
    body: "Signing in issues a token tied to your account and role. Every request is authorised on the server, so one role cannot reach another's data by editing a URL.",
  },
  {
    icon: ShieldCheck,
    title: "We ask for what the service needs",
    body: "Clients provide a name, email and phone number. Lawyers additionally provide the professional details that clients need in order to choose: registration number, practice areas, experience, fee and office address.",
  },
  {
    icon: BadgeCheck,
    title: "Your contact details are not published",
    body: "A lawyer's public profile shows their professional information. Client details are shared with a lawyer only through an appointment the client themselves booked.",
  },
];

const FAQ: Array<{ question: string; answer: string }> = [
  {
    question: "Does it cost anything to use VakilConnect?",
    answer:
      "Creating a client account, searching, and booking a consultation are all free. VakilConnect does not add a fee or commission. The consultation fee shown on a lawyer's profile is set by that lawyer and paid to them directly — see the pricing page for details.",
  },
  {
    question: "How do I know a lawyer is genuine?",
    answer:
      "Every lawyer in search results has had their bar council registration checked by an administrator. Unverified profiles are not listed and cannot accept appointments, so anyone you can find and book has already been through that check.",
  },
  {
    question: "Can two clients book the same slot?",
    answer:
      "No. A confirmed slot is held by a database constraint, not by application logic that could be raced. If two requests arrive for the same lawyer at the same time, exactly one succeeds.",
  },
  {
    question: "What happens after I request an appointment?",
    answer:
      "The request starts as pending. The lawyer either accepts or rejects it, and you see the change on your appointments page. You can cancel a pending or accepted appointment; one that is already completed, rejected or cancelled cannot be changed.",
  },
  {
    question: "Who can leave a review?",
    answer:
      "Only a client whose appointment with that lawyer was marked complete, and only once per lawyer. That is why ratings on the platform correspond to consultations that actually happened.",
  },
  {
    question: "I am a lawyer. How long does verification take?",
    answer:
      "It is a manual review by an administrator rather than an automated check, so it is not instant. You can complete your profile, set your fee and publish your availability while you wait — none of it is visible to clients until your profile is approved.",
  },
  {
    question: "Can I change my consultation fee or practice areas later?",
    answer:
      "Yes. Your fee, biography, city, office address and practice areas are all editable from your profile at any time, and changes are reflected in search immediately.",
  },
];

export default function AboutPage() {
  return (
    <>
      {/* ------------------------------------------------------------- mission */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,hsl(var(--primary)/0.10),transparent_70%)]"
        />

        <div className="container flex flex-col items-center gap-6 py-20 text-center sm:py-24">
          <h1 className="max-w-3xl text-balance text-4xl font-semibold tracking-tight sm:text-5xl">
            Legal help should not depend on who you happen to know
          </h1>

          <p className="max-w-2xl text-pretty text-lg text-muted-foreground">
            Finding a lawyer in India usually means asking around and hoping the
            recommendation is sound. VakilConnect replaces that with something
            checkable: verified credentials, published fees, real availability,
            and ratings from consultations that actually took place.
          </p>
        </div>
      </section>

      {/* -------------------------------------------------------- how it works */}
      <section
        aria-labelledby="how-it-works"
        className="border-t border-border bg-muted/30"
      >
        <div className="container py-16 sm:py-20">
          <h2
            id="how-it-works"
            className="text-center text-2xl font-semibold tracking-tight"
          >
            How the platform works
          </h2>

          <ol className="mt-10 grid gap-6 sm:grid-cols-2">
            {STEPS.map((step, index) => {
              const Icon = step.icon;

              return (
                <li key={step.title}>
                  <Card className="h-full">
                    <CardContent className="space-y-3 p-6">
                      <div className="flex items-center gap-3">
                        <span
                          className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                          aria-hidden
                        >
                          <Icon className="size-4" />
                        </span>
                        <span className="text-xs font-medium text-muted-foreground">
                          Step {index + 1}
                        </span>
                      </div>

                      <h3 className="font-medium">{step.title}</h3>
                      <p className="text-sm leading-relaxed text-muted-foreground">
                        {step.body}
                      </p>
                    </CardContent>
                  </Card>
                </li>
              );
            })}
          </ol>
        </div>
      </section>

      {/* ---------------------------------------------------------- verification */}
      <section aria-labelledby="verification" className="border-t border-border">
        <div className="container py-16 sm:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <h2
              id="verification"
              className="text-2xl font-semibold tracking-tight"
            >
              How lawyers are verified
            </h2>
            <p className="mt-3 text-muted-foreground">
              Verification is the reason this platform is worth using. It is a
              person checking a registration, not a checkbox.
            </p>
          </div>

          <ol className="mx-auto mt-10 max-w-2xl space-y-6">
            {VERIFICATION.map((step, index) => (
              <li key={step.title} className="flex gap-4">
                <span
                  className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-sm font-medium text-primary"
                  aria-hidden
                >
                  {index + 1}
                </span>
                <div className="space-y-1">
                  <h3 className="font-medium">{step.title}</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {step.body}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* --------------------------------------------------------------- privacy */}
      <section
        aria-labelledby="privacy"
        className="border-t border-border bg-muted/30"
      >
        <div className="container py-16 sm:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <h2 id="privacy" className="text-2xl font-semibold tracking-tight">
              Privacy and security
            </h2>
            <p className="mt-3 text-muted-foreground">
              What we hold, and what we do to protect it.
            </p>
          </div>

          <ul className="mt-10 grid gap-6 md:grid-cols-2">
            {PRIVACY.map((item) => {
              const Icon = item.icon;

              return (
                <li key={item.title} className="space-y-3">
                  <span
                    className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary"
                    aria-hidden
                  >
                    <Icon className="size-4" />
                  </span>
                  <h3 className="font-medium">{item.title}</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {item.body}
                  </p>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      {/* ------------------------------------------------------------------- faq */}
      <section aria-labelledby="faq" className="border-t border-border">
        <div className="container py-16 sm:py-20">
          <h2
            id="faq"
            className="text-center text-2xl font-semibold tracking-tight"
          >
            Frequently asked questions
          </h2>

          <dl className="mx-auto mt-10 max-w-2xl divide-y divide-border border-y border-border">
            {FAQ.map((item) => (
              <div key={item.question}>
                {/*
                 * <details> gives keyboard operation, focus handling and
                 * expanded/collapsed announcement natively. `group` and
                 * `group-open:` drive the chevron without a line of JavaScript.
                 */}
                <details className="group">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 py-5 text-left font-medium transition-colors hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2">
                    <dt>{item.question}</dt>
                    <span
                      aria-hidden
                      className="shrink-0 text-muted-foreground transition-transform group-open:rotate-45"
                    >
                      +
                    </span>
                  </summary>
                  <dd className="pb-5 pr-8 text-sm leading-relaxed text-muted-foreground">
                    {item.answer}
                  </dd>
                </details>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* -------------------------------------------------------------------- cta */}
      <section className="border-t border-border bg-muted/30">
        <div className="container flex flex-col items-center gap-5 py-16 text-center sm:py-20">
          <h2 className="text-2xl font-semibold tracking-tight">
            Ready to find a lawyer?
          </h2>
          <p className="max-w-xl text-muted-foreground">
            Browsing is open to everyone — you only need an account to book.
          </p>

          <div className="mt-2 flex flex-col gap-3 sm:flex-row">
            <Link
              href={ROUTES.LAWYERS}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-6 py-3 text-sm font-medium text-primary-foreground shadow-xs transition-opacity hover:opacity-90"
            >
              <Search className="size-4" aria-hidden />
              Browse lawyers
            </Link>

            <Link
              href={ROUTES.REGISTER}
              className="inline-flex items-center justify-center rounded-lg border border-border px-6 py-3 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-foreground"
            >
              Create an account
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
