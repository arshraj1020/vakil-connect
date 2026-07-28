import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { LawyerProfileResponse } from "@/types";

/**
 * Biography and practice areas.
 *
 * `bio` is required by the backend on both creation and update, so it is always
 * present - no empty state is needed. Specializations require at least one, but
 * are rendered defensively since the profile endpoint is public and could in
 * principle serve older data.
 */
export function LawyerAbout({ lawyer }: { lawyer: LawyerProfileResponse }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>About</CardTitle>
      </CardHeader>

      <CardContent className="space-y-5">
        <p className="whitespace-pre-line text-sm leading-relaxed text-muted-foreground">
          {lawyer.bio}
        </p>

        {lawyer.specializations.length > 0 ? (
          <div className="space-y-2">
            <p className="text-sm font-medium">Practice areas</p>
            <div className="flex flex-wrap gap-1.5">
              {lawyer.specializations.map((specialization) => (
                <Badge key={specialization} variant="secondary">
                  {specialization}
                </Badge>
              ))}
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
