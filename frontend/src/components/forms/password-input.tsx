"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState, type ComponentProps } from "react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * Password field with a visibility toggle.
 *
 * Used by both sign-in and sign-up, so the accessibility details are written
 * once: the toggle is a real button (keyboard reachable, in tab order),
 * `aria-pressed` announces the current state, and `aria-label` says what the
 * action will do rather than what it currently shows.
 *
 * The toggle deliberately does not receive focus-stealing behaviour - revealing
 * a password should not move the caret out of the field.
 */
export function PasswordInput({
  className,
  ...props
}: Omit<ComponentProps<typeof Input>, "type">) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="relative">
      <Input
        type={visible ? "text" : "password"}
        className={cn("pr-11", className)}
        {...props}
      />

      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-pressed={visible}
        aria-label={visible ? "Hide password" : "Show password"}
        className={cn(
          "absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg",
          "text-muted-foreground transition-colors hover:text-foreground",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        )}
      >
        {visible ? (
          <EyeOff className="size-4" aria-hidden />
        ) : (
          <Eye className="size-4" aria-hidden />
        )}
      </button>
    </div>
  );
}
