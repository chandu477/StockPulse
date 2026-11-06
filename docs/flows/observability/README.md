# Observability: Errors, Logs, Tracing

Maintained by `backend-engineer` + `angular-architect` · ADR-9

## Status
| Section | Status | Task |
|---|---|---|
| §1 Standard error response | 🟩 Implemented | BE-03 |
| §2 Trace id | 🟩 Implemented | BE-03 |
| §3 Log files | 🟩 Implemented | BE-03 |
| §4 Event catalogue | 🟩 Implemented | BE-03+ |
| §5 Frontend error handling | 🟩 Implemented | FE-02 |
| §6 Tracing a failure | 🟦 Designed | — |

## 1. Standard error response
**Every** non-2xx response, from controllers, filters, `/error` and security handlers, uses this shape:
```json
{
  "timestamp": "2026-11-06T10:15:30.123Z",
  "status": 409,
  "error": "Conflict",
  "code": "NOT_PENDING",
  "message": "Suggestion 42 is already ACCEPTED.",
  "path": "/api/v1/pricing-suggestions/42",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "quantity", "message": "must be ≥ 1", "rejectedValue": 0 } ]
}
```
- `errors` is always present (empty unless the error is `VALIDATION_FAILED`).
- No stack traces, SQL, class names or provider details. `server.error.include-stacktrace=never`, whitelabel off, ProblemDetail off.
- Built only by `ApiErrorResponseFactory`.

| Code | HTTP |
|---|---|
| `VALIDATION_FAILED` | 400 |
| `UNKNOWN_STRATEGY` | 400 |
| `PRODUCT_NOT_FOUND`, `SUGGESTION_NOT_FOUND` | 404 |
| `DUPLICATE_SKU`, `INSUFFICIENT_STOCK`, `NOT_PENDING`, `CONCURRENT_MODIFICATION` | 409 |
| `INTERNAL_ERROR` | 500 |

## 2. Trace id
A filter reads `traceparent` / `X-Trace-Id`, or generates an id. It is put in MDC, returned in the `X-Trace-Id` header and included in every error body. Async work carries it through the outbox payload and AMQP headers.

## 3. Log files (`backend/logs/`, JSON Lines, never committed)
| File | Content |
|---|---|
| `stockpulse-backend.log` | all application logs (INFO+) |
| `stockpulse-backend-failures.log` | WARN/ERROR only |
| `stockpulse-access.log` | one line per HTTP request: method, path, status, duration, traceId |
| `stockpulse-audit.log` | business decisions: suggestion created/accepted/rejected/superseded, strategy changed |

Every line has `@timestamp`, `level`, `event`, `traceId`, `logger`, `message` and context fields. **Never** log `LLM_API_KEY`, `Authorization` headers or passwords.

## 4. Event catalogue
| Event | Level | Where |
|---|---|---|
| `ORDER_RECORDED`, `STOCK_ADJUSTED` | INFO | inventory |
| `OUTBOX_PUBLISHED` / `OUTBOX_PUBLISH_FAILED` | DEBUG / ERROR | relay |
| `TRIGGER_EVALUATED` | INFO | loop |
| `SUGGESTION_DEDUPLICATED` | INFO | recommendation |
| `SUGGESTION_CREATED` | INFO (audit) | recommendation |
| `LLM_CALL` (provider, model, latency, tokens) | INFO | llm |
| `LLM_FALLBACK` (reason) | WARN | llm |
| `SUGGESTION_ACCEPTED` / `_REJECTED` / `_SUPERSEDED` | INFO (audit) | approval |
| `STRATEGY_CHANGED` | INFO (audit) | admin |
| `MESSAGE_DEAD_LETTERED` | ERROR | loop |
| `REQUEST_FAILED` | WARN/ERROR | error handler |

## 5. Frontend error handling
`errorInterceptor` → typed `ApiError` (network failure → `NETWORK_ERROR`). Views show the message + `Error ref: <traceId first 8>` + Retry. `GlobalErrorHandler` shows a toast, so the screen never goes blank. **No frontend log file** in the MVP; hosted error tracking is a production item.

## 6. Tracing a failure
User reports "Error ref 4bf92f35" → `grep 4bf92f35 backend/logs/*.log` → the access line, the failure line and the related audit/LLM lines all share the id.

## Implementation Log
| Date | Task | Change | Notes |
|---|---|---|---|
| 2026-11-06 | SE-00 | Skeleton created | |
| 2026-11-06 | BE-03 | `shared/error/*` (factory, handler, `/error` controller), `shared/web/{TraceIdFilter,AccessLogFilter}`, `logback-spring.xml` (ECS JSON Lines, 4 files) | Extra codes `NOT_FOUND`, `METHOD_NOT_ALLOWED` for unknown paths/methods |
