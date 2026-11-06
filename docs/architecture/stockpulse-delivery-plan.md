# StockPulse: Delivery Plan

**Source of truth** for requirements, architecture, contracts, tasks, testing and readiness (see `CLAUDE.md`).
Owner: Senior Engineer · Last revised: 2026-11-06 · Decisions: [ADR.md](../../ADR.md)

> **Revision 2026-11-06.** The frontend moves from Angular 17 to **Angular 19** (ADR-8 revised). The Angular tooling runs on **Node 22** in Docker because the host has Node 26 and Angular 19 supports only Node 18.19, 20.11 and 22. The default LLM is a **free-tier** provider: Groq, with Ollama for offline use (ADR-10). The design system is **minimalist** ([ui-theme.md](../design/ui-theme.md)).

---

## 0. Scope

### 0.1 Problem
When stock crosses its reorder threshold or demand velocity spikes, StockPulse detects it automatically. It then generates a **pricing suggestion** and a **reorder suggestion** (from AI, falling back to rules) and queues both for a merchandiser to approve. Nothing changes price or stock until a human accepts.

### 0.2 The loop
`order / stock PATCH` → **observe** (outbox event) → **reason** (strategy: AI or rule) → **act** (queue PENDING suggestions) → **checkpoint** (merchandiser accepts or rejects) → price or stock updated atomically.

### 0.7 Out of scope (MVP)
Storefront, cart, payments, catalog editing beyond create, competitor scraping, supplier catalog, automated purchase orders, auto-apply pricing, price-change cooldown, authentication (Q-13), and a rolling 24 h velocity window (Q-2). See ADR-6.

### 0.8 Open questions: proposed defaults (accepted pending stakeholder confirmation)

| # | Question | Default |
|---|---|---|
| Q-1 | Product id format | String id. Seeds use `PRD-001…`; new products get `PRD-` + a sequence |
| Q-2 | Demand velocity semantics | The stored counter is incremented by order quantity. No time decay in the MVP |
| Q-3 | Spike threshold | velocity > `stockpulse.trigger.spike-multiplier` (3.0) × **peer** category average (**excluding the product itself**, see §C.5) |
| Q-4 | Order larger than available stock | `409 INSUFFICIENT_STOCK`; stock never goes negative |
| Q-5 | Effect of accepting a reorder | `stockLevel += recommendedQuantity` immediately (simulated inbound) |
| Q-6 | Can the merchandiser edit the value on accept? | No. Accept as-is or reject (an optional `note` is stored) |
| Q-7 | Edge- or level-triggered | **Level-triggered**: every event re-evaluates the conditions. Dedupe prevents repeats |
| Q-8 | Product status | Derived (ADR-7) |
| Q-9 | Stale sibling suggestions after accept | `SUPERSEDED` (ADR-7) |
| Q-10 | Both triggers on one event | Each trigger produces its own pair of suggestions with its own `pending_key` |
| Q-11 | "Switchable at runtime" | Admin endpoint + persisted setting (ADR-3) |
| Q-12 | Price sanity bounds | 0.5×–2.0× the current price; outside → `OUT_OF_BOUNDS` fallback |
| Q-13 | Auth | None in the demo; production profile in §I |
| Q-14 | Unified or split AI call | Split, with two parallel calls (ADR-2) |
| Q-15 | Manual suggest while one is PENDING | Return the existing PENDING suggestion (`200`, header `X-Deduplicated: true`) |
| Q-16 | Money | `BigDecimal`, scale 2, `HALF_UP`, single currency |
| Q-17 | Manual suggest latency | Synchronous, bounded at about 10.5 s (connect 2 s + read 10 s + rule fallback) |
| Q-18 | Max reorder quantity | `stockpulse.reorder.max-quantity` = 10 000 |

---

## A. Requirements

### Functional
| Id | Requirement |
|---|---|
| FR-1 | Create and list products (filter by `status`, `category`) |
| FR-2 | `PATCH` stock; simulate an order (decrement stock, increase velocity) |
| FR-3 | Rule pricing: stock < threshold → +10%; velocity > 2× peer average → +5%; otherwise HOLD |
| FR-4 | Rule reorder: `max(1, threshold × 3 − stock)` |
| FR-5 | AI pricing and reorder strategies, switchable at runtime with no restart |
| FR-6 | On-demand `suggest-pricing` / `suggest-reorder` |
| FR-7 | Agentic loop: `INVENTORY_LOW` and `DEMAND_SPIKE` generate both suggestion types asynchronously and are idempotent |
| FR-8 | Accept or reject suggestions. Accepting pricing sets `currentPrice`; accepting reorder increments stock. Both are atomic |
| FR-9 | Merchandising console: review queue, badges, product table, simulate sale, polling, loading and error states |
| FR-10 | *(bonus)* SSE stream of the AI's reasoning |

