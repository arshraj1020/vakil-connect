import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merges conditional class names and resolves Tailwind conflicts, so a caller
 * can override a component's internal classes (e.g. passing "px-8" to a button
 * that already sets "px-4") without specificity fights.
 *
 * Required by every shadcn/ui component.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
