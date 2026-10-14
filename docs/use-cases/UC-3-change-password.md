# UC-3 — Change Password

| Item | Value |
| --- | --- |
| Feature | Profile Management |
| Actors | Admin, Receptionist, Doctor, Technician, Cashier |
| Source | `usecases.xlsx` sheet `Mới`; BR-03; [`../security.md`](../security.md#password-handling) |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

A staff member changes their own password after entering the current password.

## Trigger and Preconditions

- The user is authenticated and the account is `ACTIVE`.

## Primary Flow

1. The user enters the current password, the new password, and the confirmation of the new password.
2. The system verifies the current password.
3. The system checks the new password against the policy: 8–64 characters, at most 72 bytes in UTF-8, different from the current password (AQ-03).
4. The system stores the hash of the new password and clears "password change required".
5. The system revokes all other sessions of the account, keeps the current one (AQ-08), writes `PASSWORD_CHANGED` to the audit table, and confirms success. If the token carried `pwd_change: true`, the client refreshes to receive a normal token.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| A field is missing, or the confirmation does not match | `400 REQUEST_VALIDATION_FAILED`. The confirmation check may stay client-side only — **ASSUMPTION** |
| Current password incorrect | Rejected (BR-03). **ASSUMPTION:** `422` with code `CURRENT_PASSWORD_INCORRECT`, not `401`, so the client does not treat it as a logout |
| New password violates the policy | `422` with the violated rule |
| New password equals the current password | `422` (AQ-03) |

## Business Rules and Permissions

- BR-03: the correct current password is required.
- Own account only. The account ID comes from the authenticated token, never from the request.
- Repeated wrong current passwords: **ASSUMPTION** — they count toward the same failed-login counter as UC-1 (AQ-04), so an unattended session cannot be used to guess the password.

## State Changes and Transaction

- Updates the password hash, clears the flag, revokes other sessions, and writes the audit event in one transaction.
- No status change.

## Inputs and Outputs

- Input: `currentPassword`, `newPassword`.
- Output: `204 No Content`.
- **ASSUMPTION (proposed API):** `POST /api/v1/me/commands/change-password`.

## Acceptance Criteria

1. With the correct current password and a valid new password, the user can log in with the new password and not with the old one.
2. With an incorrect current password, the password does not change.
3. Neither password appears in logs, errors, or responses.

## Test Considerations

- Application test with a password-encoder double; API test for the error mapping.
- Integration test that login works with the new password.

## Open Questions

None. Decisions applied: AQ-03, AQ-04, AQ-08 in [`staff-account.md`](staff-account.md#decisions).
