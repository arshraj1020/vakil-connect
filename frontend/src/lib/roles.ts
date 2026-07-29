import { ShieldCheck, Scale, UserRound, type LucideIcon } from "lucide-react";

import type { Role } from "@/types";

/**
 * How a role is presented.
 *
 * Shared: admin user management lists every role, and the client profile shows
 * the signed-in user's own. One mapping means "CLIENT" reads as "Client"
 * identically wherever it appears.
 *
 * Typed as a total Record, so adding a role to the backend enum fails to
 * compile here until it is handled - better than rendering it unstyled.
 */

export interface RoleMeta {
  label: string;
  /** Badge variant, expressed semantically rather than by hue. */
  intent: "default" | "secondary" | "info";
  icon: LucideIcon;
  description: string;
}

export const ROLE_META: Record<Role, RoleMeta> = {
  CLIENT: {
    label: "Client",
    intent: "secondary",
    icon: UserRound,
    description: "Books consultations with lawyers.",
  },
  LAWYER: {
    label: "Lawyer",
    intent: "info",
    icon: Scale,
    description: "Offers consultations. Must be verified to appear in search.",
  },
  ADMIN: {
    label: "Admin",
    intent: "default",
    icon: ShieldCheck,
    description: "Full access to the administration portal.",
  },
};

export function getRoleMeta(role: Role): RoleMeta {
  return ROLE_META[role];
}
