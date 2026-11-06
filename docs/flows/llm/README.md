# LLM Advisor Flow

Maintained by `backend-engineer` · Design: plan §C.6, §C.7, §C.9 · ADR-2, ADR-4, ADR-10

## Status
| Flow | Status | Task |
|---|---|---|
| Strategy contracts + rule strategies | 🟩 Implemented | BE-05 |
| Prompt selection and rendering | 🟩 Implemented | BE-09 |
| Gateway + providers | 🟩 Implemented | BE-09 |
| Parse → validate → fallback | 🟩 Implemented | BE-09 |
| Breaker, budget, retry | 🟩 Implemented (breaker + budget; retry deferred) | BE-12 |
| SSE reasoning stream *(bonus)* | 🟦 Designed | BE-11 |

## Flow: one AI pricing advice
1. `FallbackPricingStrategy.advise(ctx)` (bean `"ai"`): checks the budget (30 calls/min) and breaker state → if not allowed, rule advice with `BUDGET_EXCEEDED` / `CIRCUIT_OPEN`.
2. `AiPricingStrategy` selects the template by trigger: `prompts/pricing-{inventory-low|demand-spike|manual}.txt` and renders it with product data inside a `<<<DATA … DATA>>>` block.
3. `LLMGateway.callLLM(prompt)`: provider from `LLM_PROVIDER`; timeouts connect 2 s / read 10 s.
4. `AdviceParser`: strip code fences → first `{…}` → strict Jackson → `INVALID_JSON` on failure.
5. `AdviceValidator`: price > 0 and within 0.5×–2.0× current; direction consistent with the change; confidence ∈ [0,1]; reasoning non-blank → `OUT_OF_BOUNDS` on failure.
6. Success → `source=AI`. Any failure → `RulePricingStrategy` result with `source=RULE_FALLBACK` + `fallbackReason` + `LLM_FALLBACK` WARN log.

Reorder follows the same flow: quantity is an integer in 1..10 000, plus `leadTimeDays`.

## Rule strategies (bean `"rule"`)
- Pricing: stock < threshold → +10%; else velocity > 2× peer avg → +5%; else HOLD. Confidence 0.6, with reasoning that explains the rule.
- Reorder: `max(1, threshold × 3 − stock)`, lead time 7 days.

## Prompts: why six, not one
| Template | Situation given to the model | Decision asked for |
|---|---|---|
| pricing-inventory-low | stock vs threshold, days of cover, velocity vs peers | raise to protect scarce stock **vs** clearance **vs** hold, with an explicit trade-off |
| pricing-demand-spike | velocity × peer multiple, stock headroom, sell-out risk | modest capture increase; warn about overreacting to a short spike |
| pricing-manual | neutral snapshot | review the current price |
| reorder-inventory-low | cover target (threshold × 3), lead time | replenish to safe cover |
| reorder-demand-spike | new velocity, lead time | size to the new demand, capped |
| reorder-manual | neutral snapshot | review the reorder need |

## Providers (ADR-10, free tier)
| Provider | `LLM_BASE_URL` | Key | Use |
|---|---|---|---|
| Groq (default) | `https://api.groq.com` | free key from console.groq.com | fast demo |
| Gemini | `https://generativelanguage.googleapis.com` | free AI Studio key | alternative |
| Ollama | `http://ollama:11434` | none | offline backup |
| WireMock sim | `http://llm-sim:8080` | dummy | tests / failure injection |

## Fallback Reasons
`TIMEOUT`, `RATE_LIMITED`, `PROVIDER_ERROR`, `EMPTY_RESPONSE`, `INVALID_JSON`, `OUT_OF_BOUNDS`, `CIRCUIT_OPEN`, `BUDGET_EXCEEDED`.

## Safety and Cost
- The key comes only from `LLM_API_KEY`; it is never logged.
- Prompt injection: data is delimited and output is validated, so injected text cannot move a price outside its bounds.
- Cost: suggestions are deduplicated **before** the LLM call; there are 2 calls per trigger; the budget caps the call rate.

## Deviations from Design
- The registered `"ai"` strategies (`AiPricingStrategy`, `AiReorderStrategy`) *are* the fallback decorators around the pure `AiAdvisor`; no separate `Fallback*Strategy` class names.
- The `PriceGuardrail` chain still runs after AI advice (defence in depth), but out-of-range AI prices are already rejected by `AdviceValidator` → `OUT_OF_BOUNDS` fallback, so the guardrail never silently rewrites AI output.
- `leadTimeDays` is optional in the AI reorder JSON (the brief's schema omits it); default 7.
- New fallback reason `NOT_CONFIGURED`: AI selected but no key/model, so no network call is made.
- The one jittered retry on 429/503 (ADR-4) is **deferred**: the breaker + budget + fallback already cover the failure; a retry would double free-tier usage.
- Host runs import the repo-root `.env` (`spring.config.import`), so the key lives in one gitignored file.
- Default model `llama-3.1-8b-instant` (Groq free tier); change `LLM_MODEL` if Groq renames it.

## Verification
Implementer tests (QA-03 still to review): `AdviceParsingAndValidationTest` (19: fences, prose, invalid JSON, $0, $999,999, HOLD-with-change, wrong direction, confidence 1.7, quantity 0/-5/1.5/20000), `PromptLibraryTest` (low vs spike prompts differ; data block injection-safe), `LlmGatewayTest` (bearer key, JSON mode, 429/503/empty/timeout, budget, breaker, key masked), `AiAdvisorIT` (AI used when valid; 8 failure modes → `RULE_FALLBACK` + exact reason; loop sends the INVENTORY_LOW prompt).

## Implementation Log
| Date | Task | Change | Notes |
|---|---|---|---|
| 2026-11-06 | SE-00 | Skeleton created | |
| 2026-11-06 | BE-05 | `strategy/{PricingStrategy,ReorderStrategy,StrategyRegistry}`, `strategy/rule/*`, `guardrail/BoundsGuardrail`. Fallback reason `NOT_CONFIGURED` added for "AI selected but no key" | Registry keys come from `key()`, not bean names (two "rule" beans would clash) |
| 2026-11-06 | BE-09, BE-12 | `integration/llm/{LlmGateway,LlmProperties,CallBudget,LlmFailure}`, `recommendation/ai/{PromptLibrary,AdviceParser,AdviceValidator,AiAdvisor,AiPricingStrategy,AiReorderStrategy,AiFallbackSupport}`, `resources/prompts/*.txt` (system + 6). Metric `stockpulse.llm.fallback{reason,type}` | Retry deferred (see deviations) |