### Business rules
- **BR-1** Prices change only by accepting a PricingSuggestion.
- **BR-2** No LLM call inside a DB transaction.
- **BR-3** AI failure → rule fallback with a `fallbackReason`. Never drop a suggestion.
- **BR-4** At most one PENDING suggestion per (product, triggerReason, type).
- **BR-5** Clients never set product status.

### Success metrics (demo / MVP)
- README to a running app in < 5 min (H2, `local` profile, no Docker).
- Order endpoint p95 < 150 ms (no AI on the request path).
- Suggestions visible in the UI ≤ 5 s after the triggering order.
- 0 silent drops across QA-03 failure injection.

---

## B. Architecture overview

```
Angular 19 console ──HTTP /api/v1──▶ Spring Boot 3 (flat layered application)
   (polling 5 s)                      ├─ catalog        products, ProductView, status policy
                                      ├─ inventory      stock / orders, inventory_events, outbox_events
                                      ├─ recommendation strategies, registry, RecommendationService, listener
                                      │     └─ ai ──▶ integration.llm (LLMGateway: groq | gemini | ollama)
                                      ├─ approval       accept / reject, SUPERSEDED, side effects
                                      └─ shared         errors, logging, settings
                                      │
                       outbox relay ──▶ RabbitMQ  stockpulse.inventory → recommendation.requests (+ DLQ)
                                      │   (in-process mode on the local H2 profile)
                                      ▼
                                PostgreSQL 16 / H2
```

---

## C. Design

### C.3 Package layers and boundaries (ArchUnit-enforced, ADR-1)
Base package: `com.stockpulse`. Java files are grouped into flat `controller`, `service`, and `entity` packages. Descriptive class names (for example `ProductController`, `InventoryService`, and `PricingSuggestion`) identify the business area.
- Controllers handle HTTP and do not depend on messaging or AI-provider infrastructure.
- Entities and domain data types do not depend on controllers, services, messaging, or integrations.
- Only recommendation AI code imports `integration.llm`.
- Feature separation is communicated through class names and code review; it is not enforced by package boundaries.

### C.4 Domain model

**Product**: `id`, `sku` (unique), `name`, `category` {ELECTRONICS, APPAREL, HOME}, `currentPrice`, `stockLevel` (≥0), `reorderThreshold` (≥0), `demandVelocity` (≥0), `status` (derived), `costPrice?`, `marginFloor?`, `supplierId?` (Sprint 2 seams), `version` (optimistic lock), timestamps.

Derived status (`ProductStatusPolicy`):
```
stock == 0          → OUT_OF_STOCK
any PENDING sugg.   → PRICE_REVIEW_PENDING
else                → ACTIVE
```

**PricingSuggestion**: `id`, `productId`, `currentPrice` (snapshot), `recommendedPrice`, `direction` {INCREASE, DECREASE, HOLD}, `confidence` [0,1], `reasoning`, `status`, `triggerReason`, `source` {AI, RULE, RULE_FALLBACK}, `strategyKey`, `fallbackReason?`, `pendingKey?` (unique, nullable), `decidedAt?`, `decisionNote?`, `createdAt`.

**ReorderSuggestion**: the same, with `currentStock`, `recommendedQuantity` (1..max), `leadTimeDays`, and no price fields.

Suggestion state machine:
```
PENDING ──accept──▶ ACCEPTED
   │   ──reject──▶ REJECTED
   └── sibling of the same type accepted ──▶ SUPERSEDED
(terminal states are final; acting on them → 409 NOT_PENDING)
```

`TriggerReason`: INITIAL, INVENTORY_LOW, DEMAND_SPIKE, MANUAL.

