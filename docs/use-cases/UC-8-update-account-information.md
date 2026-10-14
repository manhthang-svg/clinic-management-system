# UC-8 — Update Account Information

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-04 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin updates the personal and professional information of a staff account: full name, academic title, phone number, email.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.
- The account exists. Its status may be `ACTIVE` or `LOCKED` — **ASSUMPTION**: the source does not restrict this.

## Primary Flow

1. Admin opens the account detail (UC-6) and chooses "Edit".
2. Admin changes full name, academic title, phone number, and/or email.
3. The system validates and saves the changes.
4. The system returns the updated detail.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Invalid input | `400 REQUEST_VALIDATION_FAILED` |
| Account not found | `404` |
| The account was changed by someone else since it was loaded | `409` (optimistic lock) — **ASSUMPTION** |
| Request includes username, role, status, or password | `400`; these have their own use cases (UC-9, UC-10, UC-11) |

## Business Rules and Permissions

- Admin only. BR-04 reserves full name and academic title for Admin.
- Role, status, and password are not edited here.

## State Changes and Transaction

- Updates the four fields in one transaction. No status change.

## Inputs and Outputs

- Input: `fullName`, `academicTitle`, `phoneNumber`, `email`, `version`.
- Output: the account detail (UC-6 shape).
- **ASSUMPTION (proposed API):** `PUT /api/v1/staff-accounts/{id}`. The resource includes only these four fields.

## Acceptance Criteria

1. Admin can change all four fields, and the owner sees the change in UC-4.
2. A stale `version` is rejected with `409` and nothing changes.
3. Non-Admin roles receive `403`.

## Test Considerations

- API test for field restrictions; integration test for optimistic locking.

## Open Questions

- AQ-06, AQ-11, AQ-13 in [`staff-account.md`](staff-account.md#open-questions).
