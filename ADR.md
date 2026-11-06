# StockPulse: Architecture Decision Records

Owner: Senior Engineer / Solution Architect · Source: [delivery plan](docs/architecture/stockpulse-delivery-plan.md) §C.10 · Format: Context → Options → Decision → Trade-offs.

All ADRs below were recorded in SE-01 on 2026-11-03. They rest on the proposed defaults for Q-1..Q-18 (plan §0.8), which are **accepted as defaults pending stakeholder confirmation**. If the product owner changes a default, the affected ADR is revised and its status updated.

| ADR | Title | Status |
|---|---|---|
| [ADR-1](#adr-1-flat-layered-monolith) | Flat layered monolith | Accepted |
| [ADR-2](#adr-2-split-pricing-and-reorder-contracts-with-two-parallel-ai-calls) | Split pricing and reorder contracts, two parallel AI calls | Accepted (Q-14 default) |
| [ADR-3](#adr-3-runtime-strategy-switching-via-a-bean-map-registry-and-a-persisted-setting) | Runtime strategy switching via a bean-map registry | Accepted (Q-11 default) |
| [ADR-4](#adr-4-llm-failure-handling-decorator-fallback-validation-timeouts-circuit-breaker-budget) | LLM failure handling | Accepted · implemented; prompts signed off (SE-05) |
| [ADR-5](#adr-5-agentic-loop-transactional-outbox--rabbitmq-level-triggered-idempotent-consumer) | Agentic loop: transactional outbox → RabbitMQ, idempotent consumer | Accepted · implemented 2026-11-06 (SKIP LOCKED deferred) |
| [ADR-6](#adr-6-extensibility-seams-and-deliberate-exclusions) | Extensibility seams and deliberate exclusions | Accepted |
| [ADR-7](#adr-7-derived-product-status-and-the-superseded-suggestion-status) | Derived product status, plus `SUPERSEDED` | Accepted · revised 2026-11-06 (one recommendation per product) |
| [ADR-8](#adr-8-angular-19-with-signals--services-no-ngrx-polling-for-refresh) | Angular 19 with signals + services, no NgRx, polling | Accepted (revised 2026-11-06) |
| [ADR-9](#adr-9-one-standard-error-response-and-backend-only-file-logging) | One standard error response; backend-only file logging | Accepted |
| [ADR-10](#adr-10-free-tier-llm-provider-groq-default-ollama-offline) | Free-tier LLM provider: Groq default, Ollama offline | Accepted (2026-11-06) |

---

## ADR-1: Flat layered monolith

**Status:** Accepted · 2026-11-03

**Context.** One small team (SE, BE, FE, QA), one database, low load, and a hackathon-sized domain (products, inventory, suggestions, approvals). The brief rewards clean separation of commerce logic and the ability to explain each step of the loop. It doesn't need independent scaling or deployment of parts of the system.

**Options.**
1. **Microservices** (catalog, inventory, recommendation, approval as separate deployables, talking over HTTP or a broker).
2. **Flat layered monolith** (one `controller` / `service` / `repository` package set for everything).
3. **Flat layered monolith**: one Spring Boot deployable with shared `controller`, `service`, and `entity` packages. Descriptive class names identify the business feature.

**Decision.** Option 3, following the team's layer-first folder convention. ArchUnit tests (SE-04) enforce these rules:
- Controllers do not depend on messaging or AI-provider infrastructure.
- Entities do not depend on controllers, services, messaging, or integrations.
- Only recommendation AI code may import `integration.llm`.
- Commerce logic lives in strategies and `RecommendationService`, never in controllers or entities. HTTP and async callers share `RecommendationService` ("two callers, one interface").

**Trade-offs.**
- (+) One build, one deploy, one transaction manager: atomic accept (price update + status) is a local transaction, not a saga.
- (+) The role of each file is immediately visible from its folder, matching the team's existing convention.
- (−) No independent scaling. Acceptable at this load (§A success metrics).
- (−) Cross-module async work needs a durable hand-off; solved by the transactional outbox + RabbitMQ (ADR-5), not by in-process events.
- (−) Feature boundaries are no longer encoded in package names; descriptive class names and review discipline carry more weight. ArchUnit enforces layer boundaries, not feature isolation.

---

## ADR-2: Split pricing and reorder contracts with two parallel AI calls

**Status:** Accepted (default for Q-14, pending stakeholder confirmation) · 2026-11-03

**Context.** Every trigger produces both a price recommendation and a reorder quantity. The brief leaves "one unified AI call vs separate calls" to the ADR. The two decisions have different inputs that matter, different validation rules (price bounds and direction vs integer quantity and max), and different fallbacks.

**Options.**
1. **One unified call** returning `{price…, quantity…}` in one JSON object, behind one `CommerceStrategy` interface.
2. **Split contracts**: `PricingStrategy` and `ReorderStrategy`, each with its own advice record, prompt template, validator, and fallback; the two AI calls run in parallel (`CompletableFuture`).

**Decision.** Option 2. `PricingStrategy.advise(ctx) → PricingAdvice` and `ReorderStrategy.advise(ctx) → ReorderAdvice` (plan §C.6), with six prompt templates (2 types × 3 triggers).

**Trade-offs.**
- (+) Independent failure: a bad price does not throw away a good reorder quantity, and each fallback records its own `fallbackReason`.
- (+) Strategies can be mixed at runtime (e.g. `pricing=ai`, `reorder=rule`), which ADR-3 relies on.
- (+) Smaller, more focused prompts and simpler validators.
- (−) Two LLM calls per trigger, roughly double the token cost. Acceptable because suggestions are event-driven and de-duplicated before the call (ADR-5), not per page view, and capped by the budget (ADR-4).
- (≈) Latency is about one call, because the calls run in parallel.


**Implementation (2026-11-06).** `recommendation/strategy/{PricingStrategy,ReorderStrategy}` with `PricingAdvice` / `ReorderAdvice` records; `RecommendationService` runs both on a bounded `strategyExecutor` in parallel (`CompletableFuture`), outside any transaction. With Groq `gpt-oss-20b` one call takes about 0.8 s, so a trigger produces both suggestions in about 2 s end to end.


**Note (2026-11-06).** The contracts stay split (independent validation and fallback per type). The *presentation* is unified: the console groups the product's pending price and restock suggestion into one recommendation card (ADR-7 revision).

---

## ADR-3: Runtime strategy switching via a bean-map registry and a persisted setting

**Status:** Accepted (default for Q-11, pending stakeholder confirmation) · 2026-11-03

**Context.** FR-5: strategies (rule ↔ AI) must be switchable at runtime with no restart, for both HTTP and async callers. "Via config at runtime" is not defined in the brief. The switch also serves as the production kill switch for AI.

**Options.**
1. `@ConditionalOnProperty`: chooses the bean at startup. Needs a restart, so it fails FR-5.
2. `@RefreshScope` + Spring Cloud Config / actuator refresh: adds Spring Cloud, a config server, and refresh semantics that rebuild beans mid-flight.
3. **Bean-map registry + persisted setting + admin endpoint**: Spring injects `Map<String, PricingStrategy>` / `Map<String, ReorderStrategy>` keyed by bean name; `StrategyRegistry` resolves the active key from `RuntimeSettings` (an `AtomicReference` backed by the `app_settings` table; defaults from `stockpulse.strategy.default-*`). `GET/PUT /api/v1/admin/strategies`.

**Decision.** Option 3. Unknown keys return `400 UNKNOWN_STRATEGY` listing the valid keys. Both `RecommendationService` callers (HTTP and async) resolve the strategy through the registry on every call.

**Trade-offs.**
- (+) No extra infrastructure; takes effect on the next request; survives restarts (persisted).
- (+) Adding a strategy is just a new `@Component("key")` bean (ADR-6).
- (−) In a multi-instance deployment, other instances only see the change after they re-read `app_settings`. Single instance in the MVP; a short cache TTL or a change event is the production follow-up.
- (−) The admin endpoint must be protected in production (`ADMIN` role, Q-13).


**Implementation (2026-11-06).** `recommendation/strategy/StrategyRegistry` indexes beans by their `key()` method rather than by bean name: the pricing and reorder rule strategies are both `"rule"`, and two beans with the same name would clash. Active keys live in `app_settings` (`strategy.pricing`, `strategy.reorder`), cached in `shared/settings/RuntimeSettings`. Exposed at `GET/PUT /api/v1/admin/strategies` and as the top-bar switch in the console. Verified by `AgenticLoopIT.failingStrategyFallsBackToRulesAndIsSwitchableAtRuntime` (a test-only third strategy is registered with no other code change).

---

## ADR-4: LLM failure handling: decorator fallback, validation, timeouts, circuit breaker, budget

**Status:** Accepted · 2026-11-03 (SE-05 prompt-design sign-off to be appended here)

**Context.** LLM output can be malformed or absurd, and providers time out, rate-limit, and fail (risks R1, R2, R5). The brief requires that AI failure never breaks the request or the loop and that no suggestion is dropped silently. Rule: never call the LLM inside a database transaction.

**Options.**
1. **Try/catch inside each AI strategy**: fallback logic mixed into the AI class.
2. **Fail the request** and let the caller retry: violates "never drop a suggestion silently".
3. **Decorator fallback + layered guards**: `FallbackPricingStrategy` / `FallbackReorderStrategy` wrap the pure AI strategies and delegate to the rule strategies on any failure.

**Decision.** Option 3, with these guards (plan §C.7, §C.9):
- **Timeouts** on the `RestClient` request factory: connect 2 s, read 10 s (`llm.timeout.*`).
- **Parsing**: strip code fences and prose, take the first JSON object, strict Jackson mapping.
- **Validation** (`AdviceValidator`): price > 0 and within 0.5×–2.0× current (Q-12); direction consistent with the change (`HOLD` with a changed price → fallback); confidence in [0, 1]; quantity an integer in 1..`stockpulse.reorder.max-quantity`; reasoning non-blank.
- **Resilience4j circuit breaker** around `LLMGateway`: opens at 50% failures over 20 calls, short-circuits for 30 s.
- **Budget**: `llm.max-calls-per-minute` (30); over budget → straight to fallback.
- **Retry**: once on 429/503 with jitter, async path only; on-demand HTTP calls are not retried.
- Every fallback stamps `source=RULE_FALLBACK` and a `fallbackReason` (`TIMEOUT`, `RATE_LIMITED`, `PROVIDER_ERROR`, `EMPTY_RESPONSE`, `INVALID_JSON`, `OUT_OF_BOUNDS`, `CIRCUIT_OPEN`, `BUDGET_EXCEEDED`), logs `LLM_FALLBACK` at WARN, and increments `stockpulse.llm.fallback{reason}`.
- Prompt-injection defence: product data goes into a delimited, quoted data block; output validation makes injected text harmless to prices.

**Trade-offs.**
- (+) The AI class stays pure and testable; fallback is uniform and observable.
- (+) The worst case for a manual suggest is bounded at about 10.5 s (Q-17).
- (−) A fallback suggestion may be lower quality than a retried AI answer. Accepted: an explainable rule suggestion beats no suggestion.
- (−) Several moving parts (breaker, budget, retry) need targeted tests (QA-03).


**Implementation (2026-11-06).**
- `integration/llm/LlmGateway`: JDK `HttpClient` with connect 2 s / read 10 s; `CallBudget` (sliding minute, 30 calls); Resilience4j breaker (50% of 20 calls, open 30 s); OpenAI-compatible JSON mode for Groq/Ollama, `responseMimeType` for Gemini. Never logs the prompt or the key (`LlmProperties.toString()` masks it).
- `recommendation/ai/AiAdvisor` → `AdviceParser` (fences/prose tolerant, strict JSON) → `AdviceValidator` (bounds, direction consistency, confidence in [0,1], integer quantity 1..max, lead time 1..60, non-blank reasoning).
- `AiPricingStrategy` / `AiReorderStrategy` (key `"ai"`) are the fallback decorators: any failure → rule advice with `source=RULE_FALLBACK`, `fallbackReason`, an `LLM_FALLBACK` WARN log and the `stockpulse.llm.fallback{reason,type}` metric.
- **Added reason `NOT_CONFIGURED`**: AI selected but no key/model, so no network call is made.
- **Deviation: the single jittered retry on 429/503 is deferred.** The fallback already guarantees a suggestion, and on a free tier a retry doubles quota use exactly when the provider is throttling.
- Tests: `AdviceParsingAndValidationTest` (19 cases incl. $0, $999,999, HOLD-with-change, 1.5 units, confidence 1.7), `LlmGatewayTest` (429/503/empty/timeout/budget/breaker), `AiAdvisorIT` (8 failure modes → exact `fallbackReason`).

**SE-05 prompt-design sign-off (2026-11-06).** `src/main/resources/prompts/`: one system prompt + six templates ({pricing, reorder} × {inventory-low, demand-spike, manual}). They differ in the decision asked, not just a field:
- *pricing-inventory-low* frames the AGT-2 ambiguity explicitly (raise to protect scarce stock **vs** clearance when demand is weak vs peers **vs** hold) and gives days of cover against restock lead time.
- *pricing-demand-spike* gives the velocity multiple vs peers and the spike threshold, warns that spikes can be short-lived, asks for a modest goodwill-safe increase sized to stock headroom.
- *reorder-** size to cover target and lead-time demand; the spike variant warns about committing to a fading spike.
- Every template states the allowed price range / quantity range, so valid answers are possible and invalid ones are rejected by the validator. Product text is sanitised and fenced in a `<<<DATA … DATA>>>` block (`PromptLibraryTest` proves it cannot break out).
- Observed on real Groq output: low stock → *"protect scarce inventory by raising the price, trading off short-term revenue for stock availability"*; spike → *"Spike 3.3x peers, stock only 1 unit, so a 10% bump maximises revenue while staying within goodwill limits"*; reorder → explicit lead-time arithmetic (45 target + 91 lead-time demand − 7 on hand = 129).

---

## ADR-5: Agentic loop: transactional outbox → RabbitMQ, level-triggered, idempotent consumer

**Status:** Accepted · 2026-11-03 (revised the same day by product-owner decision: RabbitMQ + outbox replaces in-process `@Async` events) · Q-7 default (level-triggered) still pending stakeholder confirmation

**Context.** FR-7: stock and order changes must fire `INVENTORY_LOW` / `DEMAND_SPIKE` recommendations asynchronously, de-duplicate repeated triggers, never block the order request on the AI, never produce suggestions for a rolled-back change, and **never silently drop a signal**. The brief says the loop must fire because something changed, not because a timer ticked. The product owner asked for a production-ready, durable design that can run more than one backend instance.

**Options.**
1. **Scheduler polling** products every N seconds: not event-driven; violates the brief.
2. **Synchronous generation** on the order request: puts the AI on the request path (the p95 < 150 ms target fails).
3. **In-process Spring events** (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`): simple, but triggers are lost if the JVM dies after commit, and it can't scale past one instance.
4. **Publish to a broker directly after commit**: durable once published, but the "dual write" problem remains. A crash between the DB commit and the publish loses the message.
5. **Transactional outbox + RabbitMQ**: the event row is written in the *same* DB transaction as the stock change; a relay publishes it to RabbitMQ; an idempotent consumer generates the suggestions.
6. **Transactional outbox + Kafka**: durable and replayable, but log/stream semantics, partitions and consumer groups are more than a work-queue needs.

**Decision.** Option 5.
- **Write:**
  - `InventoryService` updates stock and velocity, writes the `inventory_events` row, and **inserts an `outbox_events` row** (`id` UUID, `aggregate_id` = productId, `event_type` = `INVENTORY_CHANGED`, `payload` JSON, `status` `PENDING|PUBLISHED|FAILED`, `attempts`, `created_at`, `published_at`), all in one transaction.
  - Rollback means no outbox row, which means no message.
- **Publish:** `OutboxRelay` publishes to the topic exchange `stockpulse.inventory` (routing key `inventory.changed`), with **publisher confirms** and persistent messages. It marks rows `PUBLISHED` only after the broker confirms. It is triggered two ways:
  1. **Immediately after commit** (`@TransactionalEventListener(AFTER_COMMIT)` signals the relay). This keeps the loop change-driven, as the brief requires.
  2. **A safety-net sweep every 5 s** for rows still `PENDING` (relay crash, broker down). This is recovery, not the trigger.

  Multiple instances claim rows with `SELECT … FOR UPDATE SKIP LOCKED` on Postgres.
- **Consume:**
  - `RecommendationRequestListener` (`@RabbitListener` on queue `recommendation.requests`, durable, bound to `inventory.changed`) passes each message to `TriggerEvaluator`, then to the same `RecommendationService.generate(productId, trigger)` the HTTP path uses ("two callers, one interface").
  - Concurrency is bounded (2–4 consumers, prefetch 10).
- **Retry and dead letters:**
  - Stateless retry with exponential backoff (1 s → 2 s → 4 s, 3 attempts).
  - After that, the message is rejected without requeue to `recommendation.dlx` → `recommendation.dlq`.
  - Dead-lettered messages are logged (`MESSAGE_DEAD_LETTERED`), counted, alerted on, and replayable by an admin.
  - AI failures **never** reach the DLQ, because they already fall back to rules (ADR-4). Only infrastructure or bug failures do.
- **Idempotency (at-least-once delivery):**
  - (a) `processed_messages(message_id PK, processed_at)` is written in the consumer's transaction, so a redelivered message is skipped.
  - (b) `pending_key = productId|triggerReason` stays the database-level guarantee against duplicate PENDING suggestions, under concurrency (QA-04).
- **Separate triggers, one handler** and **level-triggered** (Q-7): unchanged. Seeded products already below threshold (PRD-003, PRD-008) still work.
- **Two messaging modes behind one interface:**
  - `RecommendationTriggerPublisher` has implementations `rabbitmq` (Docker, `postgres` and `prod` profiles) and `in-process` (the zero-setup `local` H2 profile, so the README runs in under 5 minutes without a broker). The outbox is written in both modes.
  - Selected by `stockpulse.messaging.mode`. This is a deployment choice, not a runtime switch.
- **Retired:** the `@Async` recommendation executor and the `recommendation_requests` sweeper. Their job is now done by the broker, the retries, the DLQ, and the outbox sweep.

**Trade-offs.**
- (+) No lost triggers: the outbox closes the commit/publish gap, and RabbitMQ persists messages across restarts.
- (+) Retries, a DLQ, backpressure, a visible queue depth (management UI, metrics), and horizontal scaling with competing consumers.
- (+) Order requests stay fast, and rollbacks still never produce suggestions.
- (−) More infrastructure (a broker container), a new table, relay code, and more tests (Testcontainers RabbitMQ: delivery, redelivery, DLQ, broker-down).
- (−) At-least-once delivery means the consumer must be idempotent. It is, via `processed_messages` + `pending_key`.
- (−) Slightly higher latency than in-process events (one broker hop, typically milliseconds). The 5 s sweep only matters during failures.
- (−) Two messaging modes must both be tested. `in-process` exists only for the zero-setup local run.


**Implementation (2026-11-06).**
- Write: `service/InventoryService` + `service/OutboxWriter`, in one transaction.
- Relay: `messaging/RabbitOutboxRelay` (AFTER_COMMIT trigger + 5 s sweep; `CorrelationData` confirm + mandatory routing; `messageId` = outbox id, `traceId` header).
- Topology: `messaging/RabbitMessagingConfig` (`stockpulse.inventory` → `recommendation.requests`, DLX `recommendation.dlx` → `recommendation.dlq`).
- Consumer: `messaging/RabbitRecommendationListener` → `service/RecommendationRequestHandler` (shared with `InProcessOutboxDispatcher`).
- Retries: Spring AMQP stateless retry 3 attempts (1 s → 2 s → 4 s), then a `MessageRecoverer` that logs `MESSAGE_DEAD_LETTERED` and rejects without requeue.
- Duplicate PENDING prevention under concurrency: product row lock in the write phase + `pending_key` unique constraint (`AgenticLoopIT.concurrentOrdersProduceExactlyOnePendingPair`, 12 parallel orders).
- Verified on `docker compose up` (PostgreSQL 16 + RabbitMQ 3.13): order 201 in about 40 ms; AI suggestions visible about 2 s later; 11/11 outbox rows PUBLISHED; DLQ empty.
- **Deviation: `SELECT … FOR UPDATE SKIP LOCKED` row claiming for multiple relay instances is deferred** (single instance in the MVP). A double publish is harmless because the consumer is idempotent. A Testcontainers test for redelivery/DLQ/broker-down is still open (QA-04).

---

## ADR-6: Extensibility seams and deliberate exclusions

**Status:** Accepted · 2026-11-03

**Context.** The brief's Extensions tab (Sprint 2: cost price, margin floor, `CompetitorAwareStrategy`, supplier catalog, category price caps, price-change cooldown; Sprint 3: auto-apply, auto purchase orders, storefront price API) asks the design to show where future work plugs in, without building it now. Time is the scarcest resource; the floor (agentic loop, fallback, idempotency, ADR) must not be put at risk.

**Options.**
1. **Build the Sprint 2 features now** (margin guardrails, competitor strategy, supplier catalog).
2. **Ignore the extensions** and refactor later.
3. **Prepare cheap, explicit seams now and exclude the rest deliberately**, recording each exclusion as a priority decision.

**Decision.** Option 3.

*Seams built into the MVP:*
| Seam | What exists now | How Sprint 2 plugs in |
|---|---|---|
| Nullable `costPrice`, `marginFloor`, `supplierId` | Columns on `products` (`cost_price`, `margin_floor`, `supplier_id`), fields on `ProductView`; `costPrice`/`supplierId` optional on `CreateProductRequest`; `ProductResponse.marginPct` is server-computed and null when `costPrice` is null | Margin-aware pricing and supplier lookup read these fields; no migration on the hot table needed |
| `PriceGuardrail` chain | A chain of guardrails applied to every recommended price; currently only `BoundsGuardrail` (0.5×–2.0×) | Add `MarginFloorGuardrail` and `CategoryCapGuardrail` as new chain members; strategies are untouched |
| Strategy registration via `@Component` key | `StrategyRegistry` built from `Map<String, PricingStrategy>` / `Map<String, ReorderStrategy>` (ADR-3) | `CompetitorAwareStrategy implements PricingStrategy` + `@Component("competitor")` is the whole change; it immediately appears in `GET /admin/strategies` |

*Deliberate exclusions (each a priority decision, not an oversight):*
| Excluded | Why not now | Path when needed |
|---|---|---|
| Authentication / roles in the demo | Local-only demo; auth adds setup with no demo value (Q-13) | Production profile: OAuth2 Resource Server (JWT), roles `MERCHANDISER` / `ADMIN`, `decidedBy` on decisions (plan §I) |
| Kafka / event replay and streaming analytics | RabbitMQ + outbox (ADR-5) already gives durable, retried, scalable work-queue delivery; replaying history isn't an MVP need | Add a Kafka publisher for analytics streams if needed. The outbox stays the single source |
| Rolling 24 h velocity window | MVP increments the stored velocity by order quantity (Q-2) | Recompute from `inventory_events` (Sprint 2) |
| SSE reasoning stream | Bonus and first to cut (brief, plan §F.5); polling covers the floor | BE-11 / FE-08 if the floor is complete |
| Auto-apply prices, auto purchase orders, storefront price API, price-change cooldown, competitor scraping | Out of MVP scope (plan §0.7) | Sprint 2/3 backlog |

**Trade-offs.**
- (+) Sprint 2 work becomes additive (new beans, new guardrails), not a rewrite.
- (+) The cost now is tiny: three nullable columns, one interface with one implementation, and a registry we need anyway.
- (−) The `PriceGuardrail` interface has one implementation in the MVP. Justified only because it is a declared extension seam.
- (−) Each exclusion is a known limitation to explain in the walkthrough (especially no auth in the demo).


**Where the seams are in code (2026-11-06).**
| Sprint 2 item | Plug-in point | Change needed |
|---|---|---|
| `CompetitorAwareStrategy` | `recommendation/strategy/PricingStrategy` | New `@Component` class with `key() = "competitor"`; it appears in `GET /admin/strategies` and the UI switch |
| Margin floor | `recommendation/guardrail/PriceGuardrail` chain (today `BoundsGuardrail`) + `Product.marginFloor` / `costPrice` (`V1__catalog.sql`) | New `MarginFloorGuardrail` bean; no strategy changes |
| Category price caps | Same `PriceGuardrail` chain | New `CategoryCapGuardrail` bean |
| Supplier catalog | `Product.supplierId`, `ReorderAdvice` | Reorder strategy reads the supplier; add a `supplierId` field to the reorder contract (contract change) |
| Rolling 24 h velocity | `inventory_events` table (every ORDER/ADJUST/INBOUND recorded) | Recompute velocity from events instead of the counter |
| Price-change cooldown | `RecommendationService` phase 1 | Check the last ACCEPTED pricing suggestion's `decidedAt` |

---

## ADR-7: Derived product status, and the `SUPERSEDED` suggestion status

**Status:** Accepted (defaults for Q-8 and Q-9, pending stakeholder confirmation) · 2026-11-03

**Context.** The brief defines product states (`ACTIVE`, `PRICE_REVIEW_PENDING`, `OUT_OF_STOCK`) but leaves open what status a product has after an accept (AGT-4). Separately, once a pricing suggestion is accepted, any other PENDING pricing suggestions for that product were computed against the old price and are stale.

**Options.**
1. **Stored status set by each operation** (and by clients): easy to drift out of sync with stock and pending suggestions.
2. **Derived status**, recomputed after every write by `ProductStatusPolicy.derive(stock, hasPending)`.
3. For stale suggestions: (a) leave them PENDING and flag them as stale in the UI; (b) add a terminal `SUPERSEDED` status set automatically.

**Decision.** Option 2 + option 3(b).
- Precedence: stock = 0 → `OUT_OF_STOCK`; any PENDING suggestion → `PRICE_REVIEW_PENDING`; otherwise `ACTIVE`. Clients never set status (BR-5).
- When a suggestion is accepted, the remaining PENDING suggestions **of the same type** for the same product become `SUPERSEDED` in the same transaction. `SUPERSEDED` is included in the contract-v1 status enum.

**Trade-offs.**
- (+) Status can't drift; one policy is unit-tested for every transition.
- (+) No stale suggestions can be accepted against an outdated price; a second accept gets `409 NOT_PENDING` instead of silently overwriting.
- (−) `SUPERSEDED` is a state that isn't in the brief. If the stakeholder declines Q-9, the fallback is to leave siblings PENDING with a UI "stale" flag, and this ADR is revised.
- (−) Status is recomputed on every write (a cheap pending-count query).


**Revision (2026-11-06, product-owner decision): one recommendation per product.**
- *Context:* the console showed several cards for one product (separate price and restock cards, and a second pair when a different trigger fired). The merchandiser wants one recommendation per product, recalculated when the situation changes.
- *Decision:*
  - At most **one PENDING pricing and one PENDING reorder suggestion per product**: `pending_key` is now the product id (`V8__one_pending_per_product.sql` superseded older duplicates).
  - The **same trigger again is de-duplicated**, so there is no extra LLM call per sale.
  - A **different trigger**, or an explicit **Recalculate** (`POST /suggest-pricing` / `/suggest-reorder`, now always `201`), generates fresh advice against current stock and price and marks the previous one `SUPERSEDED` ("Superseded by a recalculated recommendation").
  - When both triggers fire on one event, **DEMAND_SPIKE wins**, because its prompt also weighs stock headroom (`RecommendationRequestHandler.primary`).
  - The UI shows price + restock as **one card** with per-line and **Accept all / Reject all** actions.
- *Trade-offs:*
  - (+) One clear decision per product, and a pending suggestion never reflects stale numbers after the trigger changes.
  - (−) A recalculation discards the previous advice: it stays visible as `SUPERSEDED` in history, but cannot be accepted.
  - (−) Accept all is two API calls (price, then restock), not one transaction. If the second fails, the first stays accepted and the card shows what is left; an atomic endpoint would be a contract change.
- Tests: `AgenticLoopIT.recalculateReplacesThePendingSuggestion`, `aDifferentTriggerRecalculatesIntoOneRecommendation`, `concurrentOrdersProduceExactlyOnePendingPair`; `PrimaryTriggerTest`.

---

## ADR-8: Angular 19 with signals + services, no NgRx; polling for refresh

**Status:** Accepted · 2026-11-03 · **Revised 2026-11-06:** Angular 17 → 19 (product-owner decision)

**Context.** The console has two pages and a small state (products, pending pricing, pending reorder, loading, error, last updated). New suggestions arrive asynchronously, within seconds of an order. The brief names "React 18 or Angular 17" and asks for the choice to be documented here. Angular 17 is past end of support, and its CLI does not support current Node versions. The development host runs Node 26.

**Options.**
1. **Angular 17** as named in the brief: end of support, and signal inputs are only in developer preview.
2. **Angular 19**: standalone by default, `input()`/`output()`/`model()` and `@let` stable, Material 19 `mat.theme()` M3 tokens; supports Node 18.19 / 20.11 / 22.
3. Angular 20+: supports newer Node, but it is a larger jump from the brief and not what the product owner asked for.
4. State: **NgRx Store**, **NgRx SignalStore**, or **signals + feature store services**. Refresh: **WebSocket/SSE push** or **polling**.

**Decision.** Angular 19 + signals + feature store services + polling.
- Tooling runs on **Node 22 in Docker** (`frontend/Dockerfile.dev` from `node:22-alpine` + Chromium), so the host Node version doesn't matter. On the host, `nvm use 22` is the alternative.
- Standalone components (the default in 19, no `standalone: true`), `OnPush` everywhere, built-in control flow with `track`, signal `input()`/`output()`.
- Not used: zoneless change detection and `resource()`/`rxResource()`, because both are still experimental in 19.
- `PollingService` uses `timer(0, 5000)` + `switchMap`, pauses when the tab is hidden, and backs off exponentially on errors.
- Visual design: minimalist `--sp-*` tokens ([docs/design/ui-theme.md](docs/design/ui-theme.md)).

**Trade-offs.**
- (+) Supported framework version; stable signal APIs make components smaller and easier to explain.
- (+) Minimal boilerplate for a small state; polling every 5 s meets the "within ~5 s" goal with no push infrastructure.
- (−) Deviates from the brief's "Angular 17"; justified here. The patterns are the same, with newer syntax.
- (−) Host Node 26 is unsupported, so the developer must use the Docker toolchain (or nvm).
- (−) Polling sends requests when nothing has changed (50 users at 5 s ≈ 10 req/s, covered by the k6 target).

---

## ADR-9: One standard error response, and backend-only file logging

**Status:** Accepted · 2026-11-03 · Spec: [docs/flows/observability/README.md](docs/flows/observability/README.md) §1–§5

**Context.** The frontend must handle errors consistently (switching on a stable code), every failure must be traceable from the UI to the logs, and no error may leak stack traces, SQL, or provider details. Spring offers several error formats by default (whitelabel page, `/error` JSON, `ProblemDetail`).

**Options.**
1. Spring defaults / RFC 9457 `ProblemDetail`.
2. **One custom `ApiErrorResponse`** for every non-2xx response, built by one factory.
3. For logging: (a) a frontend log file via a client-log endpoint; (b) **backend-only file logs**, with the UI showing the `traceId` as "Error ref".

**Decision.** Option 2 + 3(b).
- Every non-2xx response is `ApiErrorResponse {timestamp, status, error, code, message, path, traceId, errors[{field, message, rejectedValue}]}`, with `code` from the fixed catalogue (observability §1). One `ApiErrorResponseFactory` is used by `GlobalExceptionHandler`, `ApiErrorController` (filters and `/error`), and the security handlers. `server.error.whitelabel.enabled=false`, `include-stacktrace=never`, `include-message=never`; `ProblemDetail` is disabled.
- `traceId` is in the body, the `X-Trace-Id` header, and every log line.
- The backend writes four JSON Lines files to `backend/logs/` (`stockpulse-backend.log`, `stockpulse-backend-failures.log`, `stockpulse-access.log`, `stockpulse-audit.log`) using Spring Boot structured logging (ECS), with the event catalogue (observability §4).
- **No frontend log file and no client-log endpoint in the MVP.** The UI shows the message plus "Error ref: {traceId prefix}"; `GlobalErrorHandler` prevents blank screens. Hosted frontend error tracking (e.g. Sentry) is a production-readiness item.

**Trade-offs.**
- (+) One shape for the frontend to handle; one id to grep; no information leakage.
- (+) No unauthenticated write-to-disk endpoint.
- (−) A custom format instead of the RFC 9457 standard; tools that expect `ProblemDetail` need a mapping.
- (−) Browser-only failures (JS crashes, backend unreachable) are not recorded centrally in the MVP; they are visible only in DevTools until hosted error tracking is added.

---

## ADR-10: Free-tier LLM provider: Groq default, Ollama offline

**Status:** Accepted · 2026-11-06

**Context.** The early stage must cost nothing. `LLMGateway` (Addendum B) supports Gemini, Groq and Ollama. The demo needs low latency (the ≤ 5 s loop goal) and must survive rate limits. The key comes only from `LLM_API_KEY`.

**Options.**
1. **Groq free tier** (OpenAI-compatible, Llama 3.x): very fast, no card required, but limited requests and tokens per minute and per day.
2. **Gemini free tier** (Flash models via AI Studio key): generous, native JSON mode, but free-tier prompts may be used by Google to improve products, and it has its own rate limits.
3. **Ollama local** (e.g. `llama3.1:8b`): free and offline with no limits, but slower on a laptop and a ~5 GB model download.

**Decision.** Default **Groq** (`LLM_PROVIDER=groq`, `LLM_BASE_URL=https://api.groq.com`, a current Llama 3.x "instant" model chosen at setup time). **Ollama** is the offline/backup option, **Gemini** stays an alternative behind the same gateway, and **WireMock** (`llm-sim`) is used for tests. The default strategy stays `rule`, so the app runs with no key at all.

**Trade-offs.**
- (+) Zero cost; fast responses keep manual suggests well under the 10.5 s bound.
- (+) Switching provider is configuration only (`LLM_PROVIDER`, `LLM_MODEL`, `LLM_BASE_URL`).
- (−) Free-tier quotas can be exhausted mid-demo. Mitigated by the 30 calls/min budget, dedupe before the call, and rule fallback with `RATE_LIMITED` / `BUDGET_EXCEEDED` (ADR-4).
- (−) Model names and limits change. They are not hard-coded; `.env.example` holds placeholders.
- (−) A free tier is not suitable for production data. Production uses a paid tier with a data-processing agreement (plan §I).

**Update (2026-11-06).** Groq retired the Llama 3.1 instant model on this account (the call returned `model_not_found`). The default is now **`openai/gpt-oss-20b`** (about 0.8 s, valid JSON mode); `openai/gpt-oss-120b` is the higher-quality option. This proves the configuration-only switch works: no code changed. Host runs read the repo-root `.env` via `spring.config.import`, so the key lives in one gitignored file; Docker passes it as an environment variable.
