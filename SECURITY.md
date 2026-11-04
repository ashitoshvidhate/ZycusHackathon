# Security notes

The LiteLLM bearer token and session cookie shared during setup should be revoked/rotated before use. They are not stored in this repository. Configure replacement values as backend-only environment variables or in a deployment secret manager.

## Current boundary

- The API now requires OAuth2 JWT bearer tokens and checks signature, issuer, timestamps, audience, and route permissions. A real issuer/JWKS URI, audience, and matching React public client must be configured; the repository does not provision an identity provider or user accounts.
- Backend authorities map token scopes such as `stockpulse.read`, `inventory.write`, `suggestions.write`, `suggestions.review`, and `strategy.admin`, plus `roles` or Keycloak `realm_access.roles` claims.
- HTTPS termination, identity-provider client configuration, tenant-specific claim mapping, secrets rotation, and production CORS allowlists remain deployment configuration.
- Kafka Compose settings use local plaintext credentials and Kafka is not connected to application producers/consumers.

Do not expose the system until the issuer and client are configured for your environment and served over TLS. Keep gateway keys and cookies in a secret manager. Never log authorization headers, cookies, request bodies, or raw LLM responses.

Before publishing, run a secret scanner such as `gitleaks detect --source . --redact` and inspect Git history. Report vulnerabilities privately to the repository owner.
