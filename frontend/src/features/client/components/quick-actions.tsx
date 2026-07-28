import { CalendarDays, ChevronRight, Search, UserCog } from "lucide-react";
import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

/**
 * Shortcuts to the tasks a client performs most.
 *
 * A server component - the destinations are static, so this stays out of the
 * client bundle.
 */
const ACTIONS = [
  {
    label: "Find a lawyer",
    description: "Search verified legal professionals",
    href: ROUTES.LAWYERS,
    icon: Search,
  },
  {
    label: "My appointments",
    description: "Review and manage your bookings",
    href: ROUTES.CLIENT_APPOINTMENTS,
    icon: CalendarDays,
  },
  {
    label: "Profile",
    description: "Update your personal details",
    href: ROUTES.CLIENT_PROFILE,
    icon: UserCog,
  },
];

export function QuickActions() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {ACTIONS.map((action) => {
        const Icon = action.icon;

        return (
          <Link
            key={action.href}
            href={action.href}
            className="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
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
        );
      })}
    </div>
  );
}
