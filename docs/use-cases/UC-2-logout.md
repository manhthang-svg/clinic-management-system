# UC-2 — Logout

| Item | Value |
| --- | --- |
| Feature | Authentication |
| Actors | Admin, Receptionist, Doctor, Technician, Cashier |
| Source | `usecases.xlsx` sheet `Mới`; [`../security.md`](../security.md) |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

A staff member signs out and ends their current working session.

## Trigger and Preconditions

- Trigger: the user chooses "Log out".
- The request carries the refresh-token cookie of the current session.

## Primary Flow

1. The user chooses "Log out".
2. The system revokes the refresh-token family of the current session.
3. The system clears the refresh-token cookie.
4. The client discards the access token from memory and shows the login screen.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Cookie missing, invalid, expired, or already revoked | `204`; the cookie is cleared anyway. **ASSUMPTION:** logout is idempotent so the user can always leave the session |
| Request fails the CSRF/Origin check | `403`; nothing is revoked |

## Business Rules and Permissions

- Only the current session ends. Other sessions of the same account (for example on another computer) stay valid. **ASSUMPTION:** "end their current working session" in the source.
- Available to every role. It does not require a valid access token, because the access token may already have expired.

## State Changes and Transaction

- Marks the session's refresh-token family as revoked, in one transaction.
- The access token already issued stays valid until it expires. This is accepted by the stateless design in security.md; its short lifetime limits the window.

## Inputs and Outputs

- Input: refresh-token cookie.
- Output: `204 No Content` and an expired cookie.
- **ASSUMPTION (proposed API):** `POST /api/v1/auth/logout`.

## Acceptance Criteria

1. After logout, the old refresh token cannot be used to refresh.
2. Logging out twice returns `204` both times.
3. Logging out one session does not end another session of the same account.

## Test Considerations

- API test: login, logout, then refresh with the old cookie returns `401`.
- CSRF/Origin rejection test for the cookie-based endpoint.

## Open Questions

- **TBD:** Is a "log out all my sessions" action needed? It is not in the use-case list.
- AQ-14 in [`staff-account.md`](staff-account.md#open-questions).
