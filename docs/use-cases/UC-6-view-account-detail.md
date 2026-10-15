# UC-6 — View Account Detail

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; Report 3 UC-06 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin views the detailed information of a selected staff account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- Admin selects an account, usually from the list in UC-5.

## Primary Flow

1. Admin selects an account.
2. The system returns its details.
3. From this screen Admin can start UC-8, UC-9, UC-10, UC-11, or, for a Doctor or Technician, UC-15. On the caller's own account, the lock action of UC-10 and UC-11 are not offered (AQ-07). On any `ADMIN` account, UC-9 is not offered (AQ-17).

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Account ID does not exist | `404` |
| Caller is not Admin | `403` |

## Business Rules and Permissions

- Admin only.

## State Changes and Transaction

- Read-only transaction.

## Inputs and Outputs

- Output (Report 3 UC-06):
  - account: ID, username, role, status, created time, created by;
  - personal: full name, academic title, date of birth, gender, phone number, email, address;
  - assignment: for Doctor and Technician the room code, name, location, and department, or "not assigned"; for other roles "not applicable" (AQ-12);
  - security: last sign-in time, consecutive failed logins, login throttled until (AQ-04), lock reason, lock time and locking Admin while `LOCKED`, last password change, password change required;
  - recent activity: the 10 latest `LOGIN_SUCCEEDED`, `LOGOUT`, and `LOGIN_FAILED` events of the account, newest first, each with time and event type (AQ-18). No IP and no other audit event types;
  - the optimistic-lock version used by UC-8 — **ASSUMPTION**.
- Never returned: password hash, token data.
- This is the only read of the audit table exposed by the API (security.md). It is read-only and limited to the selected account.
- **ASSUMPTION (proposed API):** `GET /api/v1/staff-accounts/{id}`.

## Acceptance Criteria

1. Admin sees every attribute listed above for an existing account.
2. Unknown ID returns `404`; non-Admin returns `403`.
3. Recent activity holds at most 10 entries, newest first, and only sign-in, sign-out, and failed sign-in events of that account.

## Test Considerations

- API test for the response shape and the absence of sensitive fields.

## Open Questions

- None. Decisions applied: AQ-07, AQ-12, AQ-17, AQ-18 in [`staff-account.md`](staff-account.md#decisions).
