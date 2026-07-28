import { Logo } from "./logo";

/** Marketing footer. Server component - no interactivity. */
export function Footer() {
  return (
    <footer className="border-t border-border py-10">
      <div className="container flex flex-col items-center justify-between gap-4 sm:flex-row">
        <Logo />
        <p className="text-sm text-muted-foreground">
          &copy; {new Date().getFullYear()} VakilConnect. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