### C.5 Trigger evaluation (`TriggerEvaluator`)
- `INVENTORY_LOW`: `stockLevel < reorderThreshold` (and > 0, or = 0; out-of-stock still needs a reorder).
- `DEMAND_SPIKE`: `velocity > multiplier × peerAvg(category, excluding this product)`.
- **Why "peers, excluding self":** if the average includes the product, then for 3 apparel products the spike condition `v > 3·(v+others)/3` reduces to `others < 0`, which **can never fire**. FR-3's 2× rule has the same problem. With the seed data, PRD-008 (velocity 15) against peers PRD-003/PRD-004 (avg 7): the 2× rule fires now (15 > 14) and the 3× spike fires at velocity > 21 (≈ 7–8 more units sold, a bit more if PRD-003 was sold first, because that raises the peer average). This is the demand-spike demo path.
- If peer average = 0 (no peers or all zero), the spike is not evaluated (avoids division by zero and false spikes).

### C.6 Strategy contracts (ADR-2, ADR-3)
```java
record CommerceContext(ProductView product, TriggerReason trigger,
                       BigDecimal peerAvgVelocity, int peerCount, Instant at) {}

interface PricingStrategy { PricingAdvice advise(CommerceContext ctx); }
interface ReorderStrategy { ReorderAdvice advise(CommerceContext ctx); }

record PricingAdvice(BigDecimal recommendedPrice, Direction direction, double confidence,
                     String reasoning, Source source, FallbackReason fallbackReason) {}
record ReorderAdvice(int recommendedQuantity, int leadTimeDays, double confidence,
                     String reasoning, Source source, FallbackReason fallbackReason) {}
```
Beans: `@Component("rule")`, `@Component("ai")` (= `Fallback*Strategy` wrapping `Ai*Strategy`). `StrategyRegistry` resolves the active key from `RuntimeSettings` on every call. Sprint 2: `@Component("competitor")`.

`RecommendationService.generate(productId, trigger)` is the **one** entry point for HTTP and async callers:
1. Short tx: load `ProductView`, compute peer average, check the `pending_key` → exit early if PENDING exists.
2. **No tx:** run the pricing and reorder strategies in parallel (`CompletableFuture`, bounded executor `llmExecutor`).
3. Short tx: apply `PriceGuardrail` chain → insert both suggestions (a unique violation on `pending_key` = a concurrent duplicate → skip) → re-derive status → audit log.

### C.7 LLM integration (ADR-4, ADR-10)
- `LLMGateway.callLLM(prompt)` from Addendum B, completed for `groq` / `ollama` (OpenAI-compatible `/v1/chat/completions`, `temperature 0.2`, JSON mode where supported) and `gemini` (`generateContent`, `responseMimeType: application/json`).
- **Six prompt templates** (`resources/prompts/`): `{pricing,reorder}-{inventory-low,demand-spike,manual}.txt`. They differ in substance, not just one field:
  - *Pricing / inventory-low*: stock vs threshold, days of cover (`stock / velocity`), **explicit trade-off**: raise price to protect scarce stock vs clearance to move slow stock vs hold. Ask the model to state which it chose and why.
  - *Pricing / demand-spike*: velocity vs peer average (×multiple), stock headroom, risk of selling out. Bias toward a modest increase; warn about overreacting to a short spike.
  - *Reorder / inventory-low*: cover target (threshold × 3) and lead time. *Reorder / demand-spike*: size to the new velocity, cap at max quantity.
  - *Manual*: neutral review of the current situation.
- Product data goes inside a delimited `<<<DATA … DATA>>>` block, with an instruction to treat it as data only (prompt-injection defence).
- Output schemas as in the brief. Parsing: strip code fences → first `{…}` → strict Jackson.

### C.9 Resilience (ADR-4)
Timeouts 2 s / 10 s · `AdviceValidator` (bounds, direction consistency, confidence, quantity, non-blank reasoning) · Resilience4j breaker · budget 30 calls/min (matches typical free-tier RPM) · one jittered retry on 429/503 on the async path only · `fallbackReason` enum · `LLM_FALLBACK` WARN log + metric.

### C.10 ADR index
ADR-1 flat layered application · ADR-2 split contracts · ADR-3 registry switching · ADR-4 LLM failure handling · ADR-5 outbox + RabbitMQ · ADR-6 seams and exclusions · ADR-7 derived status + SUPERSEDED · ADR-8 Angular 19 + signals · ADR-9 error shape + logging · ADR-10 free-tier LLM provider.

### C.11 Agentic loop (ADR-5), summary
`InventoryService` (one tx: stock, velocity, `inventory_events`, `outbox_events`) → after commit, `OutboxRelay` publishes `inventory.changed` (publisher confirms) and a 5 s sweep picks up stragglers → `RecommendationRequestListener` → `processed_messages` check → `TriggerEvaluator` → `RecommendationService.generate` per firing trigger. Retries 1/2/4 s → `recommendation.dlq`. `in-process` mode on H2: same outbox, and an AFTER_COMMIT listener calls the same handler.

