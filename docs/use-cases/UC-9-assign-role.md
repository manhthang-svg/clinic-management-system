# UC-9 — Assign Role

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-02, BR-04 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin assigns or changes the role of an account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- The account exists.

## Primary Flow

1. Admin opens the account detail and chooses "Change role".
2. Admin selects the new role.
3. The system checks the guards below.
4. The system replaces the role (exactly one role at all times, BR-02).
5. The system revokes all sessions of the account (AQ-08), writes `ROLE_CHANGED` to the audit table, and returns the updated detail.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Role not one of the five roles | `400` |
| New role equals the current role | `409` (no change) — **ASSUMPTION** |
| Admin changes their own role, or the change would leave no `ACTIVE` Admin | `409` (AQ-07) |
| Doctor or Technician still has room assignments or in-progress work | **TBD** (AQ-09) |
| Account not found | `404` |

## Business Rules and Permissions

- Admin only. BR-02: exactly one role. BR-04: role is Admin-only.
- The user must log in again; an access token already issued keeps the old role for at most 15 minutes (AQ-08).

## State Changes and Transaction

- Replaces the role through an explicit domain method (`changeRole`), in one transaction with any session revocation.
- No status change.

## Inputs and Outputs

- Input: `role`.
- Output: the account detail.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/assign-role`.

## Acceptance Criteria

1. After the change, the user's sessions cannot be refreshed, and a new login grants only the new role's functions.
2. An account never has zero roles or two roles.
3. Non-Admin roles receive `403`.

## Test Considerations

- Authorization test: a token issued after the change has only the new role's access.
- Domain tests for the AQ-07 guards; AQ-09 guards once decided.

## Open Questions

- AQ-09 in [`staff-account.md`](staff-account.md#open-questions).
- Decisions applied: AQ-07, AQ-08, AQ-11 in [`staff-account.md`](staff-account.md#decisions).
