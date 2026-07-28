import type { Metadata } from "next";

import { LawyerProfileView } from "@/features/lawyers/components/lawyer-profile-view";

export const metadata: Metadata = {
  title: "Lawyer profile",
  description:
    "View a lawyer's experience, practice areas, consultation hours and client reviews.",
};

/**
 * In Next 15 `params` is a Promise and must be awaited.
 *
 * Metadata is static rather than generated from the profile: a dynamic title
 * would require fetching during rendering, which couples page generation to
 * backend availability for no meaningful gain on an authenticated-adjacent
 * page.
 */
export default async function LawyerProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  return <LawyerProfileView lawyerId={id} />;
}
