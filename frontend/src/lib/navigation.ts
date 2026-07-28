import {
  CalendarClock,
  CalendarDays,
  ClipboardList,
  LayoutDashboard,
  MessageSquareText,
  Search,
  ShieldCheck,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";

import type { Role } from "@/types";

import { ROUTES } from "./routes";

/**
 * Sidebar navigation, as data.
 *
 * Navigation is configuration rather than markup: `Sidebar` renders whatever
 * this file describes for the current role. Adding a destination is a one-line
 * change here and touches no component.
 */
export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  /**
   * Match this href exactly rather than by prefix. Needed for index routes that
   * would otherwise stay highlighted on every child page.
   */
  exact?: boolean;
}

export const NAV_BY_ROLE: Record<Role, readonly NavItem[]> = {
  CLIENT: [
    { label: "Dashboard", href: ROUTES.CLIENT_DASHBOARD, icon: LayoutDashboard },
    { label: "Find lawyers", href: ROUTES.LAWYERS, icon: Search },
    {
      label: "My appointments",
      href: ROUTES.CLIENT_APPOINTMENTS,
      icon: CalendarDays,
    },
    { label: "Profile", href: ROUTES.CLIENT_PROFILE, icon: UserCog },
  ],

  LAWYER: [
    { label: "Dashboard", href: ROUTES.LAWYER_DASHBOARD, icon: LayoutDashboard },
    {
      label: "Appointments",
      href: ROUTES.LAWYER_APPOINTMENTS,
      icon: ClipboardList,
    },
    {
      label: "Availability",
      href: ROUTES.LAWYER_AVAILABILITY,
      icon: CalendarClock,
    },
    {
      label: "Reviews",
      href: ROUTES.LAWYER_REVIEWS,
      icon: MessageSquareText,
    },
    { label: "Profile", href: ROUTES.LAWYER_PROFILE, icon: UserCog },
  ],

  ADMIN: [
    { label: "Dashboard", href: ROUTES.ADMIN_DASHBOARD, icon: LayoutDashboard },
    { label: "Verify lawyers", href: ROUTES.ADMIN_LAWYERS, icon: ShieldCheck },
    { label: "Users", href: ROUTES.ADMIN_USERS, icon: Users },
    { label: "Reviews", href: ROUTES.ADMIN_REVIEWS, icon: MessageSquareText },
  ],
};

/** True when `pathname` should highlight `item`. */
export function isNavItemActive(item: NavItem, pathname: string): boolean {
  if (item.exact) return pathname === item.href;
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
}
