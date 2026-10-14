# Staff Account — Shared Model for UC-1 → UC-11

This file holds the account facts shared by the authentication, profile, and account-management use cases so that each specification does not repeat them. Use-case behavior stays in the individual `UC-<n>-*.md` files.

Sources: `usecases.xlsx` sheet `Mới` (UC-1 → UC-11), `Business_flow.xlsx` sheet `BR` (BR-01 → BR-05), [`../security.md`](../security.md), [`../permission-matrix.md`](../permission-matrix.md). Account statuses come from the superseded state-transition draft (A01–A03, marked "source" there) and agree with BR-01 and UC-10.

## Attributes

| Attribute | Rule | Editable by | Source |
| --- | --- | --- | --- |
| Technical ID | UUID; JWT subject ([`security.md`](../security.md#jwt-access-tokens)) | Nobody | security.md |
| Username | Required, unique, case-insensitive, immutable (BR-01, AQ-05) | Set at creation only | BR-01, UC-7 |
| Password | Stored only as an adaptive hash (security.md) | Owner (UC-3), Admin reset (UC-11) | BR-03 |
| Full name | Required — **ASSUMPTION** | Admin only (BR-04) | UC-7, UC-8 |
| Academic title (học hàm học vị) | Optional — **ASSUMPTION** | Admin only (BR-04) | UC-8 |
| Phone number | See AQ-06 | Owner (UC-4) or Admin (UC-8) | BR-04 |
| Email | See AQ-06 | Owner (UC-4) or Admin (UC-8) | BR-04 |
| Role | Exactly one of `ADMIN`, `RECEPTIONIST`, `DOCTOR`, `TECHNICIAN`, `CASHIER` | Admin only (UC-9) | BR-02 |
| Status | `ACTIVE` or `LOCKED` | Admin only (UC-10) | BR-01, UC-10 |
| Password change required | Set by account creation and Admin reset; cleared by UC-3 | System | AQ-01, AQ-02 |
| Department | Derived from the assigned room; not stored on the account | Via room assignment (UC-15, out of this batch) | BR-05 |

Other "personal details" mentioned by UC-7 are **TBD** (AQ-15). Do not add fields that are not listed here.

## Status Lifecycle

| ID | From | Event | Guard / effect | To | UC |
| --- | --- | --- | --- | --- | --- |
| A01 | — | Admin creates the account | Exactly one role assigned | `ACTIVE` | UC-7 |
| A02 | `ACTIVE` | Admin locks the account | Account can no longer log in (BR-01). Not the caller's own account and not the last active Admin (AQ-07). All sessions revoked (AQ-08) | `LOCKED` | UC-10 |
| A03 | `LOCKED` | Admin unlocks the account | — | `ACTIVE` | UC-10 |

- Status changes are explicit domain behavior (`lock`, `unlock`), not field assignments.
- Locking an already locked account, or unlocking an active one, is rejected as an invalid transition (`409`). **ASSUMPTION:** an idempotent no-op was not chosen because the UI should reflect a stale view.
- There is no delete use case. Accounts are never deleted; locking is the way to remove access.
- Password reset, profile edits, and role changes do not change the status.
- The persisted status codes and enum names are **TBD** with the database naming decision. The Vietnamese codes `HOAT_DONG` / `DA_KHOA` from the superseded draft are not adopted.
- This table must move to [`../state-transitions.md`](../state-transitions.md) when that document is rewritten.

## Invariants

1. Username is unique, compared case-insensitively (BR-01, AQ-05).
2. An account has exactly one role at all times (BR-02).
3. A `LOCKED` account cannot authenticate, whether by password login or token refresh (BR-01). **ASSUMPTION:** the refresh rejection follows from BR-01; otherwise a locked user could continue working.
4. A password is never stored, logged, or returned in plain text (security.md).
5. At least one `ACTIVE` account with role `ADMIN` exists once the system has been bootstrapped (AQ-07, AQ-10).

## Decisions

Approved on 14/10/2026: the recommendations for AQ-01, AQ-02, AQ-03, AQ-05, AQ-07, AQ-08, AQ-10, and UQ-1 (UC-1) were accepted by the team. AQ-04, AQ-11, and AQ-14 follow from the security decisions in [`../security.md`](../security.md).

| ID | Decision |
| --- | --- |
| AQ-01 | On creation the system generates a 12-character one-time password, shows it to Admin once, and marks the account "password change required". At first login the user must change it (UC-3) before using any other function. |
| AQ-02 | Admin reset uses the same mechanism as AQ-01. |
| AQ-03 | Password: 8–64 characters, at most 72 bytes in UTF-8, no composition rules, must differ from the current password. |
| AQ-04 | 5 consecutive failed logins block password login for that account for 15 minutes. This does not change the status and is not visible as a separate error. |
| AQ-05 | Username: case-insensitive (stored in lowercase), immutable after creation. Allowed format: 3–50 characters from `a–z`, `0–9`, `.`, `_`, `-` — **ASSUMPTION**: the format was not specified; this keeps usernames typeable and unambiguous. |
| AQ-07 | Forbidden, rejected with `409`: an Admin locking their own account, an Admin changing their own role, and any lock or role change that would leave no `ACTIVE` Admin. |
| AQ-08 | Lock, role change, and Admin reset revoke all refresh-token families of the account. Changing one's own password revokes all other families and keeps the current one. Access tokens already issued expire within 15 minutes. |
| AQ-10 | At startup, if no account with role `ADMIN` exists and `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` are set, the system creates that Admin with "password change required". If an Admin exists, the variables are ignored. Nothing is created by a migration. |
| AQ-11 | Account events are written to the security audit table (event list in security.md). |
| AQ-14 | Sessions are bounded only by token lifetimes: 2-hour idle timeout, 12-hour absolute lifetime. |
| UQ-1 | A locked account receives the same generic `401` as wrong credentials. |

## Open Questions

Still **TBD**; each blocks only the use cases listed.

| ID | Question | Recommendation (not decided) | Blocks |
| --- | --- | --- | --- |
| AQ-06 | Are phone number and email required? Must they be unique? | Optional, not unique | UC-4, UC-7, UC-8 |
| AQ-09 | Can the role of a Doctor or Technician who has room assignments or in-progress patients be changed? What happens to the assignments? | Reject while assigned to rooms | UC-9 |
| AQ-12 | How is department shown and filtered for staff with several room assignments, and for roles without rooms (Admin, Receptionist, Cashier)? | List all departments; empty for roles without rooms | UC-5, UC-6 |
| AQ-13 | Is the academic title free text or chosen from a catalog? | Free text | UC-7, UC-8 |
| AQ-15 | Which other "personal details" does UC-7 require (e.g. date of birth, gender, citizen ID)? | Only the attributes above until confirmed | UC-7, UC-8 |
