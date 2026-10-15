# UC-9 — Assign Role

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-02, BR-04, BR-05; Report 3 UC-09 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin assigns or changes the role of an account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- The account exists.

## Primary Flow

1. Admin opens the account detail and chooses "Change role".
2. Admin selects the new role from `RECEPTIONIST`, `DOCTOR`, `TECHNICIAN`, `CASHIER`. `ADMIN` is not offered (AQ-17).
3. The system checks the guards below.
4. The UI asks for confirmation, showing the old and new role and, if the old role is Doctor or Technician, that the room assignment will be removed.
5. The system replaces the role (exactly one role at all times, BR-02). If the old role is Doctor or Technician, it removes the active room assignment (AQ-09, AQ-16).
6. The system revokes all sessions of the account (AQ-08), writes `ROLE_CHANGED` to the audit table, and returns the updated detail.
7. If the new role is Doctor or Technician, the UI reminds Admin to assign a room (UC-15).

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Role not one of the five roles | `400` |
| New role equals the current role | `409` (no change) — **ASSUMPTION** |
| The account's current role is `ADMIN`, including the caller's own account | `409` (AQ-17). **ASSUMPTION:** code `ADMIN_ROLE_IMMUTABLE` |
| The new role is `ADMIN` | `409` (AQ-17). **ASSUMPTION:** code `ADMIN_ROLE_IMMUTABLE` |
| A Doctor or Technician has a visit `In Examination` or a paraclinical order `In Progress` assigned to them | `409` with the number of items in progress (AQ-09). Accounts with other roles have no such guard (AQ-17). **ASSUMPTION:** code `STAFF_HAS_WORK_IN_PROGRESS` |
| Account not found | `404` |

## Business Rules and Permissions

- Admin only. BR-02: exactly one role. BR-04: role is Admin-only.
- An Admin account keeps the `ADMIN` role for good (AQ-17); to remove an Admin's access, lock the account (UC-10). No account can be promoted to `ADMIN`; the only Admin is the bootstrap account (AQ-10).
- The user must log in again; an access token already issued keeps the old role for at most 15 minutes (AQ-08).

## State Changes and Transaction

- Replaces the role through an explicit domain method (`changeRole`), in one transaction with the work-in-progress check, the room-assignment removal, and the session revocation.
- The work-in-progress check reads the visit and paraclinical-order tables. Until those exist there is nothing to check; the guard is added with the use cases that create that work (UC-44, UC-65).
- No status change.

## Inputs and Outputs

- Input: `role`.
- Output: the account detail.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/assign-role`.

## Acceptance Criteria

1. After the change, the user's sessions cannot be refreshed, and a new login grants only the new role's functions.
2. An account never has zero roles or two roles.
3. After any role change of a Doctor or Technician, the account has no active room assignment.
4. Changing the role of any `ADMIN` account, or changing any account to `ADMIN`, is rejected, and nothing changes.
5. A change is rejected while a Doctor or Technician has work in progress, and nothing changes.
6. Non-Admin roles receive `403`.

## Test Considerations

- Authorization test: a token issued after the change has only the new role's access.
- Domain tests for the AQ-09 and AQ-17 guards.
- PostgreSQL integration test: the role change and the room-assignment removal commit or roll back together.

## Open Questions

- None.
- Decisions applied: AQ-08, AQ-09, AQ-11, AQ-16, AQ-17 in [`staff-account.md`](staff-account.md#decisions).
