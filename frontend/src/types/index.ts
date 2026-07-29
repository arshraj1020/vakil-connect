/**
 * Barrel for the API contract types.
 *
 * Lets consumers write `import type { AppointmentResponse } from "@/types"`
 * without needing to know which module a DTO lives in.
 */
export * from "./common";
export * from "./auth";
export * from "./lawyer";
export * from "./appointment";
export * from "./review";
export * from "./admin";
export * from "./reference";
