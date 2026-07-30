import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

/**
 * Global test setup.
 *
 * Two things, both of which exist to stop one test leaking into the next:
 *
 *   * `cleanup()` unmounts anything React Testing Library rendered. Without it a
 *     previous test's DOM stays in `document.body` and queries silently match
 *     the wrong element.
 *   * every `document.cookie` entry is cleared, because `auth-storage` writes a
 *     real cookie in jsdom. A token left behind by one test would make the next
 *     one's "signed out" assertion pass for the wrong reason.
 *
 * jest-dom is imported for its matchers (`toBeInTheDocument`, `toHaveFocus`)
 * so component tests added later need no per-file import.
 */
afterEach(() => {
  cleanup();

  for (const entry of document.cookie.split(";")) {
    const name = entry.split("=")[0]?.trim();
    if (name) {
      document.cookie = `${name}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
    }
  }
});
