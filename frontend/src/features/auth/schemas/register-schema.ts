import { z } from "zod";

import { lawyerProfileSchema } from "@/features/lawyer-profile/schemas/lawyer-profile-schema";

/**
 * Registration validation.
 *
 * Every rule mirrors the backend's Bean Validation so the two agree: the client
 * gives instant feedback, the server stays authoritative.
 *
 * The six professional fields are NOT defined here. They are shared with the
 * profile-editing screen, so they live in `lawyer-profile-schema.ts` and this
 * schema delegates to them - the rules cannot drift between signing up and
 * editing afterwards. Only `barCouncilNumber` is checked locally, because it is
 * collected at registration and never editable again.
 *
 * Shape note: this is a single object with a conditional `superRefine` rather
 * than a discriminated union on `role`. A union is the more precise model, but
 * React Hook Form cannot resolve field paths through one - `register(
 * "lawyerProfile.city")` becomes a type error because the CLIENT branch has no
 * such key. The refinement gives identical guarantees (lawyer fields are
 * required if and only if the role is LAWYER) while keeping a single, indexable
 * form type.
 *
 * Numeric inputs are kept as STRINGS here. An <input> always yields a string,
 * and `z.coerce.number()` would make the schema's input and output types
 * diverge, which RHF then has to be told about through extra generics. Parsing
 * happens once, at submit, where the payload is built.
 *
 * ADMIN is absent by construction: the backend rejects it with 400.
 */

const PHONE_PATTERN = /^\+?[0-9]{10,15}$/;

export const registerSchema = z
  .object({
    role: z.enum(["CLIENT", "LAWYER"]),

    fullName: z
      .string()
      .trim()
      .min(1, "Full name is required")
      .max(150, "Full name cannot exceed 150 characters"),
    email: z
      .string()
      .trim()
      .min(1, "Email is required")
      .email("Enter a valid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    phoneNumber: z
      .string()
      .trim()
      .refine((value) => value === "" || PHONE_PATTERN.test(value), {
        message: "Enter a valid phone number",
      }),

    /**
     * Always present in form state so the fields stay controlled when the role
     * is toggled; only validated when the role is LAWYER.
     */
    lawyerProfile: z.object({
      barCouncilNumber: z.string().trim(),
      experienceYears: z.string().trim(),
      consultationFee: z.string().trim(),
      city: z.string().trim(),
      officeAddress: z.string().trim(),
      bio: z.string().trim(),
      specializations: z.array(z.string().min(1)),
    }),
  })
  .superRefine((data, ctx) => {
    if (data.role !== "LAWYER") return;

    const profile = data.lawyerProfile;

    const addIssue = (field: keyof typeof profile, message: string) => {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["lawyerProfile", field],
        message,
      });
    };

    if (!profile.barCouncilNumber) {
      addIssue("barCouncilNumber", "Bar council number is required");
    }

    /*
     * The shared schema validates the flat shape, so its issue paths are
     * re-prefixed onto this form's nested `lawyerProfile.` paths on the way in.
     * Every issue it raises is a custom one, so re-coding them loses nothing.
     */
    const result = lawyerProfileSchema.safeParse(profile);
    if (result.success) return;

    for (const issue of result.error.issues) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["lawyerProfile", ...issue.path],
        message: issue.message,
      });
    }
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;

/**
 * Lawyer field names as the BACKEND reports them - flat, not nested. Used to
 * remap server `fieldErrors` onto the nested form paths.
 */
export const LAWYER_PROFILE_FIELDS = [
  "barCouncilNumber",
  "experienceYears",
  "consultationFee",
  "city",
  "officeAddress",
  "bio",
  "specializations",
] as const;

/**
 * Practice areas offered in the picker.
 *
 * The backend resolves specializations find-or-create by name, so this list is
 * a convenience rather than a constraint - it keeps naming consistent instead
 * of accumulating "Family law", "family Law" and "Family Law" as three rows.
 */
export const SPECIALIZATION_OPTIONS = [
  "Family Law",
  "Criminal Law",
  "Civil Law",
  "Corporate Law",
  "Property Law",
  "Tax Law",
  "Labour Law",
  "Consumer Law",
  "Cyber Law",
  "Intellectual Property",
] as const;
