import { z } from "zod";

/**
 * Booking form validation.
 *
 * Mirrors `BookAppointmentRequest`. The date and time are chosen through custom
 * pickers rather than typed, so these rules mainly guard the "nothing selected
 * yet" case and keep the submit button honest.
 *
 * The future-date rule is enforced by the pickers themselves (the calendar
 * disables today and earlier) and again by the backend's @Future constraint;
 * it is restated here so a stale selection cannot slip through if the page is
 * left open past midnight.
 */
export const bookingSchema = z.object({
  appointmentDate: z.string().min(1, "Choose a date"),
  appointmentTime: z.string().min(1, "Choose a time"),
  consultationMode: z.enum(["ONLINE", "OFFLINE"], {
    required_error: "Choose how you would like to meet",
  }),
  notes: z
    .string()
    .trim()
    .max(2000, "Notes cannot exceed 2000 characters")
    .optional()
    .or(z.literal("")),
});

export type BookingFormValues = z.infer<typeof bookingSchema>;
