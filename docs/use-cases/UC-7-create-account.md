# UC-7 — Create Account

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-01, BR-02; Report 3 UC-07 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin creates a new staff account with login information and personal details.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.

## Primary Flow

1. Admin enters username, role, full name, academic title, date of birth, gender, phone number, email, and address.
2. The system validates the input against the rules in [`staff-account.md`](staff-account.md#attributes) and checks that the username and the email are not used by any account, including `LOCKED` ones (BR-01, AQ-06).
3. The system generates a 12-character one-time password and marks the account "password change required" (AQ-01).
4. The system creates the account with status `ACTIVE` (A01).
5. The system writes `ACCOUNT_CREATED` to the audit table and returns the new account together with the one-time password, which is shown to Admin once and never again.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Required field missing or invalid format, including a username outside 3–50 characters of `a–z 0–9 . _ -` (AQ-05) | `400 REQUEST_VALIDATION_FAILED` |
| Username already exists | `409`. **ASSUMPTION:** code `USERNAME_ALREADY_EXISTS` |
| Email already used by another account | `409`. **ASSUMPTION:** code `EMAIL_ALREADY_EXISTS` |
| Role not one of the five roles | `400` |
| Role is `ADMIN` | `409`; only the bootstrap creates an Admin (AQ-17). **ASSUMPTION:** code `ADMIN_ROLE_IMMUTABLE` |
| Two Admins create the same username or email at the same time | One succeeds; the other gets `409` from the unique constraint |

## Business Rules and Permissions

- Admin only.
- BR-01: unique username.
- BR-02: exactly one role, chosen from `RECEPTIONIST`, `DOCTOR`, `TECHNICIAN`, `CASHIER`. `ADMIN` is not offered (AQ-17).
- Room assignment is a separate use case (UC-15). A new Doctor or Technician has no room until assigned; the UI then offers UC-15 (Report 3 UC-07 AF-02).
- "Save & New" (Report 3 UC-07 AF-03) is a UI convenience that calls the same API.

## State Changes and Transaction

- Creates the account in `ACTIVE` in one transaction.
- Database: unique constraint on the lowercase username (AQ-05) and on the lowercase email (AQ-06).
- Records the creating Admin as "created by".

## Inputs and Outputs

- Input: `username`, `role`, `fullName`, `academicTitle`, `dateOfBirth`, `gender`, `phoneNumber`, `email`, `address`.
- Output: `201 Created` with `Location`, the account detail (UC-6 shape), and `temporaryPassword`.
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts`.

## Acceptance Criteria

1. A created account appears in UC-5 with status `ACTIVE`, can log in with the one-time password (UC-1), and must change it before doing anything else.
2. A duplicate username or email is rejected and no account is created.
3. A request with role `ADMIN` is rejected and no account is created.
4. The initial password is not logged and is never readable again after creation.

## Test Considerations

- Domain test for invariants; PostgreSQL integration test for the unique constraint under concurrency.

## Open Questions

- None.
- Decisions applied: AQ-01, AQ-05, AQ-06, AQ-10, AQ-11, AQ-13, AQ-15, AQ-17 in [`staff-account.md`](staff-account.md#decisions).
