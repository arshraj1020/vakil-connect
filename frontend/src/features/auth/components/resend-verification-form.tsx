"use client";

import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { isCooldownActive } from "@/features/auth/lib/identity-errors";
import { identityService } from "@/services/identity-service";
import { isApiError } from "@/types";

/**
 * Requests another verification email.
 *
 * THE ACKNOWLEDGEMENT IS DELIBERATELY VAGUE. The backend answers an identical
 * 202 for an unknown address, an already-verified account and a successful
 * send, and this component must not undo that: saying "we sent it" only when
 * the account exists would rebuild the account-enumeration oracle the API was
 * designed to avoid. So the wording is conditional - "if that address needs
 * verifying" - and never confirms anything.
 *
 * @param defaultEmail prefills the field when the caller already knows the
 *                     address, e.g. the login form after EMAIL_NOT_VERIFIED.
 */
export function ResendVerificationForm({
  defaultEmail = "",
}: {
  defaultEmail?: string;
}) {
  const [email, setEmail] = useState(defaultEmail);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    try {
      await identityService.resendVerification(email.trim());
      setSent(true);
      toast.success(
        "If that address needs verifying, a new link is on its way.",
      );
    } catch (error) {
      if (isCooldownActive(error)) {
        // The backend refuses a second email inside the cooldown. Say so
        // plainly - the user is holding a working link already.
        toast.info(
          "We already sent a link recently. Please check your inbox, including spam.",
        );
        setSent(true);
      } else {
        toast.error(
          isApiError(error)
            ? error.message
            : "Unable to send that right now. Please try again.",
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <p className="text-sm text-muted-foreground" role="status">
        Check your inbox — and your spam folder — for a message from
        VakilConnect.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="resend-email">Email address</Label>
        <Input
          id="resend-email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
        />
      </div>
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? "Sending…" : "Send a new verification link"}
      </Button>
    </form>
  );
}
