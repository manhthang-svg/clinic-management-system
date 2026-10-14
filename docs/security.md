# Security

## Document Authority and Scope

This document is the source of truth for technical security architecture, authentication, authorization enforcement, secrets, logging, and sensitive-data handling. Roles and permissions belong in [`permission-matrix.md`](permission-matrix.md). Decisions marked with a date were approved on that date.

## Security Baseline

- Use Spring Security for backend authentication and authorization.
- Deny access by default; explicitly allow only public endpoints that have an approved need.
- Apply defense in depth across transport, API, application, domain, and persistence boundaries.
- Treat patient, medical, credential, and token data as sensitive by default.
- Do not rely on the frontend for authorization or protection of data.
- Applicable legal, regulatory, contractual, and retention requirements are **TBD** pending legal review; see [Sensitive Data Handling](#sensitive-data-handling).

## Authentication

Authentication establishes who the caller is. The backend validates credentials and tokens; the frontend only transports them and manages user interaction.

### JWT Access Tokens

- Protect authenticated API calls with a JWT access token sent as `Authorization: Bearer <token>`.
- Spring Security OAuth2 Resource Server remains the mechanism for bearer-token extraction, JWT decoding, validation, and authentication-context creation.
- Do not introduce JJWT or a custom bearer-token authentication filter. Extend the Resource Server configuration through supported decoders, validators, and converters when customization is required.
- Access tokens are signed and short-lived. Validation must cover the signature, issuer, audience, expiration, and an explicitly configured clock-skew allowance.
- Use the stable technical account UUID as the JWT subject. Do not use a mutable username, email address, or other login identifier as the subject.
- Include only the minimum claims needed for authentication and coarse authorization. A JWT ID or other additional claim requires a documented use case.
- Do not include passwords, refresh tokens, medical data, or unnecessary personally identifiable information in a JWT.
- Do not accept tokens through query parameters.

Decided parameters (14/10/2026):

| Parameter | Decision |
| --- | --- |
| Lifetime | 15 minutes |
| Algorithm | `HS256` (HMAC-SHA256). The same application issues and validates tokens, so no public key needs to be distributed |
| Signing key | At least 256 random bits, base64-encoded, from the environment variable `JWT_SIGNING_KEY`. Startup fails if it is missing or too short |
| Issuer (`iss`) | Configurable; default `clinic-backend` |
| Audience (`aud`) | Configurable; default `clinic-web` |
| Clock skew | 30 seconds |
| Claims | `sub` (account UUID), `iss`, `aud`, `iat`, `exp`, `role` (single role, BR-02), `sid` (login-session ID; UC-3 keeps the caller's own session while revoking the others), `pwd_change` (present and `true` only while a password change is required, see [Password Handling](#password-handling)) |
| Key rotation | Replace `JWT_SIGNING_KEY` and restart. Outstanding access tokens become invalid and clients obtain new ones through refresh, because refresh tokens are opaque and do not depend on the key. No previous-key validation is kept |

- The `role` claim is acceptable because access tokens live 15 minutes and every refresh re-reads the account from the database, so role, status, and password-change state are re-evaluated at least that often.

### Refresh Tokens

At the architectural level, refresh tokens must support revocation and rotation:

- Generate an opaque, cryptographically random, high-entropy value rather than placing long-lived authorization data in a JWT.
- Store only the SHA-256 digest of each refresh token. The raw value must never be persisted or logged.
- Rotate the refresh token after every successful refresh and invalidate the previous token.
- Create an independent token family for each successful login or device session so one session can be revoked without invalidating unrelated sessions.
- Detect reuse of an already-rotated refresh token and revoke the corresponding token family.
- Apply an absolute family/session expiry that rotation cannot extend, plus an idle timeout (each refresh token expires if unused; values below).
- Serialize concurrent refresh requests with a database-safe mechanism so the same token cannot be rotated successfully more than once. Decided strategy: a single conditional update that marks the presented token as used only if it is still unused (`UPDATE ... SET used_at = :now WHERE token_hash = :hash AND used_at IS NULL`); exactly one request sees one affected row and may rotate. Any other request for the same token is treated as reuse. This must be verified with a PostgreSQL concurrency test.
- Several browser tabs share one refresh cookie. The frontend must run refresh as a single in-flight operation shared across tabs (for example with the Web Locks API), because a parallel refresh would be detected as reuse and end the session.
- Return a generic `401 Unauthorized` response for missing, invalid, expired, revoked, or replayed refresh tokens. Detailed failure reasons belong only in sanitized internal security telemetry and must not reveal token material.
- The browser client receives the refresh token only as a cookie: name `clinic_refresh`, `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, no `Domain` attribute (host-only), `Max-Age` equal to the remaining absolute session lifetime. The `Secure` flag is configurable only so that plain-HTTP local development works; it must be enabled in every deployed environment.
- PostgreSQL is the durable persistence mechanism for refresh-token sessions, rotation, and revocation. Redis is not required for authentication correctness and must not be introduced without a separate concrete requirement.

Decided session parameters (14/10/2026):

| Parameter | Decision |
| --- | --- |
| Refresh-token idle timeout | 2 hours: a refresh token not used within 2 hours of issue expires |
| Absolute session (family) lifetime | 12 hours from login, not extended by rotation; covers one working day |
| Session limit per account | None; each login creates a new family |
| Refresh-token value | 32 random bytes from `SecureRandom`, base64url-encoded; stored as its SHA-256 digest |
| Persistence | A session-family table (account, created, absolute expiry, revoked time and reason) and a refresh-token table (family, digest unique, issued, expires, used time) |
| Revocation events | Logout: the current family. Reuse detected: that family. Account locked, role changed, password reset by Admin: all families of the account. Own password changed: all other families of the account |
| Cleanup | A daily job deletes families whose absolute expiry or revocation is older than 30 days, together with their tokens |

## Password Handling

- Hash passwords with Spring Security's `DelegatingPasswordEncoder` and an adaptive one-way password hash.
- Decided encoder: BCrypt with strength 12, as the default of the `DelegatingPasswordEncoder` (`{bcrypt}` prefix). Argon2id was not chosen because Spring Security's implementation requires an additional cryptography dependency. The prefix allows a later migration without rehashing existing users at once.
- Benchmark strength 12 on production hardware before go-live; target 250–500 ms per hash. Adjust the strength through configuration if the benchmark misses the target.
- Generate a unique salt through the selected encoder and never implement custom password cryptography.
- Never store, log, return, or include plaintext passwords in events or error messages.

Decided account-security rules (14/10/2026; business decisions recorded in [`use-cases/staff-account.md`](use-cases/staff-account.md#decisions)):

| Topic | Decision |
| --- | --- |
| Login identifier | Username, compared case-insensitively (stored normalized to lowercase), immutable after creation |
| Password policy | 8–64 characters and at most 72 bytes in UTF-8 (BCrypt input limit). No composition rules. The new password must differ from the current one |
| Temporary passwords | On account creation and Admin reset, the system generates a 12-character random password from an alphabet without look-alike characters, shows it to Admin once, and sets "password change required" |
| Password change required | Login succeeds, but the access token carries `pwd_change: true`, and such a token is accepted only for change password (UC-3), view own profile, and logout. After the change, the client refreshes to receive a normal token |
| Recovery | No self-service recovery. Only Admin can reset (BR-03, UC-11) |
| Failed-login throttling | After 5 consecutive failed password checks for an existing account, password login for that account is refused for 15 minutes. This is not the Admin `LOCKED` status, and the response is the same generic `401`. A successful login resets the counter. Unknown usernames still run a password-hash comparison so that response time does not reveal whether the account exists |
| Multi-factor authentication | Not required |

## Authentication Versus Authorization

- Authentication answers whether a caller has a valid identity.
- Authorization answers whether that identity may perform a specific operation on a specific scope of data.
- A valid token never implies permission for every operation.
- Domain invariants apply regardless of the caller's permissions; authorization does not replace business validation.

## Authorization Model

Use role-based access control with explicit permissions:

- Roles group permissions for administration and assignment.
- Permissions represent stable actions or capabilities checked by the application.
- Role names and assignments are defined in [`permission-matrix.md`](permission-matrix.md). There is no exceptional or emergency access.
- Decided permission model (14/10/2026): permissions are named `<resource>:<action>` in lowercase kebab-case (for example `staff-account:manage`). The role-to-permission mapping is a fixed table in backend code, because the five roles are fixed by BR-02 and no use case lets Admin edit permissions. The JWT carries only the role; a converter expands it into permission authorities. Each permission is introduced with the use case that needs it and is listed in the permission matrix.
- Do not hardcode undocumented role names throughout controllers or domain code.
- URL-level Spring Security rules establish coarse authentication boundaries.
- Use `@PreAuthorize` at application/use-case entry points for operation-level authorization once permissions are approved.
- Where authorization depends on a resource or data scope, load only the information required and enforce the rule before returning or changing protected data.
- The domain remains responsible for invariants and valid state transitions.

## `401` and `403`

- Return `401 Unauthorized` when credentials are missing, malformed, invalid, revoked, or expired. Include the appropriate `WWW-Authenticate` response header for bearer authentication.
- Return `403 Forbidden` when the caller is authenticated but lacks the required permission or allowed scope.
- Do not use `403` to represent an unauthenticated request.
- Do not expose distinct client-visible refresh-token errors for missing, invalid, expired, revoked, or replayed tokens; use the generic `401` contract defined above.
- Decided visibility policy (14/10/2026): a caller whose role lacks the permission for an operation receives `403`. A caller who has the permission but asks for a specific resource outside their data scope (for example a Doctor asking for a visit of another room, BR-19) receives `404`, so the existence of patient data is not revealed.
- Security errors use the standard response from [`api-conventions.md`](api-conventions.md) without disclosing policy internals.

## CORS and CSRF for SPA and API

The intended client is a React SPA calling the Spring Boot API.

- Configure an explicit allow-list of frontend origins per environment. Do not use wildcard origins with credentials.
- Allow only required methods and headers, and keep preflight caching intentional.
- When access tokens are accepted only from the `Authorization` header and the API does not authenticate through ambient cookies, the stateless API routes may disable CSRF protection.
- Refresh and logout endpoints that use an `HttpOnly` refresh-token cookie receive ambient credentials and require targeted CSRF protection. Do not impose cookie-oriented CSRF handling on unrelated bearer-only API routes without a demonstrated need.
- Targeted protection must combine suitable cookie settings with Origin/Referer validation and, where required by the approved deployment topology, a CSRF token mechanism.
- Do not store refresh tokens in `localStorage` or expose them to JavaScript.
- Decided topology (14/10/2026): **same-origin**. In every deployed environment one host serves the SPA and forwards `/api/` to the backend through a reverse proxy; in development the Vite dev server proxies `/api` (already configured).
- CORS stays disabled: the backend sends no CORS headers and cross-origin browser calls fail. An allow-list may be added through configuration only for a documented need.
- CSRF protection for the cookie-based refresh and logout endpoints is `SameSite=Strict` plus a mandatory `Origin` header check against the configured frontend origin (`APP_FRONTEND_ORIGIN`, for example `http://localhost:5173` in development). Requests with a missing or different `Origin` receive `403`. No CSRF token is used. All other API routes are bearer-only and keep CSRF disabled.

## Secret and Key Handling

- Never hardcode or commit passwords, JWT keys, database credentials, API keys, or other secrets.
- Load secrets through environment variables or an approved secret-management mechanism.
- Separate secrets by environment and grant the minimum required access.
- Support rotation without requiring source-code changes.
- Do not place real secrets in example configuration, tests, logs, command history, or documentation.
- Represent future JWT, cookie, and CORS settings with validated `@ConfigurationProperties` so invalid or missing required configuration fails at startup.
- The JWT signing key comes from `JWT_SIGNING_KEY`, set by the deployment environment's secret store. Rotation is described in [JWT Access Tokens](#jwt-access-tokens). Local development and tests generate or use a non-production key that is never committed in a reusable form.

## Time-Dependent Authentication Logic

- Inject `java.time.Clock` into authentication components that issue, validate, rotate, expire, revoke, or clean up tokens.
- Use a fixed clock in tests to verify boundary behavior deterministically; do not use sleeps or the system clock directly in authentication tests.
- Keep transaction boundaries for refresh rotation and revocation in the application/use-case layer, and verify concurrency and commit behavior with PostgreSQL integration tests.

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
- Decided security audit (14/10/2026): security and account-administration events are stored in a PostgreSQL audit table, separate from application logs. Fields: ID, time, actor account ID (empty when unauthenticated), event type, target account ID, outcome, correlation ID. Never stored: passwords, tokens, the submitted username of a failed login for an unknown account, medical data.
- Event types: `LOGIN_SUCCEEDED`, `LOGIN_FAILED` (existing account only), `LOGIN_THROTTLED`, `LOGOUT`, `REFRESH_TOKEN_REUSE_DETECTED`, `PASSWORD_CHANGED`, `ACCOUNT_CREATED`, `ACCOUNT_UPDATED`, `ROLE_CHANGED`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`, `PASSWORD_RESET`. Audit events for clinical data are defined with the clinical use cases.
- Audit records are append-only. No use case exposes them through the API yet, so only operators with database access can read them. They are not deleted until a retention period is set by legal review.
- An Admin action and its audit event are written in the same transaction. Failed-login events and throttling counters are committed even though the login fails.

## Sensitive Data Handling

- Use TLS for all non-local network traffic.
- Collect, return, and retain only the data required by approved use cases.
- Enforce authorization before reading or changing sensitive data.
- Avoid sensitive data in URLs, browser storage, caches, metrics, traces, and error details.
- Use parameterized queries and framework binding; never construct SQL from untrusted input.
- Decided technical baseline (14/10/2026): database storage and backups must be encrypted at rest by the hosting platform; database backups follow the same access restrictions as the database. No field-level encryption is applied now, because no current use case justifies the key-management cost; this is revisited after legal review.
- **TBD (legal review):** retention and deletion periods for medical records and personal data, data-export rights, and breach response. The review must cover at least Vietnam's personal-data protection decree (Nghị định 13/2023/NĐ-CP) and the Ministry of Health rules on electronic medical records (Thông tư 46/2018/TT-BYT). These are legal decisions and are not made by the engineering team.

## Remaining Decisions

All technical security decisions listed previously were made on 14/10/2026 and are recorded in the sections above. The following still remain **TBD**:

- legal and regulatory requirements: retention, deletion, data export, breach response, and the audit retention period;
- benchmark confirmation of the BCrypt strength on production hardware;
- technical permission names for use cases that are not yet specified.
