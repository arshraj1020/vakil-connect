import type { Metadata } from "next";

import { RegisterForm } from "@/features/auth/components/register-form";

export const metadata: Metadata = {
  title: "Create an account",
  description:
    "Join VakilConnect as a client to book consultations, or as a lawyer to list your practice.",
};

export default function RegisterPage() {
  return <RegisterForm />;
}
