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

The access token is held in memory and sent as `Authorization: Bearer <token>`. The refresh token is an HttpOnly
cookie; browser calls to refresh and logout must include credentials and the configured frontend `Origin`.

When `passwordChangeRequired` is `true`, the access token may only view the caller's profile or change the password.
Profile updates return `403 FORBIDDEN`. After a successful password change, call refresh to receive an unrestricted
access token.

Successful responses with a body use `ApiResponse`; failures use `ErrorResponse`. FE code should branch on the stable
`code` field rather than the English `message`. Endpoint-specific statuses, codes, field constraints, nullable fields,
cookie behavior and response schemas are documented in the generated OpenAPI document.
