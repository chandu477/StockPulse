# StockPulse: Project Rules

AI inventory and dynamic pricing engine. When stock or demand changes, the system generates AI or rule-based pricing and reorder **suggestions**. A merchandiser approves them before anything changes.

## Stack
- Backend: Java 21, Spring Boot 3.x (MVC, JPA, Flyway), PostgreSQL 16 / H2 (`local` profile). Lives in `/backend`.
- Frontend: Angular 17 (standalone, signals + RxJS, Angular Material). Lives in `/frontend`.
- LLM: Gemini / Groq / Ollama through `LLMGateway`. The key comes from the `LLM_API_KEY` environment variable only.

## Where things are
| Path | What |
|---|---|
| `docs/architecture/stockpulse-delivery-plan.md` | Requirements, architecture, contracts, tasks (SE/BE/FE/QA-xx), testing, readiness. **Source of truth.** |
| `docs/flows/` | How each part works (backend, agentic-loop, llm, frontend, qa). **Must be kept in sync with the code.** |
| `contracts/openapi.yaml` | Frozen API contract. Changes need Senior Engineer approval. *(task SE-02)* |
| `ADR.md` | Architecture decisions. *(task SE-01)* |
| `docs/design/ui-theme.md` | Frontend visual design (clean SaaS tokens, colours, layout). Components use `--sp-*` tokens only |
| `.claude/agents/` | Team agents: `principal-architect`, `backend-engineer`, `angular-architect`, `qa-engineer`, `code-reviewer` |

## Non-negotiable rules
1. Prices change **only** by accepting a PricingSuggestion. Product status is derived, never set by clients.
2. Never call the LLM inside a database transaction. AI failure → rule fallback with a recorded `fallbackReason`. Never drop a suggestion silently.
2a. The agentic loop is **transactional outbox → RabbitMQ** (ADR-5). Write the `outbox_events` row in the same transaction as the stock change, publish only after commit (publisher confirms), and keep the consumer idempotent (`processed_messages` + `pending_key`), with retries → `recommendation.dlq`. No `@Async` pools for the loop. The `in-process` messaging mode is for the local H2 run only.
3. Build to the contract. Don't change it or invent fields. Report gaps instead.
4. Never commit secrets (`.env`, keys). Never edit an applied Flyway migration.
5. Never weaken or delete tests to get a green build. Report real test results only.
6. After each task, update the relevant `docs/flows/*` document (status, file paths, implementation log).
7. Every error response uses the **one standard shape**: `{timestamp, status, error, code, message, path, traceId, errors[]}`. Never a different format, and never a stack trace. Spec: `docs/flows/observability/README.md` §1.
8. The backend logs everything to files in `backend/logs/` (backend, failures, access, audit). Use JSON Lines with `traceId`, use catalogue event names, and never log secrets. `logs/*.log` is never committed. The frontend has **no log file** in the MVP: it shows "Error ref" (the `traceId`) and uses a `GlobalErrorHandler`. Hosted error tracking is a production item.
9. No self-merge: every change goes through `code-reviewer` / Senior Engineer review (plan §H).

## Workflow per task
`principal-architect` (plans and assigns) → `backend-engineer` / `angular-architect` (implement + test + update flow doc) → `code-reviewer` (verify, approve or request changes) → `qa-engineer` (verify flows, update the Verification sections).

## Commands
First time: `cp .env.example .env` and set `POSTGRES_PASSWORD` and `RABBITMQ_PASSWORD` for Docker (never commit `.env`).

**Docker (preferred; the host can keep Node 24, Angular tooling runs on Node 20 in containers)**
- Backend + PostgreSQL 16 + RabbitMQ: `docker compose up -d --build` → `curl localhost:8080/actuator/health` · RabbitMQ UI: http://localhost:15672 (credentials from `.env`) · stop: `docker compose down` (keeps volumes).
- Frontend dev server (after FE-01): `docker compose --profile frontend up` → http://localhost:4200
- Local LLM: `docker compose --profile ai-local up -d`, then `docker compose exec ollama ollama pull llama3.1:8b` (set `LLM_BASE_URL=http://ollama:11434`)
- LLM simulator (QA): `docker compose --profile llm-sim up -d` (WireMock on :8089, stubs in `qa/llm-simulator/mappings`)
- Angular CLI: `docker compose run --rm ng <cmd>` (FE-01 scaffold: `docker compose run --rm ng new stockpulse-frontend --directory . --standalone --routing --style=scss --skip-git`)
- Logs: `backend/logs/` on the host (`stockpulse-backend.log`, `-failures.log`, `stockpulse-access.log`, `stockpulse-audit.log`); `docker compose logs -f backend` for the console

**Backend on the host (alternative; Java 21, no Maven install needed)**
- Build + all gates (tests, JaCoCo, Spotless): `cd backend && ./mvnw verify` (Windows cmd: `mvnw.cmd verify`)
- Format: `./mvnw spotless:apply` · package only: `./mvnw -DskipTests package`
- Run (H2, zero setup; `local` is the default profile): `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- Run against PostgreSQL: `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres` (DB_URL / DB_USERNAME / DB_PASSWORD from the environment)

**Contract**
- Lint: `npx --yes @stoplight/spectral-cli@6 lint contracts/openapi.yaml` and `npx --yes @redocly/cli@1 lint contracts/openapi.yaml`

**Frontend / E2E (after FE-01 / QA-05)**
- Unit tests: FE-01 defines the containerised command (`node:20-alpine` has no Chrome, so Karma needs a Chrome-capable image). CI uses Node 20: `npm test -- --watch=false --browsers=ChromeHeadless`
- E2E: `cd e2e && npx playwright test`
