import type {
  LawyerProfileResponse,
  UpdateLawyerProfileRequest,
} from "@/types";
import type { LawyerProfileFormValues } from "../schemas/lawyer-profile-schema";

/**
 * Conversions between the API contract and the form's own shape.
 *
 * Two things differ and both are deliberate:
 *
 *  - numbers are strings in the form, because an <input> yields a string and
 *    the schema keeps input and output types identical (see the schema's note),
 *  - the form carries only the six editable fields, while the response carries
 *    fourteen.
 *
 * Isolating the conversion here means the form never sees a `LawyerProfileResponse`
 * and the service never sees form state.
 */

/** Seeds the form from the server's copy. */
export function toFormValues(
  profile: LawyerProfileResponse,
): LawyerProfileFormValues {
  return {
    experienceYears: String(profile.experienceYears),
    consultationFee: String(profile.consultationFee),
    city: profile.city,
    officeAddress: profile.officeAddress,
    bio: profile.bio,
    // Copied, not aliased: the form mutates this array as chips are toggled,
    // and the query cache's object must not move underneath other observers.
    specializations: [...profile.specializations],
  };
}

/**
 * Builds the payload.
 *
 * Every field is sent on every save, including untouched ones, because the
 * endpoint is a full replace and rejects a partial body.
 */
export function toUpdateRequest(
  values: LawyerProfileFormValues,
): UpdateLawyerProfileRequest {
  return {
    experienceYears: Number(values.experienceYears),
    consultationFee: Number(values.consultationFee),
    city: values.city,
    officeAddress: values.officeAddress,
    bio: values.bio,
    specializations: values.specializations,
  };
}
