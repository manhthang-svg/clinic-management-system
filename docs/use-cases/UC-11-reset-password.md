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
2. The system sets a new password (mechanism **TBD**, AQ-02).
3. The system stores only its hash.
4. The system applies the session policy (AQ-08).
5. Admin gives the new password to the user through an out-of-band channel.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Account not found | `404` |
| Admin-typed password violates the policy | `422` (only if AQ-02 chooses Admin-typed passwords) |
| Account is `LOCKED` | **ASSUMPTION:** reset is allowed and does not unlock the account |
| Admin resets their own password | **TBD**; recommended to use UC-3 instead |

## Business Rules and Permissions

- Admin only (BR-03).
- Reset does not change the status.

## State Changes and Transaction

- Password hash update and session revocation (if AQ-08 confirms it) in one transaction.

## Inputs and Outputs

- Input: none, or `newPassword` if Admin types it (AQ-02).
- Output: the generated one-time password shown once, or `204`.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/reset-password`.

## Acceptance Criteria

1. After the reset, the old password no longer works and the new one does.
2. The new password is not logged and cannot be retrieved again.
3. Non-Admin roles receive `403`.

## Test Considerations

- Integration test: reset, then login with the old password fails and with the new password succeeds.
- Log-capture test that the password does not appear.

## Open Questions

- AQ-01, AQ-02, AQ-03, AQ-08, AQ-11 in [`staff-account.md`](staff-account.md#open-questions).