---

## D. Persistence (Flyway, never edit an applied migration)
| Migration | Content |
|---|---|
| `V1__catalog.sql` | `products` (+ nullable `cost_price`, `margin_floor`, `supplier_id`, `version`) |
| `V2__suggestions.sql` | `pricing_suggestions`, `reorder_suggestions` (unique `pending_key`, indexes on `(status, product_id)`) |
| `V3__inventory_events.sql` | `inventory_events` (type ORDER/ADJUST/INBOUND, delta, resulting stock, velocity) |
| `V4__messaging.sql` | `outbox_events`, `processed_messages` |
| `V5__app_settings.sql` | `app_settings(key PK, value, updated_at)` |
| `V6__seed.sql` | Addendum A (8 products). The status column is recomputed on startup by the policy |

`pending_key` = `productId|TRIGGER` while PENDING, set to `NULL` on leaving PENDING. This works as a partial-unique constraint on both H2 and Postgres.

---

## E. API contract (`contracts/openapi.yaml`, frozen after SE-02)
Base path `/api/v1`. All errors use `ApiErrorResponse` (ADR-9).

| Method | Path | Notes |
|---|---|---|
| POST | `/products` | `201`, `Location`. `409 DUPLICATE_SKU` |
| GET | `/products?status=&category=` | List; includes `pendingPricingCount`, `pendingReorderCount`, `marginPct` |
| GET | `/products/{id}` | |
| PATCH | `/products/{id}/stock` | `{stockLevel}` → `200` immediately; loop is async |
| POST | `/products/{id}/orders` | `{quantity ≥1}` → `201`; `409 INSUFFICIENT_STOCK` |
| POST | `/products/{id}/suggest-pricing` | `201` new / `200` dedup; `triggerReason=MANUAL` |
| POST | `/products/{id}/suggest-reorder` | same |
| GET | `/pricing-suggestions?status=&productId=` | review queue |
| GET | `/reorder-suggestions?status=&productId=` | review queue |
| PATCH | `/pricing-suggestions/{id}` | `{status: ACCEPTED\|REJECTED, note?}`; `409 NOT_PENDING` |
| PATCH | `/reorder-suggestions/{id}` | same |
| GET/PUT | `/admin/strategies` | `{pricing:"ai", reorder:"rule"}`; `400 UNKNOWN_STRATEGY` |
| GET | `/products/{id}/price-history` | *(UI ceiling)* from accepted pricing suggestions |
| POST | `/products/{id}/suggest-pricing/stream` | *(bonus)* `text/event-stream` |

Error codes: `VALIDATION_FAILED`, `PRODUCT_NOT_FOUND`, `SUGGESTION_NOT_FOUND`, `DUPLICATE_SKU`, `INSUFFICIENT_STOCK`, `NOT_PENDING`, `CONCURRENT_MODIFICATION`, `UNKNOWN_STRATEGY`, `INTERNAL_ERROR`.

---

## F. Frontend (Angular 19, ADR-8)

### F.1 Stack
- Angular 19: standalone by default, `input()`/`output()`/`model()` (stable in 19), `@if`/`@for`, `OnPush`.
- `provideHttpClient(withInterceptors([traceInterceptor, errorInterceptor]))`.
- Angular Material 19 with the `mat.theme()` M3 mixin (density −2), restyled through `--sp-*` tokens.
- No NgRx. Not used: zoneless and `resource()`, because both are still experimental in 19.
- Tooling: Node 22 in Docker (`frontend/Dockerfile.dev` from `node:22-alpine` + Chromium).

### F.2 Structure
```
src/app/
  core/      api/ (typed clients from the contract), interceptors, global-error-handler, polling.service
  shared/    trigger-badge, source-chip, confidence-bar, money/number pipes, empty/error states
  features/
    console/   review-queue page: pending pricing + reorder cards grouped by product
    products/  catalog table: stock, price, velocity, status, "Sell 1", stock edit, suggest buttons
  app.routes.ts   '' → console, 'products' → catalog
```
Stores: `ProductsStore`, `SuggestionsStore`. These use `signal`/`computed` fed by `PollingService` (`timer(0,5000)` + `switchMap`, paused when the tab is hidden, exponential backoff on error).

