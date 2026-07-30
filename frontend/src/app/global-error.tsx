"use client";

import { useEffect } from "react";

/**
 * Last-resort boundary: the root layout itself failed.
 *
 * WHY THIS ONE IS DIFFERENT. A segment `error.tsx` renders *inside* the root
 * layout, so it inherits the fonts, theme tokens and Tailwind classes every
 * other page uses. `global-error.tsx` REPLACES the root layout - which is why it
 * must render its own <html> and <body>, and why it cannot assume the design
 * system loaded. If the failure were in the stylesheet or the theme provider, a
 * component built from `bg-background` and `text-muted-foreground` would render
 * invisible text on an invisible background.
 *
 * So the styles here are inline and deliberately plain. This screen should be
 * almost impossible to reach; when it is reached, being legible matters more
 * than being on brand.
 *
 * `reset` is offered but a full reload is the honest primary action: if the root
 * layout threw, re-rendering the same tree tends to throw again.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production") {
      console.error("Global error (root layout failed):", error);
    }
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "grid",
          placeItems: "center",
          padding: "1.5rem",
          fontFamily:
            "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
          color: "#0f172a",
          background: "#ffffff",
        }}
      >
        <main style={{ maxWidth: "28rem", textAlign: "center" }}>
          <h1 style={{ fontSize: "1.125rem", fontWeight: 600, margin: "0 0 0.5rem" }}>
            VakilConnect could not start
          </h1>

          <p
            style={{
              fontSize: "0.875rem",
              lineHeight: 1.6,
              color: "#475569",
              margin: "0 0 1.5rem",
            }}
          >
            Something failed before the page could be displayed. Your data is
            safe. Reloading usually resolves it — if it does not, please try
            again in a few minutes.
          </p>

          <div
            style={{
              display: "flex",
              gap: "0.5rem",
              justifyContent: "center",
              flexWrap: "wrap",
            }}
          >
            <button
              type="button"
              onClick={() => window.location.reload()}
              style={{
                cursor: "pointer",
                borderRadius: "0.5rem",
                border: "1px solid #0f172a",
                background: "#0f172a",
                color: "#ffffff",
                padding: "0.5rem 1rem",
                fontSize: "0.875rem",
                fontWeight: 500,
              }}
            >
              Reload the page
            </button>

            <button
              type="button"
              onClick={reset}
              style={{
                cursor: "pointer",
                borderRadius: "0.5rem",
                border: "1px solid #cbd5e1",
                background: "transparent",
                color: "inherit",
                padding: "0.5rem 1rem",
                fontSize: "0.875rem",
                fontWeight: 500,
              }}
            >
              Try again
            </button>
          </div>

          {error.digest ? (
            <p
              style={{
                marginTop: "1.5rem",
                fontSize: "0.75rem",
                color: "#64748b",
              }}
            >
              Reference code:{" "}
              <span style={{ fontFamily: "ui-monospace, monospace" }}>
                {error.digest}
              </span>
            </p>
          ) : null}
        </main>
      </body>
    </html>
  );
}
