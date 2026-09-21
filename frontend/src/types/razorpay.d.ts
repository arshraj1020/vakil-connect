/**
 * Minimal ambient typing for the Razorpay Checkout script
 * (https://checkout.razorpay.com/v1/checkout.js), loaded dynamically by
 * `subscription-view.tsx` rather than bundled - it's a small, third-party,
 * globally-mutating script that only one page in the app needs.
 *
 * Deliberately loose (`any` for options/response): this is a boundary to code
 * this project does not own, and pinning down Razorpay's full options surface
 * here would be effort spent chasing an external API rather than typing our
 * own contracts.
 */
export {};

declare global {
  interface Window {
    Razorpay: new (options: Record<string, unknown>) => {
      open: () => void;
    };
  }
}