### F.3 Screens (minimalist, see ui-theme.md)
- **Review queue**: one row per product with pending items. Each row shows a product line (SKU · name · stock/threshold · velocity) and up to two compact cards. Pricing cards show `$44.99 → $49.49 ▲10%`; reorder cards show `+57 units · 5 d`. Each card has a confidence bar, a trigger badge, a source chip (AI / Rule / Fallback + reason tooltip), reasoning (2 lines, expandable), and Accept / Reject.
- **Catalog**: a dense table with category and status filters, plus inline "Sell 1" and "Set stock" actions. Shows "Last updated Xs ago".
- **Header**: strategy toggle (Rule / AI) for pricing and reorder, which calls `/admin/strategies`.
- Errors: inline message + "Error ref: abcd1234" (`traceId` prefix). A `GlobalErrorHandler` toast prevents blank screens.

### F.5 Cut order if late
1. SSE stream (FE-08 / BE-11)
2. Price history chart
3. Category filters
4. Strategy toggle UI (keep the endpoint)

Never cut: the review queue, badges, accept/reject, simulate sale.

---

## G. Tasks

Size: S ≤ 30 min, M ≤ 1 h, L ≤ 2 h. **Bold = demo floor.**

### Senior Engineer
| Id | Task | Size |
|---|---|---|
| **SE-00** | Create `docs/flows/{README,backend,agentic-loop,llm,frontend,observability,qa}` skeletons; `.claude/agents/*` | S |
| SE-01 | ADRs 1–10 (done; ADR-8 revised, ADR-10 added 2026-11-06) | ✓ |
| **SE-02** | `contracts/openapi.yaml` per §E; Spectral + Redocly lint green; freeze | M |
| SE-03 | docker-compose, `.env.example` (done; Node 22 for Angular) | ✓ |
| SE-04 | ArchUnit layer rules | S |
| SE-05 | Sign off the six prompt templates (append to ADR-4) | S |

### Backend
| Id | Task | Size |
|---|---|---|
| **BE-01** | Spring Boot 3 scaffold (Java 21, web, jpa, validation, flyway, actuator, amqp, resilience4j), profiles `local`/`postgres`, Spotless, JaCoCo, `Dockerfile` | M |
| **BE-02** | Migrations V1–V6, entities, repositories, `ProductStatusPolicy` | M |
| **BE-03** | Error shape (`ApiErrorResponseFactory`, handler, `/error`), `traceId` filter, JSON-Lines logging to `backend/logs/` | M |
| **BE-04** | Catalog + inventory endpoints (create, list, get, stock PATCH, orders) with outbox write | M |
| **BE-05** | Strategy contracts, rule pricing/reorder, `StrategyRegistry`, `RuntimeSettings`, admin endpoint | M |
| **BE-06** | `RecommendationService` (3-phase, parallel, dedupe), suggest endpoints, list endpoints | M |
| **BE-07** | Approval: accept/reject, side effects, SUPERSEDED, optimistic locking | M |
| **BE-08** | Agentic loop, `in-process` mode: `TriggerEvaluator`, AFTER_COMMIT handler. **Demo works on H2 here** | M |
| BE-09 | `LLMGateway` (groq/gemini/ollama), six prompts, parser, `AdviceValidator`, `Fallback*Strategy` | L |
| BE-10 | Agentic loop, `rabbitmq` mode: relay + confirms, sweep, listener, `processed_messages`, retry/DLQ | L |
| BE-12 | Resilience4j breaker, budget, retry | S |
| BE-11 | *(bonus)* SSE reasoning stream | M |

### Frontend
| Id | Task | Size |
|---|---|---|
| **FE-01** | Angular 19 scaffold in Docker (Node 22), Material, tokens, `Dockerfile.dev`, proxy confs, lint, Karma ChromeHeadlessCI | M |
| **FE-02** | Core: typed API client, interceptors, `GlobalErrorHandler`, `PollingService` | M |
| **FE-03** | Shared components: trigger badge, source chip, confidence bar, empty/error/loading | S |
| **FE-04** | Catalog page with Sell 1 / Set stock / Suggest | M |
| **FE-05** | Review queue with accept/reject and optimistic disable | M |
| FE-06 | Strategy toggle | S |
| FE-07 | *(ceiling)* price history sparkline, filters, margin column | M |
| FE-08 | *(bonus)* SSE reasoning panel | M |

