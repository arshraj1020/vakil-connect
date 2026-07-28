"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { X } from "lucide-react";

import { NAV_BY_ROLE, isNavItemActive } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import type { Role } from "@/types";

import { Logo } from "./logo";

interface SidebarProps {
  role: Role;
  /** Mobile drawer state. Ignored at >= lg, where the sidebar is always shown. */
  open: boolean;
  onClose: () => void;
}

/**
 * Role-scoped primary navigation.
 *
 * Renders whatever `NAV_BY_ROLE` describes - it contains no knowledge of which
 * destinations exist, so navigation changes never touch this file.
 *
 * Responsive strategy: a permanent rail from `lg` up, and an overlay drawer
 * below it. Implemented with plain state and transforms rather than a Radix
 * Sheet because the design system (Section 5) has not been generated yet.
 */
export function Sidebar({ role, open, onClose }: SidebarProps) {
  const pathname = usePathname();
  const items = NAV_BY_ROLE[role];

  return (
    <>
      {/* Scrim - mobile only */}
      <div
        className={cn(
          "fixed inset-0 z-40 bg-foreground/20 backdrop-blur-sm transition-opacity lg:hidden",
          open ? "opacity-100" : "pointer-events-none opacity-0",
        )}
        onClick={onClose}
        aria-hidden
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border bg-card",
          "transition-transform duration-200 ease-out",
          "lg:sticky lg:top-0 lg:z-auto lg:h-svh lg:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full",
        )}
        aria-label="Primary"
      >
        <div className="flex h-16 items-center justify-between border-b border-border px-4">
          <Logo />
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-md text-muted-foreground hover:bg-accent lg:hidden"
            aria-label="Close navigation"
          >
            <X className="size-4" aria-hidden />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {items.map((item) => {
            const active = isNavItemActive(item, pathname);
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onClose}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                )}
              >
                <Icon className="size-4 shrink-0" aria-hidden />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-border p-4">
          <p className="text-xs text-muted-foreground">
            Signed in as{" "}
            <span className="font-medium capitalize text-foreground">
              {role.toLowerCase()}
            </span>
          </p>
        </div>
      </aside>
    </>
  );
}
