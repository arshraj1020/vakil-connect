/**
 * Skip-to-content link (Frontend Phase F).
 *
 * The first focusable element on every page. A keyboard or screen-reader user
 * would otherwise tab through the whole header - logo, up to three nav links,
 * theme toggle, auth controls - on every single navigation before reaching the
 * thing they came for. On the admin and lawyer screens the sidebar adds several
 * more.
 *
 * VISIBLE ONLY WHEN FOCUSED, and it must genuinely be visible: `sr-only` alone
 * would hide it from sighted keyboard users, who are precisely the people it
 * serves. `focus:not-sr-only` reverses the hiding on focus and the positioning
 * classes place it over the header rather than shifting the layout.
 *
 * Server component - it is an anchor and a stylesheet, nothing more. The target
 * carries `tabIndex={-1}` so it can accept programmatic focus without joining
 * the tab order.
 */
export function SkipToContent() {
  return (
    <a
      href="#main-content"
      className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-primary focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-primary-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      Skip to content
    </a>
  );
}
