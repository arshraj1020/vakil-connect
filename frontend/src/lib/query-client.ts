import { QueryClient } from "@tanstack/react-query";

/**
 * Shared QueryClient configuration.
 *
 * Exported as a factory (not a singleton) because the client must be created
 * per browser session inside a component - a module-level singleton would be
 * shared across requests during SSR and leak one user's cache into another's.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        /* Data is considered fresh for a minute; avoids refetch storms when
           navigating between dashboard tabs. */
        staleTime: 60_000,
        gcTime: 5 * 60_000,

        refetchOnWindowFocus: false,

        /**
         * Never retry client errors. A 400/401/403/404/409 is a deterministic
         * answer from the API - retrying it three times only delays the error
         * the user needs to see. Server/network errors retry once.
         */
        retry: (failureCount, error) => {
          const status = (error as { status?: number })?.status;
          if (typeof status === "number" && status >= 400 && status < 500) {
            return false;
          }
          return failureCount < 1;
        },
      },
      mutations: {
        /* Mutations are never retried: booking an appointment twice because of
           an automatic retry is far worse than surfacing the failure. */
        retry: false,
      },
    },
  });
}
