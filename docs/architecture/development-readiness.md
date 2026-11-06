# StockPulse: Development Readiness Tracker

Owner: `principal-architect` · Updated by every agent at the end of each task · Plan: [§G Tasks](stockpulse-delivery-plan.md#g-tasks)

Legend: 🟦 Designed · 🟨 In progress · 🟩 Implemented · 👀 In review · ✅ Verified · ⛔ Blocked · — Not started

**Overall: 🟨 In progress (demo-ready).** Full stack on `docker compose`: PostgreSQL + RabbitMQ + Groq AI, one recommendation per product, white & blue console in ₹ for a clothing supplier. Backend 66 tests, frontend 26 tests + lint green. Open: Testcontainers RabbitMQ IT, SSE bonus, Playwright E2E. Last updated 2026-11-06.

## Senior Engineer
| Task | Description | Owner | Status | Review | Notes |
|---|---|---|---|---|---|
| SE-00 | Flow doc skeletons, team agents | principal-architect | 🟩 | — | `docs/flows/*`, `.claude/agents/*` |
| SE-01 | ADR-1..ADR-10 | principal-architect | 🟩 | — | ADR-8 revised, ADR-10 added 2026-11-06 |
| SE-02 | `contracts/openapi.yaml` + lint + freeze | principal-architect | 🟩 | — | Spectral 0 errors; Redocly valid (2 warnings: localhost server, no 4xx on GET strategies) |
| SE-03 | docker-compose, `.env.example` | principal-architect | 🟩 | — | Node 22 for Angular 19 |
| SE-04 | ArchUnit layer rules | principal-architect | 🟩 | — | `ArchitectureTest` enforces controller, entity, and AI integration boundaries |
| SE-05 | Prompt sign-off | principal-architect | 🟩 | — | Appended to ADR-4 |

## Backend
| Task | Description | Status | Flow doc | Notes |
|---|---|---|---|---|
| BE-01 | Scaffold, profiles, Spotless, JaCoCo, Dockerfile | 🟩 | backend | |
| BE-02 | Migrations V1–V6, entities, status policy | 🟩 | backend | |
| BE-03 | Error shape, traceId, JSON logging | 🟩 | observability | |
| BE-04 | Catalog + inventory endpoints + outbox write | 🟩 | backend, agentic-loop | |
| BE-05 | Strategy contracts, rule strategies, registry, admin | 🟩 | backend, llm | |
| BE-06 | RecommendationService, suggest + list endpoints | 🟩 | backend, agentic-loop | |
| BE-07 | Approval: accept/reject, SUPERSEDED | 🟩 | backend | |
| BE-08 | Agentic loop, in-process mode | 🟩 | agentic-loop | **Demo floor complete here** |
| BE-09 | LLMGateway, prompts, validator, fallback | 🟩 | llm | |
| BE-12 | Breaker, budget, retry | 🟩 | llm | |
| BE-10 | Agentic loop, RabbitMQ mode | 🟩 | agentic-loop | |
| BE-11 | *(bonus)* SSE stream | — | llm, frontend | First to cut |

## Frontend
| Task | Description | Status | Notes |
|---|---|---|---|
| FE-01 | Angular 19 scaffold (Docker, Node 22), Material, tokens | 🟩 | |
| FE-02 | API client, interceptors, GlobalErrorHandler, polling | 🟩 | Needs SE-02 |
| FE-03 | Shared components (badges, chips, states) | 🟩 | |
| FE-04 | Catalog page | 🟩 | |
| FE-05 | Review queue | 🟩 | **Demo floor complete here** |
| FE-06 | Strategy toggle | 🟩 | |
| FE-07 | *(ceiling)* history, filters, margin | 🟩 | |
| FE-08 | *(bonus)* SSE panel | — | |

## QA
| Task | Description | Status | Notes |
|---|---|---|---|
| QA-01 | Unit gaps | 🟨 | Policy, triggers, rules, guardrail unit tests written by BE |
| QA-02 | API slice/integration | 🟨 | `AgenticLoopIT` (9 tests incl. concurrency + fallback) |
| QA-03 | LLM failure injection | 🟨 | In-JVM fake LLM covers all reasons (`AiAdvisorIT`); WireMock `llm-sim` stubs still to add |
| QA-04 | Concurrency + messaging | — | After BE-10 |
| QA-05 | Playwright E2E | — | After FE-05 |
| QA-06 | k6 smoke | — | |

## Release Gates (MVP demo)
- [x] README → running app in < 5 min (H2)
- [x] Inventory-low demo path (PRD-003) works end to end
- [x] Demand-spike demo path (PRD-008) works end to end
- [x] AI failure → fallback visible in the UI; nothing dropped
- [x] `./mvnw verify` green; frontend lint/test/build green
- [ ] ADR.md final; demo video recorded
