import type { Metadata } from "next";
import { Inter } from "next/font/google";

import { Providers } from "@/providers";

import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "VakilConnect — Find and consult verified lawyers",
    template: "%s | VakilConnect",
  },
  description:
    "Discover verified lawyers, book consultations, and manage your legal appointments in one place.",
};

/**
 * Root layout.
 *
 * Deliberately minimal: fonts, metadata and providers only. All chrome
 * (navbars, sidebars) belongs to the (public) and (protected) route group
 * layouts, because the two have entirely different shells.
 *
 * suppressHydrationWarning is required on <html>: next-themes writes the theme
 * class before React hydrates, so server and client markup differ by design.
 */
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${inter.variable} font-sans`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
