"use client";

import type { ReactNode } from "react";
import { Toaster } from "sonner";

import { AuthProvider } from "./auth-provider";
import { QueryProvider } from "./query-provider";
import { ThemeProvider } from "./theme-provider";

/**
 * Single composition point for every app-wide provider.
 *
 * The root layout mounts only this component, so adding or reordering
 * providers never touches routing code.
 *
 * Order matters:
 *  - ThemeProvider is outermost because Toaster reads the resolved theme.
 *  - AuthProvider sits INSIDE QueryProvider because logout clears the query
 *    cache, which requires access to the QueryClient.
 */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <QueryProvider>
        <AuthProvider>
          {children}
          <Toaster
            position="top-right"
            richColors
            closeButton
            toastOptions={{ duration: 4000 }}
          />
        </AuthProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
