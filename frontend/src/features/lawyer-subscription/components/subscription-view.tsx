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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { isApiError, type SubscriptionPlan } from "@/types";

import { useCreateSubscriptionOrder } from "../hooks/use-create-subscription-order";
import { useSubscriptionStatus } from "../hooks/use-subscription-status";
import { useValidateCoupon } from "../hooks/use-validate-coupon";
import { useVerifySubscriptionPayment } from "../hooks/use-verify-subscription-payment";

const RAZORPAY_SCRIPT_SRC = "https://checkout.razorpay.com/v1/checkout.js";

/** hsl(46 65% 42%) - this app's --primary, as a hex value for Razorpay's `theme.color`. */
const BRAND_COLOR = "#b09025";

/** Delay between reconciliation polls after Checkout closes without a `handler` callback. */
const RECONCILE_POLL_MS = 2500;
/** Enough attempts to cover a slow UPI app hand-back without polling forever. */
const RECONCILE_POLL_ATTEMPTS = 5;

const PLANS: Array<{
  plan: SubscriptionPlan;
  label: string;
  amountPaise: number;
  cadence: string;
  note?: string;
}> = [
  { plan: "MONTHLY", label: "Monthly", amountPaise: 49_900, cadence: "/ month" },
  {
    plan: "YEARLY",
    label: "Yearly",
    amountPaise: 549_900,
    cadence: "/ year",
    note: "Save Rs 489 versus paying monthly",
  },
];

/** Renders paise as "Rs 499" or "Rs 449.10" - only shows decimals when the discount produces a fraction. */
function formatRupees(paise: number): string {
  const rupees = paise / 100;
  const rounded = Math.round(rupees * 100) / 100;
  return `Rs ${Number.isInteger(rounded) ? rounded : rounded.toFixed(2)}`;
}

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

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function statusBadge(active: boolean, status: string | null, reconciling: boolean) {
  if (active) return <Badge variant="success">Active</Badge>;
  if (reconciling) return <Badge variant="warning">Confirming payment...</Badge>;
  if (status === "PENDING") return <Badge variant="warning">Payment pending</Badge>;
  if (status === "EXPIRED") return <Badge variant="destructive">Expired</Badge>;
  return <Badge variant="secondary">Not subscribed</Badge>;
}

/**
 * Lets a lawyer pay to join the platform via Razorpay: Rs 499/month or
 * Rs 5,499/year.
 *
 * Flow: create a PENDING order on our backend -> open Razorpay Checkout with
 * that order's id -> Checkout's `handler` callback hands back a payment id +
 * signature -> we POST those to /verify, which activates the subscription
 * only after checking the signature server-side.
 *
 * `handler` DOES NOT ALWAYS FIRE. On mobile, a UPI payment routinely switches
 * away to a banking app and the browser tab can lose the JS execution context
 * before control returns to Checkout - the payment still gets captured on
 * Razorpay's side, but our page never learns about it. Two independent
 * safety nets cover that: the backend re-checks a PENDING order against
 * Razorpay directly every time `GET /api/lawyer/subscription` is called (see
 * `LawyerSubscriptionServiceImpl#reconcileWithRazorpay`), and this component
 * leans on that by polling the status a few times after Checkout's modal
 * closes, whether or not `handler` ran.
 */
