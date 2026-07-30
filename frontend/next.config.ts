import type { NextConfig } from "next";

/*
 * SECURITY HEADERS (Frontend Phase B)
 *
 * The backend sets headers on its own responses; the Next origin was setting
 * none. These apply to everything this server returns - pages, RSC payloads and
 * static assets alike.
 *
 * CSP IS REPORT-ONLY. A Content-Security-Policy that is wrong does not degrade
 * the app, it breaks it outright - a blocked script is a blank page. Report-Only
 * asks the browser to evaluate the policy and report violations WITHOUT enforcing
 * anything, so the cost of a mistake here is a console message. Switching to
 * enforcement is a deliberate follow-up once the reports are quiet, and it is one
 * header name away.
 */

const isDev = process.env.NODE_ENV !== "production";

/**
 * The API origin, which must be reachable from the browser.
 *
 * `connect-src 'self'` alone would block every XHR, because the API is a
 * different origin (:8080) from the Next server (:3000). Deriving it from the
 * same variable Axios uses means the policy cannot drift from the client.
 */
function apiOrigin(): string | null {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!raw) return null;

  try {
    return new URL(raw).origin;
  } catch {
    // A malformed value should not fail the build; the violation report will
    // name the blocked origin and point straight at the misconfiguration.
    return null;
  }
}

function contentSecurityPolicy(): string {
  const connect = ["'self'", apiOrigin(), isDev ? "ws: wss:" : null]
    .filter(Boolean)
    .join(" ");

  return [
    "default-src 'self'",

    /*
     * 'unsafe-inline' is required by Next, not chosen.
     *
     * The App Router streams inline <script> tags carrying the RSC payload and
     * the hydration bootstrap. Removing it needs per-request nonces, which means
     * generating one in middleware and threading it through the document - a
     * change to the same middleware that guards authentication, and out of scope
     * for this phase.
     *
     * 'unsafe-eval' is DEVELOPMENT ONLY: React Refresh evaluates modules at
     * runtime. Production never receives it.
     */
    `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,

    /*
     * Radix sets inline styles for positioning (popover placement, scroll-lock
     * padding) and Next injects a critical-CSS <style> block. Both are inline
     * styles, so 'unsafe-inline' here is unavoidable without nonces. Style
     * injection is a far weaker vector than script injection.
     */
    "style-src 'self' 'unsafe-inline'",

    // data: for inlined SVG icons, blob: for anything generated client-side.
    "img-src 'self' data: blob:",
    "font-src 'self' data:",

    `connect-src ${connect}`,

    // Nothing is embedded, and nothing embeds us.
    "frame-src 'none'",
    "frame-ancestors 'none'",
    "object-src 'none'",

    // An injected <base> tag rewrites every relative URL on the page.
    "base-uri 'self'",

    // Forms post to this origin only - blocks an injected exfiltration target.
    "form-action 'self'",

    // Blocks an http:// downgrade of any subresource.
    "upgrade-insecure-requests",
  ].join("; ");
}

const securityHeaders = [
  {
    /*
     * Report-Only. Violations surface in the browser console; no reporting
     * endpoint is configured, because collecting reports needs somewhere to send
     * them and that is an infrastructure decision rather than a frontend one.
     */
    key: "Content-Security-Policy-Report-Only",
    value: contentSecurityPolicy(),
  },
  {
    /*
     * Superseded by frame-ancestors on modern browsers - but frame-ancestors
     * lives inside the Report-Only policy and is therefore NOT ENFORCED. Until
     * CSP is enforced, this header is the only thing actually preventing
     * clickjacking.
     */
    key: "X-Frame-Options",
    value: "DENY",
  },
  {
    // Stops a JSON or text response being sniffed and executed as script.
    key: "X-Content-Type-Options",
    value: "nosniff",
  },
  {
    /*
     * Full URL same-origin, origin only when crossing to another HTTPS site,
     * nothing at all on a downgrade to HTTP. Query strings on authenticated
     * pages can carry identifiers; this keeps them out of third-party referers.
     */
    key: "Referrer-Policy",
    value: "strict-origin-when-cross-origin",
  },
  {
    /*
     * Denies powerful features the app does not use. An empty allowlist is
     * stricter than omitting the header, which leaves each feature at its
     * browser default.
     */
    key: "Permissions-Policy",
    value: [
      "camera=()",
      "microphone=()",
      "geolocation=()",
      "payment=()",
      "usb=()",
      "magnetometer=()",
      "gyroscope=()",
      "accelerometer=()",
    ].join(", "),
  },
];

const nextConfig: NextConfig = {
  reactStrictMode: true,

  /* Fail the production build on type or lint errors rather than shipping
     them. Both default to false in Next, which silently allows broken types
     into a build. */
  typescript: { ignoreBuildErrors: false },
  eslint: { ignoreDuringBuilds: false },

  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