### QA
| Id | Task | Size |
|---|---|---|
| **QA-01** | Unit: status policy, rule strategies, trigger evaluator (peer average!), validator | M |
| **QA-02** | API slice/integration: every endpoint + error codes, accept atomicity | M |
| QA-03 | LLM failure injection via WireMock (`qa/llm-simulator`): timeout, 429, garbage, `$0`, `$999,999`, HOLD-with-change → every case falls back, nothing dropped | M |
| QA-04 | Concurrency: 20 parallel orders → exactly one PENDING pair per trigger; Testcontainers RabbitMQ redelivery/DLQ | M |
| QA-05 | Playwright E2E: the two demo paths | M |
| QA-06 | k6 smoke: order p95 < 150 ms | S |

### Sequence and critical path
```
SE-00 → SE-02 ─┬─ BE-01 → BE-02 → BE-03 → BE-04 → BE-05 → BE-06 → BE-07 → BE-08  ◀ demo floor on H2
               │                                              └→ BE-09 → BE-12 → BE-10
               └─ FE-01 → FE-02 → FE-03 → FE-04 → FE-05 (against the Prism mock until BE-07)
QA-01/02 alongside each BE task · QA-03 after BE-09 · QA-04 after BE-10 · QA-05 last
```
**Rule:** reach the end-to-end demo on H2 + rule strategy (BE-08 + FE-05) **before** starting the AI (BE-09) and RabbitMQ (BE-10). Each later step is then additive, and the demo is never broken.

---

## H. Workflow and review
`principal-architect` plans → `backend-engineer` / `angular-architect` implement, test, and update the flow doc → `code-reviewer` approves or requests changes → `qa-engineer` verifies and updates the Verification section. No self-merge. One branch per task (`task/BE-04-inventory-endpoints`), squash-merge to `main`.

Definition of done: tests green (real results), `./mvnw verify` passes (Spotless, JaCoCo ≥ 80% on `recommendation` and `approval`), flow doc updated, contract unchanged or change approved.

---

## Testing summary
| Level | Tools | Focus |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | policy, rules, evaluator, validator, parser |
| Slice | `@WebMvcTest`, `@DataJpaTest` | contract shape, error codes |
| Integration | `@SpringBootTest`, Testcontainers (Postgres, RabbitMQ), WireMock | loop, fallback, dedupe, DLQ |
| Frontend | Karma/Jasmine (ChromeHeadless) | stores, polling, components |
| E2E | Playwright | demo paths |
| Perf | k6 | order p95 |

---

## Demo script (5 min)
1. Console is empty; catalog shows PRD-003 (stock 8 / threshold 15).
2. **Inventory low:** click "Sell 1" on PRD-003 → within ≤ 5 s two cards appear with the `INVENTORY_LOW` badge, AI reasoning and confidence.
3. Accept pricing → the price changes in the catalog; the reorder card stays. Accept reorder → stock +N, status returns to ACTIVE.
4. **Demand spike:** sell on PRD-008 until velocity > 21 → `DEMAND_SPIKE` cards with different reasoning.
5. **Resilience:** switch `LLM_BASE_URL` to the simulator's garbage stub (or kill the network) → the card shows "Rule fallback · INVALID_JSON". Nothing is lost.
6. Show the RabbitMQ UI queue, an ADR, and the `CompetitorAwareStrategy` seam.

---

## Risks
| # | Risk | Mitigation |
|---|---|---|
| R1 | LLM returns malformed or absurd output | Validator + fallback (ADR-4) |
| R2 | Free-tier rate limits / quota during the demo | Budget 30/min, dedupe before the call, Ollama as backup, rule fallback |
| R3 | Scope: outbox + RabbitMQ + resilience is large for the time box | Build the H2/in-process floor first (§G rule); RabbitMQ is additive |
| R4 | Angular 19 tooling on host Node 26 | Run in Docker on Node 22 (or `nvm use 22`) |
| R5 | Spike never fires because the average includes the product | Peer average excluding self (§C.5) + unit test |
| R6 | Race between accept and a new trigger | Optimistic locking (`version`) + `pending_key` uniqueness |

---

## I. Production readiness (post-MVP)
OAuth2 resource server (roles `MERCHANDISER`, `ADMIN`, `decidedBy`); secrets manager for `LLM_API_KEY`; multi-instance settings refresh; hosted frontend error tracking; metrics dashboards (fallback rate, DLQ depth, queue lag); a paid LLM tier with a data-processing agreement; nginx production image for the frontend.
