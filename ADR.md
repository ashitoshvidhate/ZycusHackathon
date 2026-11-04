# Architecture Decision Records

## ADR-001: Separate approval aggregates for pricing and reorder

**Status:** Accepted.  
**Context:** Pricing and purchasing are distinct business decisions. A user accepting one must not apply the side effect of the other.  
**Decision:** Persist `PricingSuggestion` and `ReorderSuggestion` separately. The matching controllers and `RecommendationService.reviewPricing` / `reviewReorder` accept one decision at a time and update only price or stock respectively. Each review stores its authenticated reviewer and timestamp. Flyway V3 extends the existing tables.  
**Tradeoffs:** A signal creates two records and the UI makes more review requests. This gives independent status, concurrency control, and auditability.  
**Extension point:** Add new suggestion types as separate entities and service methods; do not overload an approval to mutate unrelated product state.

## ADR-002: Define strategy contracts before implementations

**Status:** Accepted.  
**Context:** Deterministic rules are required for local operation and AI output must be optional.  
**Decision:** `PricingStrategy` and `ReorderStrategy` define distinct `suggestPrice` and `suggestReorder` contracts. `RuleBasedStrategy` implements the baseline and `LiteLlmStrategy` implements the AI path. `StrategySelector` uses an atomic runtime selector; `PUT /api/v1/strategy` switches between `RULE_BASED` and `LITELLM` without restart. Both HTTP and event-driven paths call the same `RecommendationService`.  
**Tradeoffs:** Runtime selection is process-local and the HTTP mutation requires the `strategy.admin` scope or `ADMIN` role. For multi-instance production, persist shared strategy configuration and audit changes.
**Extension point:** Add a strategy bean and selector key; callers retain the same service and API contracts.

## ADR-003: Keep model calls bounded and validate all generated values

**Status:** Accepted.  
**Context:** Model output is untrusted, and the user's LiteLLM-compatible gateway can be slow, unavailable, or return malformed content.  
**Decision:** `LiteLlmGateway` calls OpenAI-compatible `/chat/completions` from the backend with connect/read timeouts, Resilience4j retry, a rate limiter, a circuit breaker, and optional gateway headers from environment configuration. `LiteLlmStrategy` uses distinct pricing and reorder prompts; it validates JSON shape, bounds, confidence, direction, positive quantity, and readable reasoning. `RecommendationService` falls back to deterministic rules and stores a safe failure marker if the AI path fails.  
**Tradeoffs:** The fallback can be less context-aware. It preserves availability and does not bypass human review. Gateway cookies and API keys must remain in backend secrets. Circuit-breaker and rate-limit counters are per process; multi-instance deployment needs a shared limiter or edge gateway.  
**Extension point:** Implement another gateway behind `LiteLlmGateway` or add a provider-specific strategy while keeping result validation and fallback behavior.

## ADR-004: Publish inventory signals after database commit

**Status:** Accepted.  
**Context:** Order and stock writes should not wait for model latency, and no recommendation may be created for a transaction that later rolls back.  
**Decision:** `RecommendationJobService.enqueue` writes `recommendation_jobs` in the same transaction as the inventory mutation and publishes a job ID. `RecommendationEventListener` receives it after commit on a bounded async executor, retries three times, and replays unfinished jobs at startup. Exhausted jobs remain `FAILED` with a safe error code. Idempotency checks are per product, trigger, suggestion type, and pending status while holding the product row lock. AI/provider failures fall back before persistence.  
**Tradeoffs:** The database job table provides durability for this single service, but it is not a cross-service Kafka delivery guarantee. Failed jobs need a protected operational replay flow.  
**Extension point:** Publish the durable job to Kafka and add consumer group ownership, retry topics, and a dead-letter workflow when deploying the services separately.

## ADR-005: Use Flyway migrations and fail on schema drift

**Status:** Accepted.  
**Context:** JPA schema generation is convenient in demos but unsafe for controlled production rollouts.  
**Decision:** Flyway owns schema changes (`V1` through `V4`), while Hibernate uses `ddl-auto: validate`. Tests use H2 in MySQL mode and execute the same migrations.  
**Tradeoffs:** Migration syntax must remain compatible with both the deployed database and the test database. Production rollout still needs backup/rollback procedures.  
**Extension point:** Add an immutable forward migration for every schema change and test it from an empty database.

## ADR-006: Use a thin React console with explicit request/error states

**Status:** Accepted.  
**Context:** Reviewers need to see inventory signals and act on each suggestion without coupling UI state to business calculations.  
**Decision:** React uses `oidc-client-ts` Authorization Code + PKCE for a public OIDC client and keeps user tokens in session storage. It requests product and suggestion views from the REST API and shows trigger, reasoning, confidence, and separate accept/reject actions. The API returns stable error codes, friendly messages, and a request ID; the browser logger records only safe request metadata. Spring Security validates JWT signature, issuer, audience, and timestamps, then enforces scope/role checks by operation.  
**Tradeoffs:** The identity provider, client registration, and claim mapping must be configured per deployment. The current five-second refresh uses several requests per product and can be replaced with SSE or a consolidated query for larger catalogs.  
**Extension point:** Add SSE for server-pushed review updates or a paginated query endpoint without moving pricing rules into the browser.
