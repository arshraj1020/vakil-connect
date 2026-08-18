import { API_ERROR_CODE, isApiError } from "@/types";

/**
 * Maps a failed token operation onto what the user should be told and offered.
 *
 * PURE, AND DELIBERATELY SO. This is the decision layer for every token screen,
 * so it is a plain function over an error - unit-testable without rendering a
 * component or mocking a router.
 *
 * BRANCHES ON `code`, NEVER ON `message`. The message is prose a copy edit is
 * free to reword; the code is a contract.
 */

export type TokenOutcome =
  | "expired"
  | "already-used"
  | "invalid"
  | "network"
  | "unknown";

export interface TokenOutcomeCopy {
  outcome: TokenOutcome;
  title: string;
  description: string;
  /** True when requesting a fresh link is the useful next step. */
  offerNewLink: boolean;
}

export function classifyTokenError(error: unknown): TokenOutcomeCopy {
  if (!isApiError(error)) {
    return {
      outcome: "unknown",
      title: "Something went wrong",
      description:
        "We could not complete that just now. Please try again in a moment.",
      offerNewLink: true,
    };
  }

  if (error.isNetworkError) {
    return {
      outcome: "network",
      title: "Unable to reach the server",
      description:
        "Check your connection and try again. Your link is still valid.",
      // The link was never used, so a new one would be wasted.
      offerNewLink: false,
    };
  }

  switch (error.code) {
    case API_ERROR_CODE.TOKEN_EXPIRED:
      return {
        outcome: "expired",
        title: "This link has expired",
        description:
          "Links are short-lived for your security. Request a new one and we will email it straight away.",
        offerNewLink: true,
      };

    case API_ERROR_CODE.TOKEN_ALREADY_USED:
      return {
        outcome: "already-used",
        title: "This link has already been used",
        description:
          "Each link works exactly once. If you have already completed this step, you can sign in.",
        offerNewLink: false,
      };

    case API_ERROR_CODE.TOKEN_INVALID:
      return {
        outcome: "invalid",
        title: "This link is not valid",
        description:
          "It may have been superseded by a newer email, or copied incompletely. Request a fresh one.",
        offerNewLink: true,
      };

    default:
      return {
        outcome: "unknown",
        title: "Something went wrong",
        description:
          "We could not complete that just now. Please try again in a moment.",
        offerNewLink: true,
      };
  }
}

/** True when a login failure means "verify your email", not "wrong password". */
export function isEmailNotVerified(error: unknown): boolean {
  return (
    isApiError(error) && error.code === API_ERROR_CODE.EMAIL_NOT_VERIFIED
  );
}

/** True when the backend refused because a cooldown is still running. */
export function isCooldownActive(error: unknown): boolean {
  return isApiError(error) && error.code === API_ERROR_CODE.COOLDOWN_ACTIVE;
}
