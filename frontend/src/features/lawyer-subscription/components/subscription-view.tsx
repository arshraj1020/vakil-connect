"use client";

import { useState } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { isApiError, type SubscriptionPlan } from "@/types";

import { useCreateSubscriptionOrder } from "../hooks/use-create-subscription-order";
import { useSubscriptionStatus } from "../hooks/use-subscription-status";
import { useVerifySubscriptionPayment } from "../hooks/use-verify-subscription-payment";

const RAZORPAY_SCRIPT_SRC = "https://checkout.razorpay.com/v1/checkout.js";

/** hsl(46 65% 42%) - this app's --primary, as a hex value for Razorpay's `theme.color`. */
const BRAND_COLOR = "#b09025";

const PLANS: Array<{
  plan: SubscriptionPlan;
  label: string;
  price: string;
  cadence: string;
  note?: string;
}> = [
  { plan: "MONTHLY", label: "Monthly", price: "Rs 500", cadence: "/ month" },
  {
    plan: "YEARLY",
    label: "Yearly",
    price: "Rs 5,500",
    cadence: "/ year",
    note: "Save Rs 500 versus paying monthly",
  },
];

/** Loads Razorpay's Checkout script once and resolves when it's ready to use. */
function loadRazorpayScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve();
      return;
    }

    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${RAZORPAY_SCRIPT_SRC}"]`,
    );
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Failed to load Razorpay")));
      return;
    }

    const script = document.createElement("script");
    script.src = RAZORPAY_SCRIPT_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load Razorpay"));
    document.body.appendChild(script);
  });
}

function statusBadge(active: boolean, status: string | null) {
  if (active) return <Badge variant="success">Active</Badge>;
  if (status === "PENDING") return <Badge variant="warning">Payment pending</Badge>;
  if (status === "EXPIRED") return <Badge variant="destructive">Expired</Badge>;
  return <Badge variant="secondary">Not subscribed</Badge>;
}

/**
 * Lets a lawyer pay to join the platform via Razorpay: Rs 500/month or
 * Rs 5,500/year.
 *
 * Flow: create a PENDING order on our backend -> open Razorpay Checkout with
 * that order's id -> Checkout's `handler` callback hands back a payment id +
 * signature -> we POST those to /verify, which activates the subscription
 * only after checking the signature server-side. The webhook
 * (`RazorpayWebhookController`) is a background safety net for the case where
 * the browser never gets to call `handler` (e.g. tab closed mid-payment); it
 * is not part of this component's flow.
 */
export function SubscriptionView() {
  const { data: status, isLoading } = useSubscriptionStatus();
  const [payingPlan, setPayingPlan] = useState<SubscriptionPlan | null>(null);

  const createOrder = useCreateSubscriptionOrder();
  const verifyPayment = useVerifySubscriptionPayment({
    onSuccess: () => {
      toast.success("Subscription activated. Welcome aboard!");
      setPayingPlan(null);
    },
    onError: (error) => {
      toast.error("Payment verification failed", {
        description: isApiError(error) ? error.message : "Please try again.",
      });
      setPayingPlan(null);
    },
  });

  async function handleSubscribe(plan: SubscriptionPlan) {
    setPayingPlan(plan);

    try {
      const [order] = await Promise.all([
        createOrder.mutateAsync(plan),
        loadRazorpayScript(),
      ]);

      const razorpay = new window.Razorpay({
        key: order.keyId,
        amount: order.amountPaise,
        currency: order.currency,
        order_id: order.orderId,
        name: "VakilConnect",
        description: `${plan === "YEARLY" ? "Yearly" : "Monthly"} subscription`,
        theme: { color: BRAND_COLOR },
        handler: (response: {
          razorpay_order_id: string;
          razorpay_payment_id: string;
          razorpay_signature: string;
        }) => {
          verifyPayment.mutate({
            orderId: response.razorpay_order_id,
            paymentId: response.razorpay_payment_id,
            signature: response.razorpay_signature,
          });
        },
        modal: {
          ondismiss: () => setPayingPlan(null),
        },
      });

      razorpay.open();
    } catch (error) {
      toast.error("Could not start the payment", {
        description: isApiError(error) ? error.message : "Please try again.",
      });
      setPayingPlan(null);
    }
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  const active = status?.active ?? false;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Subscription</h1>
          <p className="text-sm text-muted-foreground">
            An active subscription keeps your profile listed on VakilConnect.
          </p>
        </div>
        {statusBadge(active, status?.status ?? null)}
      </div>

      {active && status?.expiresAt && (
        <Card>
          <CardContent className="pt-6 text-sm text-muted-foreground">
            Your {status.plan === "YEARLY" ? "yearly" : "monthly"} plan is active until{" "}
            <span className="font-medium text-foreground">
              {new Date(status.expiresAt).toLocaleDateString(undefined, {
                year: "numeric",
                month: "long",
                day: "numeric",
              })}
            </span>
            .
          </CardContent>
        </Card>
      )}

      <div className="grid gap-6 sm:grid-cols-2">
        {PLANS.map(({ plan, label, price, cadence, note }) => (
          <Card key={plan}>
            <CardHeader>
              <CardTitle>{label}</CardTitle>
              <CardDescription>
                <span className="text-2xl font-semibold text-foreground">{price}</span>{" "}
                {cadence}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {note && <p className="text-sm text-muted-foreground">{note}</p>}
            </CardContent>
            <CardFooter>
              <Button
                className="w-full"
                disabled={payingPlan !== null}
                onClick={() => handleSubscribe(plan)}
              >
                {payingPlan === plan ? "Opening checkout..." : `Subscribe ${label.toLowerCase()}`}
              </Button>
            </CardFooter>
          </Card>
        ))}
      </div>
    </div>
  );
}
