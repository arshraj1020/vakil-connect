"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

/**
 * Moves focus to the main landmark after a client-side navigation.
 *
 * THE PROBLEM THIS SOLVES. A full page load resets focus to the top of the
 * document; a client-side route change does not. React swaps the content while
 * focus stays on whatever was clicked - a nav link that may no longer exist.
 * A screen reader therefore announces nothing, and the next Tab continues from
 * the old position, so the user is on a new page with no way to tell.
 *
 * WHY `main` AND NOT THE `h1`. Focusing the landmark puts the whole page ahead
 * of the cursor, so the next Tab reaches the first control in the content.
 * Focusing the heading would skip anything rendered above it.
 *
 * DELIBERATELY NOT ON FIRST RENDER. On initial load the browser has already put
 * focus in the right place, and stealing it would break deep links and undo the
 * autofocus on the login form.
 *
 * `preventScroll` because Next already restores scroll position on navigation;
 * without it, focusing the landmark would fight that and jump the viewport.
 */
export function RouteFocusReset() {
  const pathname = usePathname();
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }

    const main = document.getElementById("main-content");
    main?.focus({ preventScroll: true });
  }, [pathname]);

  return null;
}
