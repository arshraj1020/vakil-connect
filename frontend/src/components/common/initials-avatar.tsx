import { cn } from "@/lib/utils";

/**
 * Avatar placeholder built from a person's initials.
 *
 * The API exposes no profile photo - `profile_photo_url` exists on the database
 * table but is not part of any response DTO - so initials are the honest
 * placeholder rather than a generic silhouette, and they help distinguish
 * results at a glance.
 */
export function InitialsAvatar({
  name,
  size = "default",
  className,
}: {
  name: string;
  size?: "sm" | "default" | "lg";
  className?: string;
}) {
  const initials = name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");

  const sizeClass =
    size === "sm"
      ? "size-8 text-xs"
      : size === "lg"
        ? "size-16 text-lg"
        : "size-12 text-sm";

  return (
    <span
      className={cn(
        "grid shrink-0 place-items-center rounded-full bg-primary/10 font-semibold text-primary",
        sizeClass,
        className,
      )}
      aria-hidden
    >
      {initials || "?"}
    </span>
  );
}