export function SubscriptionView() {
  const { data: status, isLoading, refetch } = useSubscriptionStatus();
  const [payingPlan, setPayingPlan] = useState<SubscriptionPlan | null>(null);
  const [reconciling, setReconciling] = useState(false);
  const [couponCode, setCouponCode] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState<{ code: string; discountPercent: number } | null>(
    null,
  );

  const createOrder = useCreateSubscriptionOrder();
  const validateCoupon = useValidateCoupon({
    onSuccess: (result) => {
      setAppliedCoupon({ code: result.code, discountPercent: result.discountPercent });
      toast.success(`${result.code} applied - ${result.discountPercent}% off`);
    },
    onError: (error) => {
      setAppliedCoupon(null);
      toast.error("Invalid coupon code", {
        description: isApiError(error) ? error.message : "Please check the code and try again.",
      });
    },
  });
  const verifyPayment = useVerifySubscriptionPayment({
    onSuccess: (result) => {
      setPayingPlan(null);
      setReconciling(false);
      if (result.active) {
        toast.success("Subscription activated. Welcome aboard!");
      }
    },
    onError: async () => {
      // The browser's own signature-verified call failed or was rejected -
      // fall back to polling, since the payment may still have gone through
      // and the backend's own Razorpay check (reconcileWithRazorpay) can
      // still pick it up.
      await pollForActivation();
    },
  });

  /** Re-checks status a few times, since activation may land via the backend's own Razorpay reconciliation rather than this tab. */
  async function pollForActivation() {
    setReconciling(true);
    for (let attempt = 0; attempt < RECONCILE_POLL_ATTEMPTS; attempt++) {
      await sleep(RECONCILE_POLL_MS);
      const { data } = await refetch();
      if (data?.active) {
        toast.success("Subscription activated. Welcome aboard!");
        break;
      }
    }
    setReconciling(false);
    setPayingPlan(null);
  }

  function handleApplyCoupon() {
    const code = couponCode.trim();
    if (!code) return;
    validateCoupon.mutate(code);
  }

  async function handleSubscribe(plan: SubscriptionPlan) {
    setPayingPlan(plan);

    try {
      const [order] = await Promise.all([
        // Only an explicitly-Applied coupon is sent - typing a code and
        // hitting Subscribe without clicking Apply first subscribes at full
        // price, so the lawyer always sees the discount before paying it.
        createOrder.mutateAsync({ plan, couponCode: appliedCoupon?.code }),
        loadRazorpayScript(),
      ]);

      // A 100%-off coupon activates the subscription immediately server-side
      // - there is nothing for Razorpay to charge, so Checkout never opens.
      if (!order.requiresPayment) {
        const { data } = await refetch();
        setPayingPlan(null);
        if (data?.active) {
          toast.success("Coupon applied - subscription activated. Welcome aboard!");
        }
        return;
      }

      let handlerRan = false;

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
          handlerRan = true;
          verifyPayment.mutate({
            orderId: response.razorpay_order_id,
            paymentId: response.razorpay_payment_id,
            signature: response.razorpay_signature,
          });
        },
        modal: {
          // Fires when the lawyer closes Checkout themselves AND whenever
          // Checkout's tab/window loses and regains context mid-payment
          // (the UPI app-switch case) - `handlerRan` tells the two apart.
          ondismiss: () => {
            if (!handlerRan) {
              void pollForActivation();
            }
          },
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
        {statusBadge(active, status?.status ?? null, reconciling)}
      </div>

      {reconciling && (
        <Card>
          <CardContent className="flex items-center gap-3 pt-6 text-sm text-muted-foreground">
            <Spinner size="sm" />
            Confirming your payment with Razorpay. This can take a few seconds after a UPI
            payment - no need to pay again.
          </CardContent>
        </Card>
      )}

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

      {!active && (
        <div className="space-y-6">
          <div className="max-w-xs space-y-1.5">
            <Label htmlFor="coupon-code">Coupon code</Label>
            <div className="flex gap-2">
              <Input
                id="coupon-code"
                placeholder="e.g. ARSHCARE"
                value={couponCode}
                onChange={(event) => {
                  setCouponCode(event.target.value.toUpperCase());
                  setAppliedCoupon(null);
                }}
                disabled={payingPlan !== null || reconciling}
                className="uppercase"
              />
              <Button
                type="button"
                variant="outline"
                disabled={!couponCode.trim() || validateCoupon.isPending || payingPlan !== null || reconciling}
                onClick={handleApplyCoupon}
              >
                {validateCoupon.isPending ? "Checking..." : "Apply"}
              </Button>
            </div>
            {appliedCoupon ? (
              <p className="text-xs text-success">
                {appliedCoupon.code} applied - {appliedCoupon.discountPercent}% off.
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">
                Optional - click Apply to preview the discount before subscribing.
              </p>
            )}
          </div>

          <div className="grid gap-6 sm:grid-cols-2">
            {PLANS.map(({ plan, label, amountPaise, cadence, note }) => {
              const discountedPaise = appliedCoupon
                ? amountPaise - Math.floor((amountPaise * appliedCoupon.discountPercent) / 100)
                : amountPaise;
              const discounted = appliedCoupon && discountedPaise !== amountPaise;

              return (
                <Card key={plan}>
                  <CardHeader>
                    <CardTitle>{label}</CardTitle>
                    <CardDescription className="flex flex-wrap items-baseline gap-2">
                      {discounted && (
                        <span className="text-base text-muted-foreground line-through">
                          {formatRupees(amountPaise)}
                        </span>
                      )}
                      <span className="text-2xl font-semibold text-foreground">
                        {formatRupees(discountedPaise)}
                      </span>{" "}
                      {cadence}
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    {note && <p className="text-sm text-muted-foreground">{note}</p>}
                  </CardContent>
                  <CardFooter>
                    <Button
                      className="w-full"
                      disabled={payingPlan !== null || reconciling}
                      onClick={() => handleSubscribe(plan)}
                    >
                      {payingPlan === plan
                        ? reconciling
                          ? "Confirming payment..."
                          : "Opening checkout..."
                        : `Subscribe ${label.toLowerCase()}`}
                    </Button>
                  </CardFooter>
                </Card>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
