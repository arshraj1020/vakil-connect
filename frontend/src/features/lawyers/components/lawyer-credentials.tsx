import { Briefcase, Building2, ScrollText } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatExperience } from "@/lib/format";
import type { LawyerProfileResponse } from "@/types";

/**
 * Professional credentials.
 *
 * Shows the bar council number and office address - a public registration
 * number and a business address, both of which support the trust decision a
 * client is making. Personal email and phone are excluded even though the
 * endpoint returns them.
 */
export function LawyerCredentials({
  lawyer,
}: {
  lawyer: LawyerProfileResponse;
}) {
  const items: Array<{ icon: LucideIcon; label: string; value: string }> = [
    {
      icon: Briefcase,
      label: "Experience",
      value: formatExperience(lawyer.experienceYears),
    },
    {
      icon: ScrollText,
      label: "Bar council number",
      value: lawyer.barCouncilNumber,
    },
    {
      icon: Building2,
      label: "Office",
      value: lawyer.officeAddress,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Credentials</CardTitle>
      </CardHeader>

      <CardContent>
        <dl className="space-y-4">
          {items.map((item) => {
            const Icon = item.icon;

            return (
              <div key={item.label} className="flex items-start gap-3">
                <span
                  className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground"
                  aria-hidden
                >
                  <Icon className="size-4" />
                </span>

                <div className="min-w-0 space-y-0.5">
                  <dt className="text-xs text-muted-foreground">{item.label}</dt>
                  <dd className="break-words text-sm font-medium">{item.value}</dd>
                </div>
              </div>
            );
          })}
        </dl>
      </CardContent>
    </Card>
  );
}
