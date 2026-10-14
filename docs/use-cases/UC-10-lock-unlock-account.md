# UC-10 — Lock/Unlock Account

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-01 |
| Shared model | [`staff-account.md`](staff-account.md) — transitions A02, A03 |

## Goal

Admin locks an account to block sign-in, or unlocks a previously locked account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- Lock: the account is `ACTIVE`. Unlock: the account is `LOCKED`.

## Primary Flow — Lock

1. Admin chooses "Lock" on an active account.
2. The system rejects the lock if the account is the caller's own or the last `ACTIVE` Admin (AQ-07).
3. The system changes the status to `LOCKED` (A02).
4. The system revokes all sessions of the account (AQ-08) and writes `ACCOUNT_LOCKED` to the audit table.

## Primary Flow — Unlock

1. Admin chooses "Unlock" on a locked account.
2. The system changes the status to `ACTIVE` (A03) and writes `ACCOUNT_UNLOCKED` to the audit table.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Lock a `LOCKED` account, or unlock an `ACTIVE` account | `409` invalid transition |
| Admin locks their own account, or the last `ACTIVE` Admin | `409` (AQ-07) |
| Account not found | `404` |

## Business Rules and Permissions

- Admin only.
- BR-01: a locked account cannot sign in. By the shared invariant 3, it also cannot refresh a session.
- **TBD:** Can a Doctor or Technician who has patients currently in progress be locked? Their in-progress visits might be stranded; this is not covered by the sources.
- Whether a lock reason must be entered is **TBD**.

## State Changes and Transaction

- Explicit domain methods `lock()` and `unlock()`.
- Lock: status change, revocation of all refresh-token families, and the audit event in one transaction.

## Inputs and Outputs

- Input: none (plus a reason, if required).
- Output: the account detail.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/lock` and `.../commands/unlock`.

## Acceptance Criteria

1. A locked account cannot log in (UC-1) or refresh.
2. An unlocked account can log in again with its existing password.
3. Invalid transitions are rejected and change nothing.

## Test Considerations

- Domain test for both transitions and both invalid transitions.
- Integration test: lock an account, then its refresh fails.

## Open Questions

- Decisions applied: AQ-07, AQ-08, AQ-11 in [`staff-account.md`](staff-account.md#decisions).
