# Frontend Flows: Merchandising Console

Maintained by `angular-architect` · Design: plan §F · ADR-8, ADR-9 · Theme: [ui-theme.md](../../design/ui-theme.md)

## Status
| Flow | Status | Task |
|---|---|---|
| Flow A: Catalog and simulate sale | 🟩 Implemented | FE-04 |
| Flow B: New suggestions appear | 🟩 Implemented | FE-02, FE-05 |
| Flow C: Accept or reject | 🟩 Implemented | FE-05 |
| Flow D: Switch strategy | 🟩 Implemented | FE-06 |
| Flow E: Errors and Error ref | 🟩 Implemented | FE-02 |

## Stack
Angular 19 (standalone by default, `input()`/`output()`, control flow, OnPush) · Angular Material 19 (`mat.theme`, density −2) · signals + RxJS stores · no NgRx · tooling in Docker on Node 22.

## Component Tree (planned)
```
AppComponent
├─ TopBarComponent              nav (Review · Catalog), strategy toggle, loading bar
├─ ReviewQueuePage   ('')       SuggestionsStore + ProductsStore
│   └─ ProductReviewRow ×n      product line + cards
│       ├─ PricingSuggestionCard   price → price, ▲%, confidence, badges, reasoning, Accept/Reject
│       └─ ReorderSuggestionCard   +qty, lead time, confidence, badges, reasoning, Accept/Reject
└─ CatalogPage       ('products')
    └─ ProductTable             stock/threshold, price, velocity, status, Sell 1, Set stock, Suggest
shared: TriggerBadge, SourceChip, DirectionDelta, ConfidenceBar, EmptyState, ErrorState
```

## Flow A: Catalog and simulate sale
"Sell 1" → `ProductsApi.order(id, 1)` (`exhaustMap`, button disabled while pending) → on `201`, update the row from the response → the loop runs server-side. `INSUFFICIENT_STOCK` → inline message on the row.

## Flow B: New suggestions appear
`PollingService` (`timer(0, 5000)` + `switchMap`, paused when the tab is hidden, backoff on error) → `forkJoin(GET /pricing-suggestions?status=PENDING, GET /reorder-suggestions?status=PENDING)` → `SuggestionsStore` → `computed` grouping by product → the review queue renders. New cards appear within about 5 s of the order; an `aria-live` region announces the pending count.

## Flow C: Accept or reject
Accept → `PATCH /pricing-suggestions/{id} {status:'ACCEPTED'}` → disable both buttons → on success, refresh suggestions and products immediately → toast "Price updated to $X". `409 NOT_PENDING` → "Already decided", then refresh.

## Flow D: Switch strategy
Top-bar toggle → `PUT /admin/strategies` → on `400 UNKNOWN_STRATEGY`, show the valid options. It affects the next suggestion only.

## Flow E: Errors and Error ref
`errorInterceptor` maps the standard error body to `ApiError`; views show the message + `Error ref: <traceId first 8>` + Retry. `GlobalErrorHandler` catches unexpected errors and shows a toast, so the screen never goes blank. There is no frontend log file (CLAUDE.md rule 8).

## Deviations from Design
- Polling runs app-wide (started in `AppComponent`), so the top-bar pending count and both pages share one 5 s poll per resource. After "Sell 1" / "Set stock" the console also re-checks after 1.2 s (`SuggestionsStore.refreshSoon`) so cards appear faster than the next tick.
- Native `<select>` / `<table>` styled with tokens instead of `mat-select` / `mat-table`: lighter, fully accessible, closer to the minimal theme.
- Selector prefix `sp-`; routed screens are `*Page` (lint config).
- `@angular/animations@19` added (required by Material snack bar / `provideAnimationsAsync`).
- Extra dev proxy `proxy.host.conf.json` (dev server in Docker → backend on the host via `host.docker.internal`).

## Verification
_Filled by `qa-engineer` (QA-05)._

## Implementation Log
| Date | Task | Change | Notes |
|---|---|---|---|
| 2026-11-06 | SE-00 | Skeleton created | |
| 2026-11-06 | FE-01..FE-06 | Angular 19.2 in Docker (Node 22): `core/{api,errors,polling,layout,notify}`, `features/console/{data/suggestions.store,components/*-suggestion-card,pages/review-queue.page}`, `features/products/{data/products.store,pages/catalog.page}`, `shared/components/*`, tokens in `src/styles/_tokens.scss`. 18 Karma tests, lint clean | Run: `FRONTEND_PROXY=proxy.host.conf.json docker compose --profile frontend up --no-deps frontend` with the backend on the host |
| 2026-11-06 | FE-07 | UI ceiling: catalog margin column (server `marginPct`), stock heat bar (fill vs 3× threshold; amber low, red out, always with numbers), expandable price-history step chart (`features/products/components/price-history-chart.component.ts`: single series, 2px ink line, 8px markers, hover/focus tooltip, table view), pricing cards show margin before → after. Backend `V7__seed_cost_prices.sql` seeds cost prices. 22 Karma tests | Charts follow the dataviz rules: one axis, no legend for one series, text in ink tokens |
| 2026-11-06 | FE-05 rev | White & blue theme (`_tokens.scss`), rupees everywhere (`LOCALE_ID en-IN`, `InrPipe`, `formatInr`), **one recommendation card per product** (`features/console/components/recommendation-card.component.ts`) with per-line accept/reject, Accept all (price then restock), Reject all, Recalculate; catalog "↻ Recommend"; old separate price/reorder cards removed. 26 Karma tests | ADR-7 revision |
