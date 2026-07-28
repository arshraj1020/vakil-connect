# Design System

Conventions for VakilConnect's UI. Tokens live in `src/app/globals.css` and are
mapped to utilities in `tailwind.config.ts`.

## Rule

Components reference **semantic tokens only** — `bg-background`,
`text-muted-foreground`, `border-border`, `text-destructive`. No raw hex, no
`dark:` overrides. Dark mode then works without per-component effort.

## Colour

| Token | Use |
|---|---|
| `background` / `foreground` | Page surface and body text |
| `card` / `card-foreground` | Raised surfaces |
| `primary` | Brand gold `#D4AF37`; primary actions, active nav |
| `secondary`, `muted`, `accent` | Neutral surfaces, hovers, secondary text |
| `success` `warning` `info` `destructive` | Status meaning only — never decoration |
| `border` / `input` / `ring` | Lines and focus rings |

## Typography

| Role | Classes |
|---|---|
| Page title | `text-2xl font-semibold tracking-tight` (owned by `PageHeader`) |
| Section title | `text-lg font-semibold tracking-tight` (owned by `FormSection`) |
| Card title | `text-base font-semibold` (owned by `CardTitle`) |
| Body | `text-sm` |
| Caption / hint | `text-xs text-muted-foreground` |

Headings are owned by components, so no screen picks its own heading size.

## Spacing

4px base. Card padding `p-6` · page sections `space-y-6` · form fields
`space-y-4` · inline `gap-2`/`gap-3`.

## Radius & shadow

`--radius: 0.75rem`. Cards and dialogs `rounded-xl`, controls `rounded-lg`,
badges `rounded-full`.

Shadows are intentionally soft: `shadow-xs` (buttons) → `shadow-sm` (cards) →
`shadow-md` (hover) → `shadow-lg` (dialogs). Heavy shadows read as dated.

## Icons

Only three sizes: `size-4` inline/buttons · `size-5` section headers ·
`size-6` empty and error states. Always `aria-hidden` when adjacent to a label.

## Button variants

`default` · `secondary` · `outline` · `ghost` · `destructive` · `link`
Sizes: `sm` · `default` · `lg` · `icon`

## Badge variants

Semantic, not chromatic: `default` · `secondary` · `outline` · `success` ·
`warning` · `destructive` · `info`.

## Status presentation

Appointment statuses are mapped **once** in `src/lib/status.ts`
(`APPOINTMENT_STATUS_META`) to a label, a semantic intent and an icon.
`StatusBadge` and any future filter chip or timeline read from it — components
never hardcode status colours or copy.

The map is a total `Record<AppointmentStatus, StatusMeta>`, so adding a status
to the backend enum fails the build until it is presented deliberately.

## Loading, empty, error

Every data surface handles four states: **loading → error → empty → data**.

- `LoadingSkeleton` variants mirror the shape of the content they replace
- `Spinner` for actions; `Skeleton` for content
- `EmptyState` for a successful request with no results (guidance, not failure)
- `ErrorState` for failures — accepts `unknown`, narrows `ApiError` internally

## Forms

`FormSection` (+ `FormRow`) group fields; `SubmitButton` standardises the busy
state and disables on `isPending`, which is what prevents double submission.

Validation errors use `aria-invalid`, which drives the error styling on `Input`,
`Textarea` and `SelectTrigger` — no bespoke error props.
