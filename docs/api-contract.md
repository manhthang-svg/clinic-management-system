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
| `GET` | `/api/v1/services/form-options` | Admin bearer token | `200` + service types, groups, and departments |
| `POST` | `/api/v1/services` | Admin bearer token | `201` + created service ID |
| `GET` | `/api/v1/services/{serviceId}` | Admin bearer token | `200` + service detail and active performing rooms |
| `PUT` | `/api/v1/services/{serviceId}` | Admin bearer token | `200` + updated or unchanged service detail |
| `PUT` | `/api/v1/services/{serviceId}/prices` | Admin bearer token | `200` + updated or unchanged service detail |
| `GET` | `/api/v1/services/{serviceId}/deactivation-check` | Admin bearer token | `200` + unfinished-order count |
| `GET` | `/api/v1/services/{serviceId}/activation-check` | Admin bearer token | `200` when activation is allowed |
| `PATCH` | `/api/v1/services/{serviceId}/status` | Admin bearer token | `200` + updated or unchanged service detail |
| `GET` | `/api/v1/visits/active?patientId={patientId}` | Receptionist bearer token | `200` + active visit, or `204` |
| `GET` | `/api/v1/visits/fee-preview` | Receptionist bearer token | `200` + examination fee breakdown |
| `POST` | `/api/v1/insurance/verifications` | Receptionist bearer token | `200` + simulated insurance result |
| `GET` | `/api/v1/patients/{patientId}/follow-up-appointment` | Receptionist bearer token | `200` + scheduled appointment, or `204` |
| `POST` | `/api/v1/follow-up-appointments/{appointmentId}/cancel` | Receptionist bearer token | `200` + cancelled appointment |
| `POST` | `/api/v1/visits` | Receptionist bearer token | `201` + registered visit |

UC-74 accepts either a 10-digit social-insurance number or a legacy health-insurance card consisting of two uppercase
letters followed by thirteen digits. The backend derives the 10-digit social-insurance number, queries the simulated
portal data in `health_insurance_records`, compares the returned identity with the patient, and returns a verification
status of `VALID`, `INVALID`, or `MISMATCH`. A completed lookup returns `200` even for an invalid or mismatched card;
the response includes portal identity data for MSG18. Visit creation never trusts a client verification flag and
repeats verification before applying the portal-provided coverage rate.

Owner matching prefers identity data. When portal `identityNumber` and patient `cccdSo` are both present, they must be
equal and the normalized name and date of birth must also match; unequal identity numbers never fall back. When either
identity number is missing, the backend compares name, date of birth at the portal-reported precision, and gender. A
successful fallback returns `verificationMethod=DEMOGRAPHIC` and `manualReviewRequired=true`; identity matching returns
`verificationMethod=IDENTITY` and `manualReviewRequired=false`.

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

UC-21 uses `GET /api/v1/services/{serviceId}`. It returns the current service data and optimistic-lock `version`.
For a paraclinical service, `performingRooms` contains active rooms in the same service group, ordered by room code.
An examination service has no service group and returns an empty `performingRooms` array. A missing service returns
`404 SERVICE_NOT_FOUND`.

UC-24 uses `PUT /api/v1/services/{serviceId}/prices` with `clinicPrice`, nullable `insurancePrice`, and the current
optimistic-lock `version`. Clinic price is a whole VND amount from 1 to 100,000,000; insurance price is either null
or a whole VND amount from 0 to 100,000,000. Sending unchanged prices performs no write. A stale changed request
returns `409 CONCURRENT_MODIFICATION`. Payment lines keep their creation-time price snapshots (BR-09); no separate
price-history table is used.

UC-25 checks status changes before confirmation and repeats the same business checks inside the transactional
`PATCH /api/v1/services/{serviceId}/status` command. Deactivation reports unfinished paraclinical order details in
`PENDING_PAYMENT`, `WAITING`, `IN_PROGRESS`, or `PENDING_RESULT`; those existing orders continue unchanged. The last
active examination service cannot be deactivated. A paraclinical service can only be activated when its service
group has an active room. Status changes use optimistic-lock `version`.

Successful responses with a body use `ApiResponse`; failures use `ErrorResponse`. FE code should branch on the stable
`code` field rather than the English `message`. Endpoint-specific statuses, codes, field constraints, nullable fields,
cookie behavior and response schemas are documented in the generated OpenAPI document.
