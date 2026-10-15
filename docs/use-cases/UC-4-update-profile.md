# UC-4 — Update Profile

| Item | Value |
| --- | --- |
| Feature | Profile Management |
| Actors | Admin, Receptionist, Doctor, Technician, Cashier |
| Source | `usecases.xlsx` sheet `Mới`; BR-04; Report 3 UC-04 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

A staff member views their own profile and updates their own contact information (phone number, email).

## Trigger and Preconditions

- The user is authenticated and the account is `ACTIVE`.

## Primary Flow

1. The user opens "My profile".
2. The system shows, read-only: username, role, room and department (Doctor, Technician; AQ-12), status, created date, last sign-in time, full name, academic title, date of birth, gender, address. Editable: phone number and email.
3. The user edits phone number and/or email and saves.
4. The system validates the fields (AQ-06): both required, phone 10 digits starting with `0`, email valid, at most 100 characters, and not used by another account. It saves the changes and writes `ACCOUNT_UPDATED` to the audit table.
5. The system shows the updated profile.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Phone or email missing, invalid format, or too long | `400 REQUEST_VALIDATION_FAILED` |
| Request tries to change any other field (name, title, role, username, status) | `400`; the field is not accepted. **ASSUMPTION:** reject instead of silently ignoring (api-conventions) |
| Email already used by another account | `409`. **ASSUMPTION:** code `EMAIL_ALREADY_EXISTS`. A duplicate phone number is allowed (AQ-06) |
| No field changed | `200` with the unchanged profile; no audit event — **ASSUMPTION** |

## Business Rules and Permissions

- BR-04: staff may update only their own phone number and email. Other fields are Admin-only (UC-8, UC-9).
- Own account only. The account ID comes from the token.

## State Changes and Transaction

- Updates contact fields in one transaction. No status change.
- **ASSUMPTION:** concurrent edits by the owner (UC-4) and Admin (UC-8) on the same account use optimistic locking (`409` on conflict).

## Inputs and Outputs

- View output: the attributes in step 2. Password hash and technical fields are never returned.
- Update input: `phoneNumber`, `email`.
- **ASSUMPTION (proposed API):** `GET /api/v1/me`, `PUT /api/v1/me/contact`.

## Acceptance Criteria

1. A user can change their own phone number and email.
2. An email already used by another account is rejected and nothing changes.
3. A user cannot change their own name, title, date of birth, gender, address, role, username, or status through this use case.
4. A user cannot view or change another account's profile through this use case.

## Test Considerations

- API tests for field validation and the rejected-field rule.
- Authorization test: the identity always comes from the token.

## Open Questions

- None. Decisions applied: AQ-06, AQ-12, AQ-15 in [`staff-account.md`](staff-account.md#decisions).
- A changed email is not verified: neither the workbooks nor Report 3 define a verification flow.
