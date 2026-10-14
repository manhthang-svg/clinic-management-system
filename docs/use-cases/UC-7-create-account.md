# UC-7 — Create Account

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; BR-01, BR-02 |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin creates a new staff account with login information and personal details.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.

## Primary Flow

1. Admin enters username, full name, academic title, phone number, email, and role.
2. The system validates the input and checks that the username is not taken (BR-01).
3. The system sets the initial password (mechanism **TBD**, AQ-01).
4. The system creates the account with status `ACTIVE` (A01).
5. The system returns the new account and, if AQ-01 chooses a generated password, shows it to Admin once.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Required field missing or invalid format | `400 REQUEST_VALIDATION_FAILED` |
| Username already exists | `409`. **ASSUMPTION:** code `USERNAME_ALREADY_EXISTS` |
| Role not one of the five roles | `400` |
| Two Admins create the same username at the same time | One succeeds; the other gets `409` from the unique constraint |

## Business Rules and Permissions

- Admin only.
- BR-01: unique username.
- BR-02: exactly one role.
- Room assignment is a separate use case (UC-15). A new Doctor or Technician has no room until assigned.

## State Changes and Transaction

- Creates the account in `ACTIVE` in one transaction.
- Database: unique constraint on the username (normalized form per AQ-05).

## Inputs and Outputs

- Input: `username`, `fullName`, `academicTitle`, `phoneNumber`, `email`, `role`; plus `initialPassword` only if AQ-01 chooses an Admin-typed password.
- Output: `201 Created` with `Location` and the account detail (UC-6 shape).
- **ASSUMPTION (proposed API):** `POST /api/v1/staff-accounts`.

## Acceptance Criteria

1. A created account appears in UC-5 with status `ACTIVE` and can log in (UC-1).
2. A duplicate username is rejected and no account is created.
3. The initial password is not logged and is never readable again after creation.

## Test Considerations

- Domain test for invariants; PostgreSQL integration test for the unique constraint under concurrency.

## Open Questions

- AQ-01, AQ-03, AQ-05, AQ-06, AQ-10, AQ-11, AQ-13, AQ-15 in [`staff-account.md`](staff-account.md#open-questions).
