# UC-10 — Lock/Unlock Account

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-01; Report 3 UC-10 |
| Shared model | [`staff-account.md`](staff-account.md) — transitions A02, A03 |

## Goal

Admin locks an account to block sign-in, or unlocks a previously locked account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- Lock: the account is `ACTIVE`. Unlock: the account is `LOCKED`.

## Primary Flow — Lock

1. Admin chooses "Lock" on an active account and enters the required reason.
2. The system rejects the lock if the reason is empty, or if the account is the caller's own or the last `ACTIVE` Admin (AQ-07).
3. If the account is a Doctor or Technician with a visit `In Examination` or a paraclinical order `In Progress`, the UI warns that this work stays in the room's lists for another staff member of the same room. The lock is not blocked (Report 3 UC-10 AF-04).
4. The system changes the status to `LOCKED` (A02) and stores the reason, the lock time, and the locking Admin.
5. The system revokes all sessions of the account (AQ-08) and writes `ACCOUNT_LOCKED` to the audit table.

## Primary Flow — Unlock

1. Admin chooses "Unlock" on a locked account.
2. The UI asks for confirmation.
3. The system changes the status to `ACTIVE` (A03), clears the lock details, resets the failed-login counter and any login throttle (Report 3 UC-10 AF-01), and writes `ACCOUNT_UNLOCKED` to the audit table.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Lock reason empty or longer than 500 characters | `400 REQUEST_VALIDATION_FAILED`. **ASSUMPTION:** the 500-character limit; Report 3 sets none |
| Lock a `LOCKED` account, or unlock an `ACTIVE` account | `409` invalid transition |
| Admin locks their own account, or the last `ACTIVE` Admin | `409` (AQ-07) |
| Account not found | `404` |

## Business Rules and Permissions

- Admin only.
- BR-01: a locked account cannot sign in. By the shared invariant 3, it also cannot refresh a session.
- A Doctor or Technician with work in progress can be locked; the work stays in the room's lists (Report 3 UC-10 AF-04, BR-01).
- The lock reason is required (Report 3 UC-10 step 4, EF-01).
- This is the Admin `LOCKED` status only. Failed-login throttling (AQ-04) is separate and never sets `LOCKED`.

## State Changes and Transaction

- Explicit domain methods `lock()` and `unlock()`.
- Lock: status change with lock details, revocation of all refresh-token families, and the audit event in one transaction.
- Unlock: status change, counter reset, and the audit event in one transaction.

## Inputs and Outputs

- Lock input: `reason`. Unlock input: none.
- Output: the account detail.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts/{id}/commands/lock` and `.../commands/unlock`.

## Acceptance Criteria

1. A locked account cannot log in (UC-1) or refresh.
2. An unlocked account can log in again with its existing password.
3. A lock without a reason is rejected; Admin sees the reason, time, and locking Admin in UC-6.
4. Invalid transitions are rejected and change nothing.

## Test Considerations

- Domain test for both transitions and both invalid transitions.
- Integration test: lock an account, then its refresh fails.

## Open Questions

- Decisions applied: AQ-07, AQ-08, AQ-11 in [`staff-account.md`](staff-account.md#decisions).
