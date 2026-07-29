"use client";

import { Check, ChevronsUpDown, X } from "lucide-react";
import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

/**
 * Searchable select controls.
 *
 * Hand-rolled rather than pulled from a library. Radix has no combobox
 * primitive, and the project's `Select` is a plain Radix select - correct for
 * seven weekdays, unusable for five thousand cities. Adding `cmdk` would bring a
 * dependency whose main value is a command palette we do not need.
 *
 * Implements the WAI-ARIA 1.2 combobox pattern:
 *
 *   - the input carries `role="combobox"`, `aria-expanded`, `aria-controls` and
 *     `aria-autocomplete="list"`
 *   - the highlighted option is announced through `aria-activedescendant`, so
 *     DOM focus never leaves the input and typing is never interrupted
 *   - options carry `role="option"` and `aria-selected`
 *
 * `Combobox` and `MultiCombobox` share the keyboard model, the popover and the
 * option rendering below; only selection semantics differ.
 */

/* ------------------------------------------------------------------ shared */

interface BaseProps<T> {
  items: T[];
  getKey: (item: T) => string;
  getLabel: (item: T) => string;
  /** Secondary line, e.g. a city's state or a language's native name. */
  getDescription?: (item: T) => string | undefined;

  placeholder?: string;
  disabled?: boolean;
  /** A request is in flight. Distinct from "no results". */
  loading?: boolean;
  /** Shown in place of the list when there is nothing to offer. */
  emptyMessage?: string;
  /** Non-blocking failure text; the control stays usable. */
  errorMessage?: string;
  /** Guidance shown before a search is worth running. */
  hintMessage?: string;

  /** Raise the typed text for async search. Omit for local filtering. */
  onQueryChange?: (query: string) => void;

  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
  className?: string;
}

/** Local, accent-insensitive contains-match. Skipped when the caller searches. */
function localFilter<T>(
  items: T[],
  query: string,
  getLabel: (item: T) => string,
): T[] {
  const term = query.trim().toLowerCase();
  if (!term) return items;
  return items.filter((item) => getLabel(item).toLowerCase().includes(term));
}

/**
 * Open/close, highlight and keyboard handling.
 *
 * Extracted so both controls behave identically - a user who learns the arrow
 * keys on the city field should not have to relearn them on languages.
 */
function useListbox({
  itemCount,
  onSelectIndex,
  closeOnSelect,
}: {
  itemCount: number;
  onSelectIndex: (index: number) => void;
  closeOnSelect: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  // Clamp when the list shrinks under an active highlight.
  useEffect(() => {
    setHighlighted((current) =>
      itemCount === 0 ? 0 : Math.min(current, itemCount - 1),
    );
  }, [itemCount]);

  // Keep the highlighted option in view during keyboard navigation.
  useEffect(() => {
    if (!open) return;
    listRef.current
      ?.querySelector<HTMLElement>(`[data-index="${highlighted}"]`)
      ?.scrollIntoView({ block: "nearest" });
  }, [highlighted, open]);

  useEffect(() => {
    if (!open) return;

    const onPointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };

    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLInputElement>) => {
      switch (event.key) {
        case "ArrowDown":
          event.preventDefault();
          if (!open) {
            setOpen(true);
            return;
          }
          setHighlighted((i) => (itemCount === 0 ? 0 : (i + 1) % itemCount));
          return;

        case "ArrowUp":
          event.preventDefault();
          if (!open) {
            setOpen(true);
            return;
          }
          setHighlighted((i) =>
            itemCount === 0 ? 0 : (i - 1 + itemCount) % itemCount,
          );
          return;

        case "Home":
          if (!open) return;
          event.preventDefault();
          setHighlighted(0);
          return;

        case "End":
          if (!open) return;
          event.preventDefault();
          setHighlighted(Math.max(0, itemCount - 1));
          return;

        case "Enter":
          if (!open || itemCount === 0) return;
          // Only swallow Enter when it selects something - otherwise the
          // surrounding form must still be submittable from this field.
          event.preventDefault();
          onSelectIndex(highlighted);
          if (closeOnSelect) setOpen(false);
          return;

        case "Escape":
          if (!open) return;
          event.preventDefault();
          setOpen(false);
          return;

        case "Tab":
          // Tab commits nothing and moves on, per the ARIA pattern.
          setOpen(false);
          return;

        default:
      }
    },
    [closeOnSelect, highlighted, itemCount, onSelectIndex, open],
  );

  return {
    open,
    setOpen,
    highlighted,
    setHighlighted,
    handleKeyDown,
    containerRef,
    listRef,
  };
}

