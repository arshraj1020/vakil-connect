"use client";

import type { LawyerSummaryResponse } from "@/types";

import { LawyerSummaryCard } from "./lawyer-summary-card";

/**
 * The verification queue.
 *
 * A card list rather than a <table>. The data is not tabular in practice - each
 * application carries a variable-length list of practice areas and two actions
 * - and a real table would either truncate those or scroll horizontally on
 * mobile. A semantic <ul> gives assistive technology the count and position
 * that matter here, without pretending to a grid structure that would then need
 * full keyboard grid navigation to be correct.
 *
 * Rendered in the order received. The backend applies no ORDER BY and provides
 * no `createdAt`, so there is nothing to sort by and nothing worth re-ordering.
 */
export function PendingLawyersTable({
  lawyers,
  onReview,
}: {
  lawyers: LawyerSummaryResponse[];
  onReview: (lawyer: LawyerSummaryResponse) => void;
}) {
  return (
    <ul className="space-y-3" aria-label="Lawyers awaiting verification">
      {lawyers.map((lawyer) => (
        <li key={lawyer.id}>
          <LawyerSummaryCard lawyer={lawyer} onReview={onReview} />
        </li>
      ))}
    </ul>
  );
}
