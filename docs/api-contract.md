# Frontend API Contract

The generated OpenAPI document is the source of truth for the currently implemented HTTP contract. Run the backend
with the `dev` profile, then use:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

OpenAPI is disabled outside development unless `OPENAPI_ENABLED=true` and `SWAGGER_UI_ENABLED=true` are explicitly
set. Business behavior comes from Report 3; shared transport conventions come from
[`api-conventions.md`](api-conventions.md).

## Implemented endpoints

| Method | Path | Authentication | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | None | `200` + `ApiResponse<SessionResponse>` + refresh cookie |
| `POST` | `/api/v1/auth/refresh` | Refresh cookie + exact `Origin` | `200` + new access token + rotated cookie |
| `POST` | `/api/v1/auth/logout` | Refresh cookie when present + exact `Origin` | `204` + cleared cookie |
| `GET` | `/api/v1/me/profile` | Bearer token | `200` + `ApiResponse<ProfileResponse>` |
| `PUT` | `/api/v1/me/profile` | Unrestricted bearer token | `200` + updated/unchanged profile |
| `POST` | `/api/v1/me/commands/change-password` | Bearer token | `204`; refresh afterward |
| `GET` | `/api/v1/accounts` | Admin bearer token | `200` + paginated account summaries |
| `POST` | `/api/v1/accounts` | Admin bearer token | `201` + one-time password |
| `GET` | `/api/v1/accounts/departments` | Admin bearer token | `200` + department filter options |
| `GET` | `/api/v1/accounts/{accountId}` | Admin bearer token | `200` + detail and allowed actions |
| `PUT` | `/api/v1/accounts/{accountId}` | Admin bearer token | `200` + updated detail |
| `GET` | `/api/v1/accounts/{accountId}/activities` | Admin bearer token | `200` + recent security activity |
| `GET` | `/api/v1/accounts/{accountId}/change-history` | Admin bearer token | `200` + paginated change history |
| `POST` | `/api/v1/accounts/{accountId}/commands/check-role-change` | Admin bearer token | `200` + work-in-progress counts |
| `GET` | `/api/v1/accounts/{accountId}/work-in-progress` | Admin bearer token | `200` + active visit/order counts |
| `POST` | `/api/v1/accounts/{accountId}/commands/change-role` | Admin bearer token | `200` + updated detail |
| `POST` | `/api/v1/accounts/{accountId}/commands/lock` | Admin bearer token | `200` + locked detail |
| `POST` | `/api/v1/accounts/{accountId}/commands/unlock` | Admin bearer token | `200` + unlocked detail |
| `POST` | `/api/v1/accounts/{accountId}/commands/reset-password` | Admin bearer token | `200` + one-time password |
| `GET` | `/api/v1/services` | Admin bearer token | `200` + paginated service and price summaries |

The access token is held in memory and sent as `Authorization: Bearer <token>`. The refresh token is an HttpOnly
cookie; browser calls to refresh and logout must include credentials and the configured frontend `Origin`.

When `passwordChangeRequired` is `true`, the access token may only view the caller's profile or change the password.
Profile updates return `403 FORBIDDEN`. After a successful password change, call refresh to receive an unrestricted
access token.

The account-administration endpoints implement UC-05 through UC-11. List pagination is zero-based and accepts page
sizes `10`, `20`, or `50`. Sort fields are allow-listed to `username`, `fullName`, `role`, `status`, and
`createdAt`, and `lastLoginAt`; the default is `createdAt,desc`. Account creation uses a client-generated
`submissionId`; replaying it never creates a second account
and never returns the one-time password again. Optimistic commands require the current `version`, `expectedRole`, or
`expectedStatus` documented in their request schema.

Report 3 does not define an approved Academic Title catalog. Until that catalog is approved, Academic Title is a
trimmed free-text value (maximum 50 characters), required for Doctor and Technician and optional for other roles.
Full name accepts Unicode letters and spaces only, up to 100 characters. UC-08 writes one history entry per changed
field.

UC-20 uses `GET /api/v1/services` with optional `keyword`, `type`, `group`, `department`, and `status` filters.
`group` and `department` contain UUID values. Pagination is zero-based, accepts sizes `10`, `20`, or `50`, and
defaults to `page=0&size=10`. Sorting uses `field,direction`; allowed fields are `code`, `name`, `type`,
`clinicPrice`, `insurancePrice`, and `status`, with `code,asc` as the default. A service not covered by insurance has
`insurancePrice: null`. An empty catalog or unmatched filter is a successful response with an empty `content` array.

Successful responses with a body use `ApiResponse`; failures use `ErrorResponse`. FE code should branch on the stable
`code` field rather than the English `message`. Endpoint-specific statuses, codes, field constraints, nullable fields,
cookie behavior and response schemas are documented in the generated OpenAPI document.
