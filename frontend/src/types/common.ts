/**
 * Shared primitives for the VakilConnect API contract.
 *
 * These types mirror the frozen Spring Boot backend exactly. When the backend
 * says a field is nullable, so does the type - smoothing that over here only
 * moves the null check somewhere less obvious.
 */

/** ISO-8601 calendar date, e.g. `"2026-08-03"` (Java `LocalDate`). */
export type IsoDate = string;

/**
 * Time of day as `HH:mm:ss`, e.g. `"10:30:00"` (Java `LocalTime`, Jackson default).
 *
 * Note: availability windows use {@link ShortTime} (`HH:mm`) instead, because
 * those DTOs carry `@JsonFormat(pattern = "HH:mm")`. Booking submits this format.
 */
export type IsoTime = string;

/** Time of day as `HH:mm`, e.g. `"10:00"` - used by availability DTOs only. */
export type ShortTime = string;

/** ISO-8601 local date-time, e.g. `"2026-07-27T19:15:04.795"` (Java `LocalDateTime`). */
export type IsoDateTime = string;

/** UUID string as produced by the backend. */
export type Uuid = string;

/**
 * Paged response envelope.
 *
 * Matches Spring Data's `PagedModel`, which the backend opts into via
 * `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`. This is
 * deliberately NOT the legacy `PageImpl` shape (`pageable`, `sort`, `first`,
 * `last`, ...), whose structure Spring documents as unstable.
 */
export interface Paged<T> {
  content: T[];
  page: {
    /** Requested page size. */
    size: number;
    /** Zero-based page index. */
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Query parameters accepted by every paged endpoint. */
export interface PageParams {
  /** Zero-based. Backend default: 0. */
  page?: number;
  /** Backend default: 10. */
  size?: number;
}

/**
 * The backend's `ErrorResponse` body, returned by `GlobalExceptionHandler` for
 * every handled failure (400/401/403/404/409) and by the security handlers.
 */
export interface ApiErrorResponse {
  timestamp: IsoDateTime;
  status: number;
  /** HTTP reason phrase, e.g. `"Conflict"`. */
  error: string;
  message: string;
  path: string;
  /**
   * Present only for bean-validation failures (HTTP 400), mapping field name
   * to message. Maps directly onto React Hook Form's `setError`.
   */
  fieldErrors: Record<string, string> | null;
}

/**
 * Normalised error thrown by the Axios layer.
 *
 * Every failure - HTTP, network, or malformed - surfaces as this single type,
 * so callers never inspect `AxiosError` or guess at response shapes.
 *
 * Instances are immutable at runtime as well as compile time: a single error
 * object is shared by TanStack Query's cache across every component observing
 * that query, so one consumer must not be able to mutate what another sees.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Readonly<Record<string, string>> | null;
  readonly path: string | null;

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> | null = null,
    path: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors ? Object.freeze({ ...fieldErrors }) : null;
    this.path = path;

    // Must precede freeze(): restores the prototype chain so `instanceof`
    // works when targeting ES5-era output.
    Object.setPrototypeOf(this, ApiError.prototype);

    Object.freeze(this);
  }

  /** No HTTP response was received (offline, DNS failure, CORS rejection). */
  get isNetworkError(): boolean {
    return this.status === 0;
  }

  /** Bean-validation failure carrying per-field messages. */
  get isValidationError(): boolean {
    return this.status === 400 && this.fieldErrors !== null;
  }

  /**
   * A business-rule conflict, e.g. slot already booked, illegal status
   * transition, duplicate email.
   */
  get isConflict(): boolean {
    return this.status === 409;
  }

  /**
   * Not found.
   *
   * Note: the backend deliberately returns 404 (not 403) when a user acts on a
   * resource they do not own, so as not to disclose its existence. Treat this
   * on mutations as "no longer available" and refetch, rather than as a
   * permissions error.
   */
  get isNotFound(): boolean {
    return this.status === 404;
  }
}

/** Type guard for use in `catch` blocks and query error handlers. */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
