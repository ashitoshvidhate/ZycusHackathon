# StockPulse

StockPulse is a Spring Boot and React inventory console that models products, low stock and demand spikes, and independently reviewable pricing and reorder recommendations. It is a hackathon vertical slice with production-minded validation, schema migrations, bounded LLM integration, rule fallback, async post-commit processing, and UI feedback.

## Run locally

Requirements: Java 17, Maven, Node 22 (Vite 8), MySQL 8, and an OAuth2/OIDC identity provider. Register a public SPA client for Authorization Code + PKCE, allow the callback/logout URLs, configure the listed scopes/roles, then copy `.env.example` to an ignored local env file and replace the identity-provider placeholders. Configure `OAUTH_ISSUER_URI`, `OAUTH_JWK_SET_URI`, and `OAUTH_AUDIENCE` for the backend. For direct local runs, export those variables in the terminal and put the `VITE_OIDC_*` values in `frontend/.env.local` (or export them before `npm run dev`). Docker Compose reads the root `.env` file and forwards public client settings as build args. Do not add a client secret to the browser app.

```sql
CREATE DATABASE stockpulse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/stockpulse?serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-local-password"
cd backend
mvn spring-boot:run
```

In another terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`; API docs are served at `http://localhost:8080/swagger-ui.html`. Flyway creates the schema and seeds eight products. The included `.env.example` lists configuration names; use real values only in local environment variables or a secret manager.

## Demo walkthrough

1. Open PRD-003 and simulate an order to observe low-stock signal processing.
2. Request pricing and reorder suggestions manually to compare the two separate cards.
3. Accept the pricing suggestion and verify only the product price changes.
4. Accept or reject the reorder suggestion separately and verify stock changes only on acceptance.
5. Use PRD-008 to exercise the demand-spike scenario.

## Recommendation and AI settings

Rules are deterministic and are the safe baseline: low inventory raises the suggested price by 10%; demand above 2x the category average raises it by 5%; otherwise pricing holds. Reorder quantity targets three times the threshold minus current stock, with a minimum of one. Adjust `DEMAND_SPIKE_MULTIPLIER` to tune spike detection.

The strategy can be changed while the process runs:

```http
PUT /api/v1/strategy
Content-Type: application/json

{"name":"LITELLM"}
```

`RULE_BASED` and `LITELLM` are accepted. Configure `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`, and `LLM_PRODUCT_HEADER` on the backend. An optional `LLM_COOKIE` is forwarded only from the backend. Calls use the OpenAI-compatible `/chat/completions` endpoint with bounded timeouts, up to three attempts, a 10-request/minute per-instance limiter, a 30-second circuit breaker, strict JSON/value validation, and rule-based fallback. Never put gateway credentials in a `VITE_*` variable or commit them. The LLM does not make inventory or price changes; a person must review each saved suggestion. Recommendation jobs persist their status, retry up to three times, and recover unfinished work on restart.

Resilience4j applies up to three attempts with a 250ms delay, limits gateway calls to 10 per minute per backend instance, and opens the circuit after repeated failures. Open-circuit and rate-limited calls skip retries and immediately use the same rule fallback. Calls have connect/read deadlines; recommendation jobs have persistent status, up to three async attempts, and restart recovery.

## API and error handling

- `POST /api/v1/products`, `GET /api/v1/products?category=...&status=...`, `GET /api/v1/products/{id}`
- `PATCH /api/v1/products/{id}/stock`, `POST /api/v1/products/{id}/orders`
- `POST /api/v1/products/{id}/suggest-pricing`, `POST /api/v1/products/{id}/suggest-reorder`
- `GET /api/v1/products/{id}/suggestions`
- `PATCH /api/v1/pricing-suggestions/{id}`, `PATCH /api/v1/reorder-suggestions/{id}` with `{"decision":"ACCEPT"}` or `{"decision":"REJECT"}`
- `GET/PUT /api/v1/strategy`

Errors include a stable `code`, readable `message`, and `requestId`. Backend logs include the code and request reference without logging request bodies, bearer credentials, cookies, or model content. The browser logs request status/path/code only and displays the readable message.

All application API routes require an OAuth2 JWT bearer token. The resource server validates JWT signature via the configured JWK Set, issuer, timestamps, and `stockpulse-api` audience. Required scopes are `stockpulse.read`, `inventory.write`, `suggestions.write`, `suggestions.review`, and `strategy.admin`; mapped role claims include `MERCHANDISER` and `ADMIN`. The React client uses `oidc-client-ts` with Authorization Code + PKCE and session storage. CI tests access control using test principals; an actual login requires your configured identity provider.

## Verification

```powershell
cd backend
mvn test
cd ../frontend
npm ci
npm run lint
npm test
npm run build
```

GitHub Actions runs frontend lint/tests/build, backend integration tests against H2 and MySQL 8.4, secret scanning, and Docker Compose validation/image builds.

## Current deployment gaps

This repo is a single Spring Boot service rather than a deployed multi-service system. Signal processing uses a database-backed recommendation job written in the inventory transaction, with post-commit async dispatch, bounded retries, startup recovery, and persistent `FAILED` state. Add a protected operations workflow to inspect/replay exhausted jobs before production. Kafka is available in Compose but is not connected to producers/consumers. Resilience4j circuit-breaker and rate-limiter state is per process; use a gateway/shared limiter and distributed event transport for multi-instance deployments. You must provide and configure your identity provider; TLS ingress, browser end-to-end tests, and multi-instance load/concurrency tests remain deployment work.

See [ADR.md](ADR.md) for architecture decisions and [SECURITY.md](SECURITY.md) before sharing the repository.