/** Popover list, including the loading/empty/error states. */
function ComboboxList<T>({
  listId,
  optionIdPrefix,
  items,
  highlighted,
  isSelected,
  onPick,
  onHighlight,
  listRef,
  getKey,
  getLabel,
  getDescription,
  loading,
  emptyMessage,
  errorMessage,
  hintMessage,
}: {
  listId: string;
  optionIdPrefix: string;
  items: T[];
  highlighted: number;
  isSelected: (item: T) => boolean;
  onPick: (index: number) => void;
  onHighlight: (index: number) => void;
  listRef: React.RefObject<HTMLUListElement | null>;
  getKey: (item: T) => string;
  getLabel: (item: T) => string;
  getDescription?: (item: T) => string | undefined;
  loading?: boolean;
  emptyMessage: string;
  errorMessage?: string;
  hintMessage?: string;
}) {
  return (
    <div className="absolute z-50 mt-1 w-full overflow-hidden rounded-lg border border-border bg-background shadow-lg">
      {/*
        Status messages live in a live region so a screen-reader user learns that
        results arrived without having to arrow into the list to find out.
      */}
      {errorMessage ? (
        <p role="alert" className="px-3 py-2 text-sm text-destructive">
          {errorMessage}
        </p>
      ) : loading ? (
        <p
          role="status"
          className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground"
        >
          <Spinner size="sm" />
          Loading…
        </p>
      ) : hintMessage ? (
        <p role="status" className="px-3 py-2 text-sm text-muted-foreground">
          {hintMessage}
        </p>
      ) : items.length === 0 ? (
        <p role="status" className="px-3 py-2 text-sm text-muted-foreground">
          {emptyMessage}
        </p>
      ) : null}

      <ul
        id={listId}
        ref={listRef}
        role="listbox"
        className={cn(
          "max-h-60 overflow-y-auto py-1",
          (loading || errorMessage || hintMessage || items.length === 0) &&
            "hidden",
        )}
      >
        {items.map((item, index) => {
          const selected = isSelected(item);
          const description = getDescription?.(item);

          return (
            <li
              key={getKey(item)}
              id={`${optionIdPrefix}-${index}`}
              data-index={index}
              role="option"
              aria-selected={selected}
              /*
                pointerdown, not click: the input's blur/outside-click handler
                would otherwise close the list before click fires.
              */
              onPointerDown={(event) => {
                event.preventDefault();
                onPick(index);
              }}
              onPointerMove={() => onHighlight(index)}
              className={cn(
                "flex cursor-pointer items-center justify-between gap-2 px-3 py-2 text-sm",
                index === highlighted && "bg-accent text-accent-foreground",
              )}
            >
              <span className="min-w-0">
                <span className="block truncate">{getLabel(item)}</span>
                {description ? (
                  <span className="block truncate text-xs text-muted-foreground">
                    {description}
                  </span>
                ) : null}
              </span>

              {selected ? (
                <Check className="size-4 shrink-0 text-primary" aria-hidden />
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const TRIGGER_CLASSES =
  "flex w-full items-center gap-2 rounded-lg border border-input bg-background px-3 py-2 text-sm " +
  "focus-within:outline-none focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 " +
  "focus-within:ring-offset-background " +
  "aria-[invalid=true]:border-destructive";

/* ---------------------------------------------------------- single select */

export function Combobox<T>({
  value,
  onChange,
  items,
  getKey,
  getLabel,
  getDescription,
  placeholder = "Select…",
  disabled = false,
  loading = false,
  emptyMessage = "No matches",
  errorMessage,
  hintMessage,
  onQueryChange,
  id,
  className,
  ...aria
}: BaseProps<T> & {
  value: T | null;
  onChange: (value: T | null) => void;
}) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const listId = `${generatedId}-listbox`;

  const [query, setQuery] = useState("");

  /* Caller-driven search skips local filtering - the server already filtered. */
  const visible = useMemo(
    () => (onQueryChange ? items : localFilter(items, query, getLabel)),
    [items, onQueryChange, query, getLabel],
  );

  const pick = useCallback(
    (index: number) => {
      const item = visible[index];
      if (!item) return;
      onChange(item);
      setQuery("");
      onQueryChange?.("");
    },
    [onChange, onQueryChange, visible],
  );

  const listbox = useListbox({
    itemCount: visible.length,
    onSelectIndex: pick,
    closeOnSelect: true,
  });

  const { open, setOpen, highlighted, handleKeyDown, containerRef, listRef } =
    listbox;

  // When closed, the field reads as the current selection rather than as a
  // half-typed search term.
  const displayed = open ? query : (value ? getLabel(value) : "");

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div className={TRIGGER_CLASSES} aria-invalid={aria["aria-invalid"]}>
        <input
          id={inputId}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={
            open && visible.length > 0 ? `${listId}-${highlighted}` : undefined
          }
          aria-invalid={aria["aria-invalid"]}
          aria-describedby={aria["aria-describedby"]}
          autoComplete="off"
          disabled={disabled}
          value={displayed}
          placeholder={value ? getLabel(value) : placeholder}
          onChange={(event) => {
            setQuery(event.target.value);
            onQueryChange?.(event.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
        />

        {value && !disabled ? (
          <button
            type="button"
            aria-label="Clear selection"
            onClick={() => {
              onChange(null);
              setQuery("");
              onQueryChange?.("");
            }}
            className="rounded text-muted-foreground hover:text-foreground"
          >
            <X className="size-4" aria-hidden />
          </button>
        ) : null}

        <ChevronsUpDown
          className="size-4 shrink-0 text-muted-foreground"
          aria-hidden
        />
      </div>

      {open && !disabled ? (
        <ComboboxList
          listId={listId}
          optionIdPrefix={listId}
          items={visible}
          highlighted={highlighted}
          isSelected={(item) => Boolean(value) && getKey(item) === getKey(value as T)}
          onPick={pick}
          onHighlight={listbox.setHighlighted}
          listRef={listRef}
          getKey={getKey}
          getLabel={getLabel}
          getDescription={getDescription}
          loading={loading}
          emptyMessage={emptyMessage}
          errorMessage={errorMessage}
          hintMessage={hintMessage}
        />
      ) : null}
    </div>
  );
}

/* ----------------------------------------------------------- multi select */

export function MultiCombobox<T>({
  value,
  onChange,
  items,
  getKey,
  getLabel,
  getDescription,
  placeholder = "Select…",
  disabled = false,
  loading = false,
  emptyMessage = "No matches",
  errorMessage,
  hintMessage,
  onQueryChange,
  id,
  className,
  renderChip,
  ...aria
}: BaseProps<T> & {
  value: T[];
  onChange: (value: T[]) => void;
  /** Chip label, when it should differ from the option label. */
  renderChip?: (item: T) => ReactNode;
}) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const listId = `${generatedId}-listbox`;

  const [query, setQuery] = useState("");

  const visible = useMemo(
    () => (onQueryChange ? items : localFilter(items, query, getLabel)),
    [items, onQueryChange, query, getLabel],
  );

  const selectedKeys = useMemo(
    () => new Set(value.map(getKey)),
    [value, getKey],
  );

  /** Toggles rather than appends, so an option acts as a checkbox. */
  const toggle = useCallback(
    (index: number) => {
      const item = visible[index];
      if (!item) return;

      const key = getKey(item);
      onChange(
        selectedKeys.has(key)
          ? value.filter((selected) => getKey(selected) !== key)
          : [...value, item],
      );
    },
    [getKey, onChange, selectedKeys, value, visible],
  );

  const listbox = useListbox({
    itemCount: visible.length,
    onSelectIndex: toggle,
    // Stays open: choosing several is the normal case here.
    closeOnSelect: false,
  });

  const { open, setOpen, highlighted, handleKeyDown, containerRef, listRef } =
    listbox;

  const onInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    // Backspace on an empty field removes the last chip - the behaviour every
    // tag input has, and the only way to undo without reaching for the mouse.
    if (event.key === "Backspace" && query === "" && value.length > 0) {
      onChange(value.slice(0, -1));
      return;
    }
    handleKeyDown(event);
  };

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div
        className={cn(TRIGGER_CLASSES, "flex-wrap")}
        aria-invalid={aria["aria-invalid"]}
      >
        {value.map((item) => (
          <span
            key={getKey(item)}
            className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
          >
            {renderChip ? renderChip(item) : getLabel(item)}
            {!disabled ? (
              <button
                type="button"
                aria-label={`Remove ${getLabel(item)}`}
                onClick={() =>
                  onChange(
                    value.filter((s) => getKey(s) !== getKey(item)),
                  )
                }
                className="rounded hover:text-destructive"
              >
                <X className="size-3" aria-hidden />
              </button>
            ) : null}
          </span>
        ))}

        <input
          id={inputId}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={
            open && visible.length > 0 ? `${listId}-${highlighted}` : undefined
          }
          aria-invalid={aria["aria-invalid"]}
          aria-describedby={aria["aria-describedby"]}
          autoComplete="off"
          disabled={disabled}
          value={query}
          placeholder={value.length === 0 ? placeholder : ""}
          onChange={(event) => {
            setQuery(event.target.value);
            onQueryChange?.(event.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onInputKeyDown}
          className="min-w-[6rem] flex-1 bg-transparent outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
        />

        <ChevronsUpDown
          className="size-4 shrink-0 text-muted-foreground"
          aria-hidden
        />
      </div>

      {open && !disabled ? (
        <ComboboxList
          listId={listId}
          optionIdPrefix={listId}
          items={visible}
          highlighted={highlighted}
          isSelected={(item) => selectedKeys.has(getKey(item))}
          onPick={toggle}
          onHighlight={listbox.setHighlighted}
          listRef={listRef}
          getKey={getKey}
          getLabel={getLabel}
          getDescription={getDescription}
          loading={loading}
          emptyMessage={emptyMessage}
          errorMessage={errorMessage}
          hintMessage={hintMessage}
        />
      ) : null}
    </div>
  );
}
