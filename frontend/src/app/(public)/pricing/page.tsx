import {
  BadgeCheck,
  CircleDollarSign,
  ClipboardCheck,
  Info,
  Search,
  UserPlus,
  Video,
  Wallet,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

/**
 * `/pricing`.
 *
 * THERE ARE NO PLANS, AND THIS PAGE SAYS SO.
 *
 * VakilConnect has no payment integration: no gateway, no invoices, no
 * subscriptions, no commission, and nothing in the backend that moves money.
 * The only monetary figure in the system is `consultationFee`, which each lawyer
 * sets on their own profile and which the platform merely displays so clients
 * can compare.
 *
 * Inventing a tier table would be the obvious way to fill this page and would be
 * a lie about the product. Instead it states plainly what is free, who sets the
 * fee, and that payment is arranged directly between client and lawyer. When
 * billing is built, this page changes - until then it should not imply otherwise.
 *
 * Server component, no data fetching, no client JavaScript.
 */

export const metadata: Metadata = {
  title: "Pricing | VakilConnect",
  description:
    "VakilConnect is free for clients and free for lawyers. Consultation fees are set by each lawyer and paid to them directly.",
};

const FREE_FOR_CLIENTS: string[] = [
  "Creating an account",
  "Searching and filtering verified lawyers",
  "Viewing full profiles, fees and ratings",
  "Requesting and cancelling appointments",
  "Leaving a review after a completed consultation",
];

const FREE_FOR_LAWYERS: string[] = [
  "Creating a lawyer account",
  "Bar council verification",
  "Publishing your profile and practice areas",
  "Setting your own consultation fee",
  "Publishing weekly availability",
  "Accepting, rejecting and completing appointments",
];

const ONBOARDING: Array<{ icon: LucideIcon; title: string; body: string }> = [
  {
    icon: UserPlus,
    title: "Register",
    body: "Sign up as a lawyer with your bar council registration number, practice areas, experience, city and office address. Your consultation fee is part of this step and you can change it at any time.",
  },
  {
    icon: ClipboardCheck,
    title: "Wait for verification",
    body: "An administrator checks your registration against the bar council record. This is a manual review, so it is not instant. Your profile is not visible to clients and cannot receive appointments until it is approved.",
  },
  {
    icon: BadgeCheck,
    title: "Go live",
    body: "Once approved you appear in search with a verified badge, and clients can book the slots you publish. There is no listing fee and no commission on what you charge.",
  },
];

export default function PricingPage() {
  return (
    <>
      {/* --------------------------------------------------------------- header */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,hsl(var(--primary)/0.10),transparent_70%)]"
        />

        <div className="container flex flex-col items-center gap-6 py-20 text-center sm:py-24">
          <span className="inline-flex items-center gap-2 rounded-full border border-border bg-background/60 px-3 py-1 text-xs font-medium text-muted-foreground">
            <Wallet className="size-3.5 text-primary" aria-hidden />
            No plans, no commission
          </span>

          <h1 className="max-w-3xl text-balance text-4xl font-semibold tracking-tight sm:text-5xl">
            VakilConnect is free. Lawyers set their own fees.
          </h1>

          <p className="max-w-2xl text-pretty text-lg text-muted-foreground">
            There are no subscription tiers to compare, because there are none.
            The platform does not charge clients, does not charge lawyers, and
            takes no cut of any consultation.
          </p>
        </div>
      </section>

      {/* ------------------------------------------------------------ who pays */}
      <section
        aria-labelledby="whats-free"
        className="border-t border-border bg-muted/30"
      >
        <div className="container py-16 sm:py-20">
          <h2
            id="whats-free"
            className="text-center text-2xl font-semibold tracking-tight"
          >
            What is free
          </h2>

          <div className="mt-10 grid gap-6 md:grid-cols-2">
            <Card>
              <CardContent className="space-y-4 p-6">
                <div className="flex items-center gap-3">
                  <span
                    className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                    aria-hidden
                  >
                    <Search className="size-4" />
                  </span>
                  <h3 className="font-medium">For clients</h3>
                </div>

                <ul className="space-y-2">
                  {FREE_FOR_CLIENTS.map((item) => (
                    <li
                      key={item}
                      className="flex gap-2 text-sm leading-relaxed text-muted-foreground"
                    >
                      <BadgeCheck
                        className="mt-0.5 size-4 shrink-0 text-primary"
                        aria-hidden
                      />
                      {item}
                    </li>
                  ))}
                </ul>

                <p className="text-sm leading-relaxed">
                  The only thing you pay for is the consultation itself, at the
                  fee shown on the lawyer&apos;s profile.
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="space-y-4 p-6">
                <div className="flex items-center gap-3">
                  <span
                    className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                    aria-hidden
                  >
                    <BadgeCheck className="size-4" />
                  </span>
                  <h3 className="font-medium">For lawyers</h3>
                </div>

                <ul className="space-y-2">
                  {FREE_FOR_LAWYERS.map((item) => (
                    <li
                      key={item}
                      className="flex gap-2 text-sm leading-relaxed text-muted-foreground"
                    >
                      <BadgeCheck
                        className="mt-0.5 size-4 shrink-0 text-primary"
                        aria-hidden
                      />
                      {item}
                    </li>
                  ))}
                </ul>

                <p className="text-sm leading-relaxed">
                  You keep the full consultation fee. VakilConnect does not
                  deduct anything from it.
                </p>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* ----------------------------------------------------- how fees work */}
      <section aria-labelledby="how-fees-work" className="border-t border-border">
        <div className="container py-16 sm:py-20">
          <div className="mx-auto max-w-2xl">
            <h2
              id="how-fees-work"
              className="text-center text-2xl font-semibold tracking-tight"
            >
              How consultation fees work
            </h2>

            <div className="mt-10 space-y-6">
              <div className="flex gap-4">
                <span
                  className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                  aria-hidden
                >
                  <CircleDollarSign className="size-4" />
                </span>
                <div className="space-y-1">
                  <h3 className="font-medium">The lawyer sets the price</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    Each lawyer publishes a single consultation fee on their
                    profile, in rupees, per consultation. It appears in search
                    results and you can filter and sort by it, so the cost is
                    known before you book rather than after.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <span
                  className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                  aria-hidden
                >
                  <Video className="size-4" />
                </span>
                <div className="space-y-1">
                  <h3 className="font-medium">
                    The same fee covers online and in-person
                  </h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    When you book you choose whether the consultation happens
                    online or at the lawyer&apos;s office. The published fee is a
                    single figure and does not vary by mode.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <span
                  className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                  aria-hidden
                >
                  <Wallet className="size-4" />
                </span>
                <div className="space-y-1">
                  <h3 className="font-medium">
                    Payment is arranged directly with the lawyer
                  </h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    VakilConnect does not process payments. We do not collect
                    card details, hold funds, issue invoices or take a
                    commission. How and when you pay is settled between you and
                    the lawyer, exactly as it would be if you had walked into
                    their office.
                  </p>
                </div>
              </div>
            </div>

            {/*
             * Stated plainly rather than buried. A visitor deciding whether to
             * trust the platform with a transaction needs to know that no
             * transaction happens here.
             */}
            <div className="mt-10 flex gap-3 rounded-lg border border-border bg-muted/40 p-5">
              <Info className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
              <div className="space-y-1 text-sm leading-relaxed">
                <p className="font-medium">
                  Online payment is not built yet.
                </p>
                <p className="text-muted-foreground">
                  Booking a consultation through VakilConnect reserves the time
                  and nothing more — no money changes hands on this site. If that
                  changes, this page will say so before it goes live.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------ lawyer onboarding */}
      <section
        aria-labelledby="lawyer-onboarding"
        className="border-t border-border bg-muted/30"
      >
        <div className="container py-16 sm:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <h2
              id="lawyer-onboarding"
              className="text-2xl font-semibold tracking-tight"
            >
              Joining as a lawyer
            </h2>
            <p className="mt-3 text-muted-foreground">
              Three steps, none of which cost anything.
            </p>
          </div>

          <ol className="mt-10 grid gap-6 md:grid-cols-3">
            {ONBOARDING.map((step, index) => {
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

      {/* -------------------------------------------------------------------- cta */}
      <section className="border-t border-border">
        <div className="container flex flex-col items-center gap-5 py-16 text-center sm:py-20">
          <h2 className="text-2xl font-semibold tracking-tight">
            Compare fees before you commit
          </h2>
          <p className="max-w-xl text-muted-foreground">
            Every profile shows the consultation fee, experience and rating up
            front. Browsing costs nothing and needs no account.
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
              Join as a lawyer
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
