import { isApiError } from "@/types";

/**
 * Turns a booking failure into copy a client can act on.
 *
 * A 409 from this endpoint has three distinct causes, and they call for
 * different responses from the user - "pick another time", "pick another
 * lawyer", "try a different day" - so collapsing them into one message would
 * leave people stuck.
 *
 * Branching is STATUS-first; the message text only refines the wording. The
 * backend's own message is always the fallback, so if that copy is reworded
 * this degrades to "correct but less specific" rather than misreporting.
 */

export interface BookingErrorInfo {
  title: string;
  description: string;
  /**
   * The slot was taken between loading the page and submitting. The only
   * conflict the UI could not have prevented, since no endpoint exposes other
   * clients' bookings - so it is worth refreshing local data afterwards.
   */
  isSlotConflict: boolean;
}

const SLOT_TAKEN = "already been booked";
const OUTSIDE_HOURS = "not available at the requested";
const UNVERIFIED = "not yet verified";

export function describeBookingError(error: unknown): BookingErrorInfo {
  if (!isApiError(error)) {
    return {
      title: "Could not complete your booking",
      description: "Something went wrong. Please try again.",
      isSlotConflict: false,
    };
  }

  const message = error.message ?? "";

  if (error.status === 409) {
    if (message.includes(SLOT_TAKEN)) {
      return {
        title: "That time was just booked",
        description:
          "Someone else reserved this slot moments ago. Please choose another time.",
        isSlotConflict: true,
      };
    }

    if (message.includes(OUTSIDE_HOURS)) {
      return {
        title: "Outside consultation hours",
        description:
          "This lawyer is not available then. Their hours may have changed - pick another date or time.",
        isSlotConflict: false,
      };
    }

    if (message.includes(UNVERIFIED)) {
      return {
        title: "This lawyer cannot take bookings yet",
        description:
          "Their profile is still awaiting verification. Try another lawyer in the meantime.",
        isSlotConflict: false,
      };
    }

    return {
      title: "Booking could not be completed",
      description: message,
      isSlotConflict: false,
    };
  }

  if (error.status === 404) {
    return {
      title: "Lawyer not found",
      description: "This profile may have been removed. Try searching again.",
      isSlotConflict: false,
    };
  }

  if (error.status === 400) {
    return {
      title: "Check your booking details",
      description: error.fieldErrors
        ? "Some details need attention - see the highlighted fields."
        : message,
      isSlotConflict: false,
    };
  }

  if (error.isNetworkError) {
    return {
      title: "No connection",
      description: "We could not reach the server. Check your connection and try again.",
      isSlotConflict: false,
    };
  }

  return {
    title: "Could not complete your booking",
    description: message || "Please try again.",
    isSlotConflict: false,
  };
}
