# Security

## Document Authority and Scope

This document is the source of truth for technical security architecture, authentication, authorization enforcement, secrets, logging, and sensitive-data handling. Final roles and permissions belong in [`permission-matrix.md`](permission-matrix.md) and remain **TBD** until approved.

## Security Baseline

- Use Spring Security for backend authentication and authorization.
- Deny access by default; explicitly allow only public endpoints that have an approved need.
- Apply defense in depth across transport, API, application, domain, and persistence boundaries.
- Treat patient, medical, credential, and token data as sensitive by default.
- Do not rely on the frontend for authorization or protection of data.
- Applicable legal, regulatory, contractual, and retention requirements are **TBD**.

## Authentication

Authentication establishes who the caller is. The backend validates credentials and tokens; the frontend only transports them and manages user interaction.

### JWT Access Tokens

- Protect authenticated API calls with a JWT access token sent as `Authorization: Bearer <token>`.
- Access tokens are signed, short-lived, and validated for signature, issuer, audience, and expiry.
- Use a stable subject identifier. Include only the minimum claims needed for authentication and coarse authorization.
- Do not include passwords, refresh tokens, medical data, or unnecessary personally identifiable information in a JWT.
- Do not accept tokens through query parameters.
- Token lifetime, signing algorithm, issuer/audience values, key source, and key-rotation procedure are **TBD** before implementation.

### Refresh Tokens

At the architectural level, refresh tokens must support revocation and rotation:

- Use a high-entropy opaque refresh token rather than placing long-lived authorization data in a JWT.
- Store only a cryptographic hash of each active refresh token in durable server-side storage.
- Rotate the refresh token after every successful refresh and invalidate the previous token.
- Detect reuse of an invalidated token and revoke the associated token family/session.
- Scope refresh tokens to a session and expire them.
- For the browser client, prefer a `Secure`, `HttpOnly` cookie. The exact `SameSite`, path, and domain settings depend on the deployment topology and are **TBD**.
- Access-token and refresh-token lifetimes, session limits, revocation events, and persistence schema are **TBD**.
- Do not introduce Redis solely for token storage; the approved architecture does not require it.

## Password Handling

- Hash passwords with Spring Security's `DelegatingPasswordEncoder` and an adaptive one-way password hash.
- Prefer Argon2id when the deployment environment supports the required implementation; BCrypt is an acceptable supported alternative.
- Benchmark and document cost parameters before production deployment; parameters are **TBD**.
- Generate a unique salt through the selected encoder and never implement custom password cryptography.
- Never store, log, return, or include plaintext passwords in events or error messages.
- Password policy, reset flow, temporary credentials, lockout, and multi-factor authentication are **TBD** because account requirements are not finalized.

## Authentication Versus Authorization

- Authentication answers whether a caller has a valid identity.
- Authorization answers whether that identity may perform a specific operation on a specific scope of data.
- A valid token never implies permission for every operation.
- Domain invariants apply regardless of the caller's permissions; authorization does not replace business validation.

## Authorization Model

Use role-based access control with explicit permissions:

- Roles group permissions for administration and assignment.
- Permissions represent stable actions or capabilities checked by the application.
- Final role names, permission names, assignments, ownership rules, and exceptional access are **TBD** in [`permission-matrix.md`](permission-matrix.md).
- Do not hardcode undocumented role names throughout controllers or domain code.
- URL-level Spring Security rules establish coarse authentication boundaries.
- Use `@PreAuthorize` at application/use-case entry points for operation-level authorization once permissions are approved.
- Where authorization depends on a resource or data scope, load only the information required and enforce the rule before returning or changing protected data.
- The domain remains responsible for invariants and valid state transitions.

## `401` and `403`

- Return `401 Unauthorized` when credentials are missing, malformed, invalid, revoked, or expired. Include the appropriate `WWW-Authenticate` response header for bearer authentication.
- Return `403 Forbidden` when the caller is authenticated but lacks the required permission or allowed scope.
- Do not use `403` to represent an unauthenticated request.
- A protected resource may return `404` instead of revealing its existence when the approved data-visibility policy requires it; that policy is **TBD**.
- Security errors use the standard response from [`api-conventions.md`](api-conventions.md) without disclosing policy internals.

## CORS and CSRF for SPA and API

The intended client is a React SPA calling the Spring Boot API.

- Configure an explicit allow-list of frontend origins per environment. Do not use wildcard origins with credentials.
- Allow only required methods and headers, and keep preflight caching intentional.
- When access tokens are accepted only from the `Authorization` header and the API does not authenticate through ambient cookies, the stateless API routes may disable CSRF protection.
- A refresh endpoint using an `HttpOnly` cookie still receives ambient credentials. Protect it with suitable `SameSite` settings plus Origin/Referer validation and, where deployment topology requires it, a CSRF token mechanism.
- Do not store refresh tokens in `localStorage` or expose them to JavaScript.
- Whether frontend and backend are same-origin or cross-origin is **TBD** and determines the final CORS, cookie, and CSRF configuration.

## Secret and Key Handling

- Never hardcode or commit passwords, JWT keys, database credentials, API keys, or other secrets.
- Load secrets through environment variables or an approved secret-management mechanism.
- Separate secrets by environment and grant the minimum required access.
- Support rotation without requiring source-code changes.
- Do not place real secrets in example configuration, tests, logs, command history, or documentation.
- JWT key storage and rotation infrastructure are **TBD** before implementation.

## Logging and Audit Restrictions

Never log:

- plaintext or hashed passwords;
- access tokens, refresh tokens, session cookies, or authorization headers;
- sensitive medical content or full request/response bodies containing it;
- secrets or cryptographic key material.

Additional rules:

- Use structured logs and correlation identifiers that do not encode sensitive information.
- Minimize direct identifiers; mask or pseudonymize them when operational correlation genuinely requires a reference.
- Sanitize exceptions and validation failures before logging.
- Restrict and monitor access to application and audit logs.
- Security audit events, fields, retention, and access policy are **TBD** and must not be invented from technical assumptions.

## Sensitive Data Handling

- Use TLS for all non-local network traffic.
- Collect, return, and retain only the data required by approved use cases.
- Enforce authorization before reading or changing sensitive data.
- Avoid sensitive data in URLs, browser storage, caches, metrics, traces, and error details.
- Use parameterized queries and framework binding; never construct SQL from untrusted input.
- Backup protection, encryption at rest, field-level encryption, masking, retention, deletion, export, and breach-response requirements are **TBD**.

## Remaining Decisions

The following remain **TBD**:

- final roles, permissions, assignments, ownership rules, and exceptional access;
- login identifiers, password policy, recovery, lockout, session limits, and MFA;
- access-token and refresh-token lifetimes;
- JWT signing algorithm, issuer, audience, key storage, and rotation procedure;
- refresh-token persistence schema and revocation events;
- same-origin versus cross-origin deployment and final CORS/CSRF/cookie settings;
- resource-concealment policy for `404` versus `403`;
- audit-event catalog, retention, and access policy;
- legal, regulatory, encryption, retention, backup, deletion, and incident-response requirements.
