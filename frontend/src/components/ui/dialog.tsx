"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

/**
 * Radix Dialog.
 *
 * Provides the focus trap, scroll lock, Escape handling and `aria-modal`
 * semantics that a hand-rolled modal almost always gets wrong. ConfirmDialog
 * builds on this.
 */
export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

export function DialogOverlay({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Overlay>) {
  return (
    <DialogPrimitive.Overlay
      className={cn(
        "fixed inset-0 z-50 bg-foreground/20 backdrop-blur-sm",
        "data-[state=open]:animate-in data-[state=open]:fade-in-0",
        "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
        className,
      )}
      {...props}
    />
  );
}

export function DialogContent({
  className,
  children,
  showClose = true,
  ...props
}: ComponentProps<typeof DialogPrimitive.Content> & { showClose?: boolean }) {
  return (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        className={cn(
          "fixed left-1/2 top-1/2 z-50 -translate-x-1/2 -translate-y-1/2",
          /*
           * PHASE E - two fixes, both only visible on small screens.
           *
           * `w-[calc(100%-2rem)]` replaces `w-full`. The dialog is centred with
           * -translate-x-1/2, so `w-full` made it exactly viewport-wide: at
           * 375px it sat flush against both edges with the rounded corners and
           * border cut off by the screen. Below the `max-w-lg` breakpoint there
           * is now always a 16px gutter.
           *
           * `max-h` + `overflow-y-auto` is the more serious one. There was no
           * height constraint, and because the dialog is centred with
           * -translate-y-1/2, content taller than the viewport overflowed
           * EQUALLY IN BOTH DIRECTIONS - so the title and the close button went
           * above the top edge, and nothing scrolled, because a `fixed` element
           * does not extend the page. The dialog became unclosable except by
           * Escape or an overlay click. Long content is normal here: a lawyer's
           * biography, a client's review comment.
           *
           * `svh` rather than `vh` so the mobile browser's collapsing address
           * bar cannot push the dialog taller than the visible area.
           */
          "w-[calc(100%-2rem)] max-w-lg max-h-[calc(100svh-2rem)] overflow-y-auto",
          "rounded-xl border border-border bg-card p-6 shadow-lg",
          "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95",
          "data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95",
          className,
        )}
        {...props}
      >
        {children}
        {showClose ? (
          <DialogPrimitive.Close
            className={cn(
              "absolute right-4 top-4 grid size-8 place-items-center rounded-md text-muted-foreground",
              "transition-colors hover:bg-accent hover:text-accent-foreground",
              "focus:outline-none focus:ring-2 focus:ring-ring",
            )}
          >
            <X className="size-4" aria-hidden />
            <span className="sr-only">Close</span>
          </DialogPrimitive.Close>
        ) : null}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

export function DialogHeader({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("flex flex-col gap-1.5 pr-8", className)} {...props} />;
}

export function DialogFooter({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end", className)}
      {...props}
    />
  );
}

export function DialogTitle({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      className={cn("text-base font-semibold tracking-tight", className)}
      {...props}
    />
  );
}

export function DialogDescription({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Description>) {
  return (
    <DialogPrimitive.Description
      className={cn("text-sm text-muted-foreground", className)}
      {...props}
    />
  );
}
