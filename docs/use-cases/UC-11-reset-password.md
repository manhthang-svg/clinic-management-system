# UC-11 — Reset Password

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-03 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin resets the password of a staff account when the user forgets it.

## Trigger and Preconditions

- The user asks Admin in person (outside the system) to reset the password. There is no self-service "forgot password" flow (BR-03: only Admin can reset).
- The caller is authenticated with role `ADMIN`.
- The account exists.

## Primary Flow

1. Admin opens the account detail and chooses "Reset password".
2. The system generates a 12-character one-time password and marks the account "password change required" (AQ-02).
3. The system stores only its hash.
4. The system revokes all sessions of the account (AQ-08), writes `PASSWORD_RESET` to the audit table, and shows the one-time password to Admin once.
5. Admin gives the new password to the user through an out-of-band channel.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Account not found | `404` |
| Account is `LOCKED` | **ASSUMPTION:** reset is allowed and does not unlock the account |
| Admin resets their own password | `409`; Admin uses UC-3 for their own password — **ASSUMPTION**, follows the AQ-07 rule that Admins do not administer their own account |

## Business Rules and Permissions

- Admin only (BR-03).
- Reset does not change the status.

## State Changes and Transaction

- Password hash update, flag, session revocation, and audit event in one transaction.

## Inputs and Outputs

- Input: none.
- Output: `200` with `temporaryPassword`, shown once.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/reset-password`.

## Acceptance Criteria

1. After the reset, the old password no longer works, the one-time password does, and the user must change it at first login.
2. The new password is not logged and cannot be retrieved again.
3. Non-Admin roles receive `403`.

## Test Considerations

- Integration test: reset, then login with the old password fails and with the new password succeeds.
- Log-capture test that the password does not appear.

## Open Questions

- Decisions applied: AQ-01, AQ-02, AQ-08, AQ-11 in [`staff-account.md`](staff-account.md#decisions).
