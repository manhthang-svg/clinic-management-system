# UC-6 — View Account Detail

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới` |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin views the detailed information of a selected staff account.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- Admin selects an account, usually from the list in UC-5.

## Primary Flow

1. Admin selects an account.
2. The system returns its details.
3. From this screen Admin can start UC-8, UC-9, UC-10, or UC-11.

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

- Output: ID, username, full name, academic title, phone number, email, role, status, assigned rooms and departments (BR-05), and the optimistic-lock version used by UC-8 — **ASSUMPTION**.
- Never returned: password hash, token data.
- Creation and last-change information depend on the audit decision (AQ-11).
- **ASSUMPTION (proposed API):** `GET /api/v1/staff-accounts/{id}`.

## Acceptance Criteria

1. Admin sees every attribute listed above for an existing account.
2. Unknown ID returns `404`; non-Admin returns `403`.

## Test Considerations

- API test for the response shape and the absence of sensitive fields.

## Open Questions

- AQ-11, AQ-12 in [`staff-account.md`](staff-account.md#open-questions).
