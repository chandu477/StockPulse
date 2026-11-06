import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorResponse, FieldError } from '../api/api.models';

/** Typed error used across the app. Switch on `code`, never on the message text. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly traceId: string | null,
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** Short reference shown to users and searchable in backend/logs. */
  get ref(): string | null {
    return this.traceId ? this.traceId.slice(0, 8) : null;
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiErrorResponse).code === 'string' &&
    typeof (value as ApiErrorResponse).message === 'string'
  );
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    const headerTrace = error.headers?.get('X-Trace-Id') ?? null;
    if (error.status === 0) {
      return new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check that the backend is running.', headerTrace);
    }
    if (isApiErrorBody(error.error)) {
      const body = error.error;
      return new ApiError(body.status, body.code, body.message, body.traceId || headerTrace, body.errors ?? []);
    }
    return new ApiError(error.status, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.', headerTrace);
  }
  return new ApiError(-1, 'CLIENT_ERROR', 'Something went wrong in the browser.', null);
}
