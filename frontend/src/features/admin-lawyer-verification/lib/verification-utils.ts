import type { LawyerProfileResponse, LawyerSummaryResponse } from "@/types";

/**
 * Presentation rules specific to the verification queue.
 *
 * No formatting is defined here - currency goes through `lib/format`, dates
 * through `lib/date`. What lives here is the reasoning about which fields are
 * meaningful for a lawyer who has not been verified yet, because several of
 * them are structurally empty and displaying them raw would mislead.
 */

/**
 * Whether a rating is worth showing at all.
 *
 * Every lawyer in this queue is unverified, and `bookAppointment` rejects
 * unverified lawyers - so they can never have held a consultation, never have a
 * completed appointment, and therefore never have been reviewed. `rating` is
 * always 0.0 and `totalReviews` always 0 on this screen.
 *
 * Rendering "0.0 ★" would read as a POOR rating rather than as no data, and an
 * admin might decline to verify on the strength of it. So the rating is shown
 * only in the impossible case that one exists, and described as "not yet rated"
 * otherwise.
 */
export function hasMeaningfulRating(
  lawyer: LawyerSummaryResponse | LawyerProfileResponse,
): boolean {
  return lawyer.totalReviews > 0;
}

/**
 * The credentials an admin is actually checking.
 *
 * Verification is a judgement about whether this person is a practising lawyer,
 * so the bar council number is the field that matters - and it appears ONLY on
 * `LawyerProfileResponse`, never on the summary in the queue. This is why the
 * details dialog fetches the full profile rather than reusing the row's data.
 */
export function hasBarCouncilNumber(
  profile: LawyerProfileResponse,
): boolean {
  return profile.barCouncilNumber.trim().length > 0;
}

/**
 * Whether a profile still needs a decision.
 *
 * Used to keep the details dialog honest after a successful verification: the
 * PUT returns the updated profile, so the dialog can reflect the new state
 * rather than continuing to offer an action that has already been taken.
 */
export function awaitsVerification(profile: LawyerProfileResponse): boolean {
  return !profile.verified;
}

/**
 * A short, human description of how complete an application looks.
 *
 * Purely a reading of fields the backend already returns - it does not score,
 * rank or infer anything. Registration makes all of these mandatory, so a gap
 * here means a profile created before a constraint existed, or edited oddly,
 * and is worth an admin's attention before approval.
 */
export function findMissingDetails(profile: LawyerProfileResponse): string[] {
  const missing: string[] = [];

  if (!hasBarCouncilNumber(profile)) missing.push("Bar council number");
  if (!profile.bio.trim()) missing.push("Biography");
  if (!profile.city.trim()) missing.push("City");
  if (!profile.officeAddress.trim()) missing.push("Office address");
  if (profile.specializations.length === 0) missing.push("Practice areas");
  if (!profile.phoneNumber) missing.push("Phone number");

  return missing;
}
