import { ChevronRight, MessageSquareText, ShieldCheck, Users } from "lucide-react";
import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

/**
 * Shortcuts to the three admin workflows that actually exist.
 *
 * Each maps to implemented endpoints and nothing else is offered:
 *
 *   Verify lawyers   GET /api/admin/lawyers/pending, PUT .../{id}/verify
 *   Manage users     GET /api/admin/users, PUT .../{id}/activate|deactivate
 *   Moderate reviews GET /api/admin/reviews, DELETE .../{id}
 *
 * A server component - the destinations are static, so none of this needs to
 * ship as client JavaScript. Hrefs come from ROUTES, so the links stay correct
 * as those screens are built in later groups.
 */
const ACTIONS = [
  {
    label: "Verify lawyers",
    description: "Review and approve pending applications",
    href: ROUTES.ADMIN_LAWYERS,
    icon: ShieldCheck,
  },
  {
    label: "Manage users",
    description: "Activate or deactivate accounts",
    href: ROUTES.ADMIN_USERS,
    icon: Users,
  },
  {
    label: "Moderate reviews",
    description: "Remove reviews that breach the guidelines",
    href: ROUTES.ADMIN_REVIEWS,
    icon: MessageSquareText,
  },
];

export function QuickActions() {
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {ACTIONS.map((action) => {
        const Icon = action.icon;

        return (
          <li key={action.href}>
            <Link
              href={action.href}
              className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
            >
              <Card className="h-full transition-shadow hover:shadow-md">
                <CardContent className="flex items-center gap-4 p-5">
                  <span
                    className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"
                    aria-hidden
                  >
                    <Icon className="size-4" />
                  </span>

                  <div className="min-w-0 flex-1 space-y-0.5">
                    <p className="truncate text-sm font-medium">{action.label}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {action.description}
                    </p>
                  </div>

                  <ChevronRight
                    className="size-4 shrink-0 text-muted-foreground"
                    aria-hidden
                  />
                </CardContent>
              </Card>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
