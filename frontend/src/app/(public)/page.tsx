import {
  BadgeCheck,
  CalendarCheck,
  MessageSquareText,
  Search,
  ShieldCheck,
  Star,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

/**
 * Landing page for `/`.
 *
 * Its absence was the whole of Bug 1: route groups contribute no URL segment,
 * so `(public)` created no page at `/` and Next served its built-in 404 - which
 * every Logo in the application linked to, signed in or out.
 *
 * A server component with no data fetching. Every claim below describes a
 * capability the product actually has; nothing is fabricated, and there are no
 * invented statistics, testimonials or partner logos, which would be the easy
 * way to fill a landing page and the wrong one.
 */

const STEPS: Array<{ icon: LucideIcon; title: string; body: string }> = [
  {
    icon: Search,
    title: "Search",
    body: "Filter verified lawyers by practice area, city, experience and consultation fee.",
  },
  {
    icon: CalendarCheck,
    title: "Book",
    body: "Pick a slot from the lawyer's published weekly availability and request a consultation.",
  },
  {
    icon: MessageSquareText,
    title: "Review",
    body: "Once the consultation is complete, rate it and help the next person choose.",
  },
];

const ASSURANCES: Array<{ icon: LucideIcon; title: string; body: string }> = [
  {
    icon: ShieldCheck,
    title: "Every lawyer is verified",
    body: "An administrator checks each bar council registration before a profile appears in search.",
  },
  {
    icon: BadgeCheck,
    title: "No double bookings",
    body: "Slots are held at the database level, so a confirmed time is genuinely yours.",
  },
  {
    icon: Star,
    title: "Ratings from real consultations",
    body: "Only a client whose appointment was completed can leave a review.",
  },
];

export default function LandingPage() {
  return (
    <>
      {/* ------------------------------------------------------------- hero */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,hsl(var(--primary)/0.10),transparent_70%)]"
        />

        <div className="container flex flex-col items-center gap-6 py-20 text-center sm:py-28">
          <span className="inline-flex items-center gap-2 rounded-full border border-border bg-background/60 px-3 py-1 text-xs font-medium text-muted-foreground">
            <ShieldCheck className="size-3.5 text-primary" aria-hidden />
            Verified lawyers only
          </span>

          <h1 className="max-w-3xl text-balance text-4xl font-semibold tracking-tight sm:text-5xl">
            Find a lawyer you can trust, and book them in minutes
          </h1>

          <p className="max-w-2xl text-pretty text-lg text-muted-foreground">
            VakilConnect brings verified legal professionals and the people who
            need them into one place — search by practice area, compare fees and
            experience, and book a consultation without a single phone call.
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

          <p className="text-xs text-muted-foreground">
            Browsing is open to everyone. An account is needed only to book.
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
            How it works
          </h2>

          <ol className="mt-10 grid gap-6 md:grid-cols-3">
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

      {/* ------------------------------------------------------------ assurances */}
      <section aria-labelledby="why-vakilconnect" className="border-t border-border">
        <div className="container py-16 sm:py-20">
          <h2
            id="why-vakilconnect"
            className="text-center text-2xl font-semibold tracking-tight"
          >
            Why VakilConnect
          </h2>

          <ul className="mt-10 grid gap-6 md:grid-cols-3">
            {ASSURANCES.map((item) => {
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

      {/* ------------------------------------------------------ lawyer call-out */}
      <section className="border-t border-border bg-muted/30">
        <div className="container flex flex-col items-center gap-5 py-16 text-center sm:py-20">
          <h2 className="text-2xl font-semibold tracking-tight">
            Are you a lawyer?
          </h2>
          <p className="max-w-xl text-muted-foreground">
            List your practice, publish your consultation hours, and manage
            every appointment request in one place. Profiles go live once an
            administrator has verified your bar council registration.
          </p>
          <Link
            href={ROUTES.REGISTER}
            className="inline-flex items-center justify-center rounded-lg bg-primary px-6 py-3 text-sm font-medium text-primary-foreground shadow-xs transition-opacity hover:opacity-90"
          >
            Join as a lawyer
          </Link>
        </div>
      </section>
    </>
  );
}
