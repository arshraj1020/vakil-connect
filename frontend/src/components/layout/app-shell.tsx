"use client";

import { useState, type ReactNode } from "react";

import type { Role } from "@/types";

import { AppNavbar } from "./app-navbar";
import { Sidebar } from "./sidebar";

/**
 * Chrome for every authenticated screen.
 *
 * Mounted once by the protected layout and shared by all three role sections -
 * the sidebar varies by reading NAV_BY_ROLE[role], so there is no per-role
 * shell to keep in sync.
 */
export function AppShell({
  role,
  children,
}: {
  role: Role;
  children: ReactNode;
}) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex min-h-svh bg-background">
      <Sidebar
        role={role}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="flex min-w-0 flex-1 flex-col">
        <AppNavbar onOpenSidebar={() => setSidebarOpen(true)} />
        <main
          id="main-content"
          tabIndex={-1}
          className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8"
        >
          <div className="mx-auto w-full max-w-6xl animate-fade-in">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
