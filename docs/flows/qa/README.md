# QA: How Each Flow Is Verified

Maintained by `qa-engineer` · Design: plan "Testing summary", §A success metrics

## Status
| Area | Status | Task |
|---|---|---|
| Unit gaps | 🟦 Designed | QA-01 |
| API slice/integration | 🟦 Designed | QA-02 |
| LLM failure injection | 🟦 Designed | QA-03 |
| Concurrency + messaging | 🟦 Designed | QA-04 |
| E2E demo paths | 🟦 Designed | QA-05 |
| Performance smoke | 🟦 Designed | QA-06 |

## Test Map (requirement → test)
| Requirement / invariant | Test (planned) | Status |
|---|---|---|
| FR-3 rule pricing, peer avg excluding self | `RulePricingStrategyTest`, `TriggerEvaluatorTest` | 🟦 |
| FR-4 rule reorder (min 1) | `RuleReorderStrategyTest` | 🟦 |
| FR-7 loop fires both suggestion types | `AgenticLoopIT` | 🟦 |
| BR-4 / I-5 no duplicate PENDING | `ConcurrentOrdersIT` (20 parallel) | 🟦 |
| BR-3 / I-4 no silent drop | `LlmFailureInjectionIT` (one case per `fallbackReason`) | 🟦 |
| I-1 price only via accept | `ApprovalIT`, `ProductApiIT` | 🟦 |
| ADR-7 SUPERSEDED | `ApprovalIT` | 🟦 |
| ADR-5 redelivery skipped, DLQ, broker down | `RabbitMessagingIT` (Testcontainers) | 🟦 |
| ADR-9 error shape on every code | `ErrorShapeIT` | 🟦 |
| Demo paths | `e2e/tests/inventory-low.spec.ts`, `demand-spike.spec.ts` | 🟦 |
| Order p95 < 150 ms | `qa/k6/orders-smoke.js` | 🟦 |

## LLM Failure Injection (WireMock stubs in `qa/llm-simulator/mappings`)
| Stub | Expected `fallbackReason` |
|---|---|
| 12 s delay | `TIMEOUT` |
| 429 | `RATE_LIMITED` |
| 500/503 | `PROVIDER_ERROR` |
| empty body | `EMPTY_RESPONSE` |
| `not json` | `INVALID_JSON` |
| price `0` / `999999` / `HOLD` with changed price | `OUT_OF_BOUNDS` |
| quantity `0` / `-5` / `1.5` | `OUT_OF_BOUNDS` |
| JSON inside ``` fences with prose | parsed OK, `source=AI` |

## How to Run
```bash
cd backend && ./mvnw verify                                          # unit + integration (Testcontainers needs Docker)
docker compose run --rm --entrypoint npm ng test -- --watch=false --browsers=ChromeHeadlessCI
docker compose --profile llm-sim up -d                               # simulator for manual failure demos
cd e2e && npx playwright test                                        # needs backend + frontend running
k6 run qa/k6/orders-smoke.js
```

## Demo Verification Script
1. Fresh start (`local` profile); the review queue is empty.
2. Sell 1 on PRD-003 → within 5 s: 1 pricing + 1 reorder card, *Low stock* badge.
3. Sell 1 again → still exactly 1 pair (dedupe).
4. Accept price → catalog price = recommended; the reorder card remains.
5. Accept reorder → stock += qty; status ACTIVE when nothing is pending.
6. Sell on PRD-008 until velocity > 21 → *Demand spike* cards with different reasoning.
7. Point the LLM at a garbage stub → the new card shows *Fallback · INVALID_JSON*.
8. `grep <Error ref> backend/logs/*.log` finds the request.

## Implementation Log
| Date | Task | Change | Notes |
|---|---|---|---|
| 2026-11-06 | SE-00 | Skeleton created | |
