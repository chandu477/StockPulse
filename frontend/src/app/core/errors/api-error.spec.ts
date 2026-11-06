import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ApiError, toApiError } from './api-error';

describe('toApiError', () => {
  it('maps the standard error shape', () => {
    const e = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: {
          timestamp: 't',
          status: 409,
          error: 'Conflict',
          code: 'NOT_PENDING',
          message: 'Suggestion 1 is already ACCEPTED.',
          path: '/api/v1/pricing-suggestions/1',
          traceId: '4bf92f3577b34da6',
          errors: [],
        },
      }),
    );
    expect(e).toEqual(jasmine.any(ApiError));
    expect(e.code).toBe('NOT_PENDING');
    expect(e.status).toBe(409);
    expect(e.ref).toBe('4bf92f35');
  });

  it('maps a network failure', () => {
    const e = toApiError(new HttpErrorResponse({ status: 0 }));
    expect(e.code).toBe('NETWORK_ERROR');
    expect(e.message).toContain('Cannot reach the server');
  });

  it('falls back to the X-Trace-Id header for non-standard bodies', () => {
    const e = toApiError(
      new HttpErrorResponse({ status: 502, error: 'Bad gateway', headers: new HttpHeaders({ 'X-Trace-Id': 'abcdef123456' }) }),
    );
    expect(e.code).toBe('UNEXPECTED_RESPONSE');
    expect(e.ref).toBe('abcdef12');
  });

  it('wraps unknown errors', () => {
    expect(toApiError(new TypeError('boom')).code).toBe('CLIENT_ERROR');
  });
});
