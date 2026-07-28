"use client";

import { LogOut, Menu } from "lucide-react";

import { useAuth } from "@/features/auth/hooks/use-auth";
import { cn } from "@/lib/utils";

import { ThemeToggle } from "./theme-toggle";

/**
 * Top bar for the authenticated application shell.
 *
 * Holds the mobile navigation trigger, the theme switch and the session
 * controls. The user control is a plain button pending the design system's
 * DropdownMenu (Section 5).
 */
export function AppNavbar({ onOpenSidebar }: { onOpenSidebar: () => void }) {
  const { user, logout } = useAuth();

  const initials = user?.fullName
    ? user.fullName
        .split(" ")
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() ?? "")
        .join("")
    : "";

  return (
    <header
      className={cn(
        "sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border",
        "bg-background/80 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/60 sm:px-6",
      )}
    >
      <button
        type="button"
        onClick={onOpenSidebar}
        className="grid size-9 place-items-center rounded-lg border border-border text-muted-foreground hover:bg-accent lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="size-4" aria-hidden />
      </button>

      <div className="flex-1" />

      <ThemeToggle />

      {user ? (
        <div className="flex items-center gap-3">
          <div className="hidden items-center gap-2 sm:flex">
            <span
              className="grid size-8 place-items-center rounded-full bg-primary/10 text-xs font-semibold text-primary"
              aria-hidden
            >
              {initials}
            </span>
            <span className="max-w-[10rem] truncate text-sm font-medium">
              {user.fullName}
            </span>
          </div>

          <button
            type="button"
            onClick={logout}
            className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            <LogOut className="size-4" aria-hidden />
            <span className="hidden sm:inline">Sign out</span>
          </button>
        </div>
      ) : null}
    </header>
  );
}
