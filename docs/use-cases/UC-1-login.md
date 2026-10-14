# UC-1 — Login

| Item | Value |
| --- | --- |
| Feature | Authentication |
| Actors | Admin, Receptionist, Doctor, Technician, Cashier |
| Source | `usecases.xlsx` sheet `Mới`; BR-01, BR-02; [`../security.md`](../security.md) |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

A staff member signs in with username and password to access the functions of their role.

## Trigger and Preconditions

- Trigger: the user submits the login form.
- The account exists.
- No authentication is required to call this use case.

## Primary Flow

1. The user enters username and password.
2. The system finds the account by username (case handling: AQ-05).
3. The system verifies the password against the stored hash.
4. The system checks that the account is `ACTIVE`.
5. The system creates a new session (refresh-token family), issues a short-lived access token whose subject is the account UUID, and sets the refresh token as an `HttpOnly` cookie.
6. The system returns the access token and the account summary (ID, username, full name, role).
7. The client opens the home screen for the role.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Username or password missing or blank | `400 REQUEST_VALIDATION_FAILED` |
| Unknown username or wrong password | `401` with a generic message; the response does not reveal which part was wrong |
| Account is `LOCKED` | Login refused (BR-01). Whether the response says "locked" or uses the same generic `401` is **TBD** (UQ-1) |
| Too many failed attempts | **TBD** (AQ-04) |
| First login with a one-time password | **TBD** (AQ-01) |

## Session Refresh (supporting operation)

UC-1 creates the session; renewing the access token is part of the same capability and follows [`../security.md`](../security.md#refresh-tokens):

- The client calls refresh with the refresh-token cookie.
- The system rotates the refresh token and issues a new access token.
- Refresh fails with a generic `401` when the token is missing, invalid, expired, revoked, or reused, or when the account is now `LOCKED` (invariant 3 in the shared model).
- Reuse of a rotated token revokes the whole family.

## Business Rules and Permissions

- BR-01: usernames are unique; a locked account cannot sign in.
- BR-02: the token grants only the functions of the account's single role. How the role is carried (token claim or looked up per request) is **TBD** together with AQ-08.
- Public endpoint. CSRF protection for the cookie-based refresh and logout follows security.md.

## State Changes and Transaction

- No account status change.
- Creates a refresh-token family and token record in one transaction.
- A successful login does not update profile data. Whether to record a last-login time is **TBD** (AQ-11).

## Inputs and Outputs

- Input: `username`, `password`.
- Output: access token, its expiry, and an account summary. The refresh token is only in the cookie, never in the body.
- **ASSUMPTION (proposed API):** `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`.

## Acceptance Criteria

1. An active account with correct credentials receives an access token and a refresh cookie (`Secure`, `HttpOnly`).
2. A wrong password and an unknown username produce identical responses.
3. A locked account cannot log in, even with the correct password.
4. A refresh token works once; using it a second time fails and revokes the family.
5. After the account is locked, refresh fails.
6. Passwords and tokens never appear in logs or error responses.

## Test Considerations

- API tests for each row of the error table; PostgreSQL Testcontainers for token persistence and rotation.
- A concurrent refresh with the same token succeeds at most once.
- Fixed `Clock` for expiry boundaries.

## Open Questions

- UQ-1 **TBD:** Should a locked account get an explicit "account locked, contact Admin" message? Explicit message is friendlier for staff but confirms that the username exists.
- AQ-01, AQ-04, AQ-05, AQ-08, AQ-14 in [`staff-account.md`](staff-account.md#open-questions).
- Token lifetimes, signing key, and cookie attributes remain **TBD** in security.md.
