import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Vitest configuration (Frontend Phase F).
 *
 * Deliberately small. The backend has 224 integration tests; the frontend had
 * none, and the gap that mattered most was not component coverage but the pure
 * functions that decide where an authenticated user is sent - `safeRedirect`
 * shipped an open-redirect once already.
 *
 * `jsdom` rather than `node` because two of the modules under test touch
 * `document`: auth-storage reads and writes a cookie, and React Testing Library
 * needs a DOM when component tests are added later.
 *
 * The React plugin is configured now so that adding the first component test is
 * a one-file change rather than a config exercise, even though nothing under
 * test today renders JSX.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["src/lib/**/*.ts"],
      /* Barrel and constant files have no branches worth measuring. */
      exclude: ["src/lib/constants.ts", "src/lib/query-keys.ts"],
    },
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
