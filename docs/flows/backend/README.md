# Backend Flows

Maintained by `backend-engineer` · Design: plan §C, §D, §E · ADR-1, ADR-3, ADR-7, ADR-9

## Status
| Flow | Status | Task |
|---|---|---|
| Flow 1: Create and list products | 🟩 Implemented | BE-02, BE-04 |
| Flow 2: Simulate a sale | 🟩 Implemented | BE-04 |
| Flow 3: Update stock / on-demand suggestion | 🟩 Implemented | BE-04, BE-06 |
| Flow 4: Accept or reject a suggestion | 🟩 Implemented | BE-07 |
| Flow 5: Switch strategy at runtime | 🟩 Implemented | BE-05 |

## Main Packages
| Package | Responsibility |
|---|---|
| `com.stockpulse.controller` | Product, inventory, recommendation, approval, and shared error endpoints |
| `com.stockpulse.service` | Business services, repositories, outbox writer, and recommendation handler |
| `com.stockpulse.entity` | Product, inventory, suggestion, outbox, settings, and message records |
| `com.stockpulse.recommendation` | Strategy contracts, rule strategies, AI strategies, prompts, parser, and validator |
| `com.stockpulse.integration.llm` | `LLMGateway` |
| `com.stockpulse.messaging` | Outbox relay, RabbitMQ listener, and in-process delivery |
| `com.stockpulse.shared` | Error models, trace filter, and logging |

---

## Flow 1: Create and list products
`POST /api/v1/products` → validate (`sku` unique, `currentPrice > 0`, `stockLevel ≥ 0`) → insert with status derived → `201` + `Location`. Duplicate SKU → `409 DUPLICATE_SKU`.
`GET /api/v1/products?status=&category=` → list with `pendingPricingCount`, `pendingReorderCount`, `marginPct` (null when `costPrice` is null).
Files: `controller/ProductController.java`, `service/ProductService.java`, `entity/Product.java`.

## Flow 2: Simulate a sale
1. `POST /api/v1/products/{id}/orders {quantity}`. `@Valid` requires `quantity ≥ 1`.
2. `InventoryService.recordOrder` (**one transaction**): lock the product (`@Version`) → if `quantity > stock` → `409 INSUFFICIENT_STOCK` → `stock -= q`, `velocity += q` (Q-2) → insert `inventory_events(ORDER)` → insert `outbox_events(INVENTORY_CHANGED)` → re-derive status.
3. Commit → `201` with the updated product. **No AI, no broker call** on this path.
4. After commit → [agentic loop](../agentic-loop/README.md).

Failure modes: unknown id → `404 PRODUCT_NOT_FOUND`; concurrent update → retry once, then `409 CONCURRENT_MODIFICATION`.
Files: `controller/InventoryController.java`, `service/InventoryService.java`, `service/OutboxWriter.java`, `entity/InventoryEvent.java`, `entity/OutboxEvent.java`.

## Flow 3: Update stock / on-demand suggestion
- `PATCH /products/{id}/stock {stockLevel}`: same transaction pattern as Flow 2 (`inventory_events(ADJUST)` + outbox); velocity unchanged.
- `POST /products/{id}/suggest-pricing` and `/suggest-reorder`: `RecommendationService.generate(id, MANUAL, type)` **synchronously** (bounded at about 10.5 s, Q-17). Returns `201` if new, or `200` + `X-Deduplicated: true` if a PENDING MANUAL suggestion already exists (Q-15).

## Flow 4: Accept or reject a suggestion
`PATCH /api/v1/pricing-suggestions/{id} {status: ACCEPTED|REJECTED, note?}`, in **one transaction**:
1. Load the suggestion; if not PENDING → `409 NOT_PENDING`.
2. ACCEPTED (pricing): `product.currentPrice = recommendedPrice`. ACCEPTED (reorder): `product.stockLevel += recommendedQuantity` + `inventory_events(INBOUND)` + outbox row (stock change can re-trigger the loop, which is correct with level triggering).
3. Mark the suggestion ACCEPTED/REJECTED, set `decidedAt`, `decisionNote`, clear `pending_key`.
4. On accept: sibling PENDING suggestions **of the same type** for the product → `SUPERSEDED` (ADR-7).
5. Re-derive product status → `200`.

The other type is untouched: accepting the price leaves the reorder card PENDING (AGT-4).
Files: `controller/DecisionController.java`, `service/ApprovalService.java`.

## Flow 5: Switch strategy at runtime
`PUT /api/v1/admin/strategies {pricing:"ai", reorder:"rule"}` → validate against registered bean keys (`400 UNKNOWN_STRATEGY` lists the valid ones) → persist in `app_settings` → update the `AtomicReference` in `RuntimeSettings`. The next HTTP or async call uses the new strategy. No restart.

## Persistence
See plan §D: migrations V1–V6. `pending_key` = `productId|TRIGGER` while PENDING, NULL otherwise (unique).

## Deviations from Design
- Seed `V6`: PRD-003 is stored ACTIVE (derived; no PENDING suggestions yet) instead of the brief's PRICE_REVIEW_PENDING (ADR-7).
- New product ids come from sequence `product_id_seq` (`PRD-100`, `PRD-101`, …). H2 runs in PostgreSQL mode so the same SQL works on both.
- `RecommendationService` phase 3 serialises writers with a product row lock (`SELECT … FOR UPDATE`) and re-checks `pending_key`; the unique constraint remains the backstop.
- Unknown JSON fields are rejected (`fail-on-unknown-properties: true`) to honour `additionalProperties: false` in the contract.

## Verification
_Filled by `qa-engineer`._

## Implementation Log
| Date | Task | Change | Notes |
|---|---|---|---|
| 2026-11-06 | SE-00 | Skeleton created | |
| 2026-11-06 | BE-01..BE-07 | Implemented. Files grouped in flat `controller`, `service`, and `entity` packages; recommendation strategies remain under `recommendation/`; migrations `V1`–`V6` | Layer folders adopted per team convention |
