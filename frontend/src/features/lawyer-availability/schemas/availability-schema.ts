import { z } from "zod";

/**
 * Availability window form validation.
 *
 * Mirrors the backend's two rules exactly, in its own words, so the client and
 * server never disagree about what is valid:
 *   - start must be strictly before end,
 *   - an identical window (same day, same times) is rejected as a duplicate.
 *
 * The duplicate check needs the existing windows, so it lives in the component
 * rather than here; this schema covers the rules a single form value can express.
 *
 * Overlaps are deliberately NOT rejected: `AvailabilityServiceImpl` permits
 * them, and booking matches with `anyMatch` across windows, so an overlap is
 * redundant rather than wrong.
 *
 * Times are `HH:mm`, the format the DTO declares via @JsonFormat.
 */
export const availabilityWindowSchema = z
  .object({
    dayOfWeek: z.enum([
      "MONDAY",
      "TUESDAY",
      "WEDNESDAY",
      "THURSDAY",
      "FRIDAY",
      "SATURDAY",
      "SUNDAY",
    ]),
    startTime: z.string().min(1, "Choose a start time"),
    endTime: z.string().min(1, "Choose an end time"),
  })
  .refine((values) => values.startTime < values.endTime, {
    // Lexicographic comparison is exact for zero-padded HH:mm.
    message: "Start time must be before end time",
    path: ["endTime"],
  });

export type AvailabilityWindowFormValues = z.infer<
  typeof availabilityWindowSchema
>;
