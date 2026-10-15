# Staff Account — Shared Model for UC-1 → UC-11

This file holds the account facts shared by the authentication, profile, and account-management use cases so that each specification does not repeat them. Use-case behavior stays in the individual `UC-<n>-*.md` files.

Sources: `usecases.xlsx` sheet `Mới` (UC-1 → UC-11), `Business_flow.xlsx` sheet `BR` (BR-01 → BR-05), Report 3 (§I.3.2 Entity Account, §I.3.3 Data Business Rules, §II.1–2 UC-01 → UC-11), [`../security.md`](../security.md), [`../permission-matrix.md`](../permission-matrix.md). Account statuses come from the superseded state-transition draft (A01–A03, marked "source" there) and agree with BR-01 and UC-10.

## Attributes

| Attribute | Rule | Editable by | Source |
| --- | --- | --- | --- |
| Technical ID | UUID; JWT subject ([`security.md`](../security.md#jwt-access-tokens)) | Nobody | security.md |
| Username | Required, unique, case-insensitive, immutable (BR-01, AQ-05) | Set at creation only | BR-01, UC-7 |
| Password | Stored only as an adaptive hash (security.md) | Owner (UC-3), Admin reset (UC-11) | BR-03 |
| Full name | Required; letters (including Vietnamese) and spaces only; at most 100 characters | Admin only (BR-04) | Report 3 UC-07, UC-08 |
| Academic title (học hàm học vị) | Optional free text, e.g. `BSCKI`, `ThS.BS` (AQ-13). At most 50 characters — **ASSUMPTION**: Report 3 sets no limit | Admin only (BR-04) | Report 3 UC-04, UC-07 |
| Date of birth | Optional; must be in the past (AQ-15) | Admin only (BR-04) | Report 3 UC-07, UC-08 |
| Gender | Optional (AQ-15). Values `MALE`, `FEMALE`, `OTHER` — **ASSUMPTION**: Report 3 lists no values; this matches the patient schema | Admin only (BR-04) | Report 3 UC-07, UC-08 |
| Address | Optional free text; at most 255 characters (AQ-15) | Admin only (BR-04) | Report 3 UC-08 |
| Phone number | Required; 10 digits starting with `0`; not unique (AQ-06) | Owner (UC-4) or Admin (UC-8) | BR-04, Report 3 UC-04, UC-07 |
| Email | Required; valid format; at most 100 characters; unique across all accounts, including `LOCKED` ones (AQ-06). Compared case-insensitively — **ASSUMPTION** | Owner (UC-4) or Admin (UC-8) | BR-04, Report 3 UC-04, UC-07 |
| Role | Exactly one of `ADMIN`, `RECEPTIONIST`, `DOCTOR`, `TECHNICIAN`, `CASHIER` | Admin only (UC-9) | BR-02 |
| Status | `ACTIVE` or `LOCKED` | Admin only (UC-10) | BR-01, UC-10 |
| Password change required | Set by account creation and Admin reset; cleared by UC-3 | System | AQ-01, AQ-02 |
| Room and department | Doctor and Technician: at most one room at a time; the department is the room's department and is not stored on the account. Other roles: none (AQ-12) | Via room assignment (UC-15) | BR-05, Report 3 §I.3.3, UC-15 |
| Created by | The Admin who created the account; empty for the bootstrap Admin (AQ-10) | System | Report 3 UC-06 |
| Last sign-in time | Time of the last successful login | System | Report 3 UC-05, UC-06 |
| Last password change | Time of the last UC-3 change or UC-11 reset | System | Report 3 UC-06 |
| Lock details | While `LOCKED`: the required reason, lock time, and locking Admin (UC-10) | System | Report 3 UC-06, UC-10 |

The complete attribute set is the list above (AQ-15). Do not add fields that are not listed here.

Phone number and email are required by UC-4, UC-7, and UC-8, but stay nullable in the database: the bootstrap Admin (AQ-10) is created from environment variables without them and must fill them in at their first profile edit — **ASSUMPTION**.

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

1. Username is unique, compared case-insensitively (BR-01, AQ-05). Email, when present, is unique, compared case-insensitively (AQ-06).
2. An account has exactly one role at all times (BR-02).
3. A `LOCKED` account cannot authenticate, whether by password login or token refresh (BR-01). **ASSUMPTION:** the refresh rejection follows from BR-01; otherwise a locked user could continue working.
4. A password is never stored, logged, or returned in plain text (security.md).
5. At least one `ACTIVE` account with role `ADMIN` exists once the system has been bootstrapped (AQ-07, AQ-10).
6. Only a Doctor or Technician can have a room assignment, and at most one active assignment at a time (BR-05, AQ-12).
7. The only `ADMIN` account is the one created by the bootstrap (AQ-10, AQ-17). No use case creates an Admin, changes an account to Admin, or changes an Admin's role.

## Decisions

Approved on 14/10/2026: the recommendations for AQ-01, AQ-02, AQ-03, AQ-05, AQ-07, AQ-08, AQ-10, and UQ-1 (UC-1) were accepted by the team. AQ-04, AQ-11, and AQ-14 follow from the security decisions in [`../security.md`](../security.md).

| ID | Decision |
| --- | --- |
| AQ-01 | On creation the system generates a 12-character one-time password, shows it to Admin once, and marks the account "password change required". At first login the user must change it (UC-3) before using any other function. |
| AQ-02 | Admin reset uses the same mechanism as AQ-01. |
| AQ-03 | Password: 8–64 characters, at most 72 bytes in UTF-8, no composition rules, must differ from the current password. |
| AQ-04 | 5 consecutive failed logins block password login for that account for 15 minutes. This does not change the status and is not visible as a separate error. |
| AQ-05 | Username: case-insensitive (stored in lowercase), immutable after creation. Allowed format: 3–50 characters from `a–z`, `0–9`, `.`, `_`, `-` — **ASSUMPTION**: the format was not specified; this keeps usernames typeable and unambiguous. |
| AQ-07 | Forbidden, rejected with `409`: an Admin locking their own account, an Admin changing their own role, and any lock or role change that would leave no `ACTIVE` Admin. The role-change parts are superseded by AQ-17. |
| AQ-08 | Lock, role change, and Admin reset revoke all refresh-token families of the account. Changing one's own password revokes all other families and keeps the current one. Access tokens already issued expire within 15 minutes. |
| AQ-10 | At startup, if no account with role `ADMIN` exists and `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` are set, the system creates that Admin with "password change required". If an Admin exists, the variables are ignored. Nothing is created by a migration. |
| AQ-11 | Account events are written to the security audit table (event list in security.md). |
| AQ-14 | Sessions are bounded only by token lifetimes: 2-hour idle timeout, 12-hour absolute lifetime. |
| UQ-1 | A locked account receives the same generic `401` as wrong credentials. |

Closed on 15/10/2026 from Report 3 (team decision to use Report 3 for open questions; see [`../business-context.md`](../business-context.md#sources)):

| ID | Decision | Report 3 source |
| --- | --- | --- |
| AQ-06 | Phone number and email are required. Phone: 10 digits starting with `0`. Email: valid format, at most 100 characters, unique across all accounts. Phone is not unique. | UC-04 step 6, UC-07 steps 5 and 7 |
| AQ-09 | A role change is rejected while the account has a visit `In Examination` or a paraclinical order `In Progress` assigned to it. Room assignments alone do not block it. When the role changes away from Doctor or Technician, the active room assignment is removed in the same transaction. | UC-09 steps 5–8, EF-03 |
| AQ-12 | Doctors and Technicians have at most one room at a time, so the list and detail show one room and its department, or "Not assigned". Admin, Receptionist, and Cashier have no room or department ("Not applicable"). The department filter matches staff whose active room belongs to that department. | §I.3.3 Staff department assignment, UC-05, UC-06, UC-15 |
| AQ-13 | Academic title is optional free text. | UC-04, UC-07 |
| AQ-15 | Personal details are full name, academic title, date of birth, gender, phone, email, and address. No citizen ID. | UC-04, UC-07, UC-08 |

Decided by the team on 15/10/2026:

| ID | Decision |
| --- | --- |
| AQ-16 | On any role change of a Doctor or Technician, including Doctor ↔ Technician, the active room assignment is removed, because a Doctor works in an examination room and a Technician in a paraclinical room. Admin then assigns a room of the right type (UC-15). |
| AQ-17 | No Admin can change the role of an account whose role is `ADMIN`, including their own; rejected with `409`. This replaces the AQ-07 rules for role changes: since an Admin never loses the role, a role change can no longer leave the system without an Admin. Changing another account's role to `ADMIN` (UC-9) and creating an account with role `ADMIN` (UC-7) are also rejected with `409`. The bootstrap (AQ-10) is the only way an Admin account comes into existence; because it runs only when no Admin exists, the system has exactly one Admin. No work-in-progress guard applies to Admin, Receptionist, or Cashier, because only Doctor and Technician work is defined. |
| AQ-18 | Account detail (UC-6) shows the 10 latest `LOGIN_SUCCEEDED`, `LOGOUT`, and `LOGIN_FAILED` events of the account, with time and event type. The workstation IP is not shown because the audit table does not store it (security.md). |
| AQ-19 | Admin recovery: because the bootstrap Admin is the only Admin, an operator-run maintenance command resets the Admin password when the Admin forgets it or leaves. It is not exposed through the API and needs shell access to the server. It generates a one-time password (same rule as AQ-01), shows it once to the operator, sets "password change required", resets the failed-login counter and throttle, revokes all sessions (AQ-08), and writes `PASSWORD_RESET` to the audit table with an empty actor. It does not unlock a `LOCKED` account. The command form (Spring profile, CLI argument, or separate entry point) is decided when it is implemented. |

## Open Questions

None for UC-1 → UC-11.

## Report 3 Differences Not Adopted

These Report 3 statements differ from team decisions and are **not** applied. Report 3 should be corrected to match.

| Topic | Report 3 | Applied rule |
| --- | --- | --- |
| Failed sign-ins | 5 failures set the account to Locked for 30 minutes with a distinct message (MSG11); a background job unlocks it (JOB-01 says 10 minutes; UC-01 refers to "job 7") | AQ-04: password login is throttled for 15 minutes, the status does not change, and the response stays the generic `401` |
| Session timeout | 30 minutes of inactivity | AQ-14: 15-minute access token, 2-hour refresh idle timeout, 12-hour absolute session |
| Password rule | At least 8 characters including letters and digits | AQ-03: 8–64 characters, at most 72 UTF-8 bytes, no composition rules |
| Initial password (UC-07) | Admin types it or clicks Generate | AQ-01: always generated by the system, 12 characters, shown once |
| Admin role on creation (UC-07) | Admin can create an account with role Admin | AQ-17: only the bootstrap creates an Admin |
| Username format (UC-07) | 4–30 characters of `a–z 0–9 . _` | AQ-05: 3–50 characters of `a–z 0–9 . _ -` (already implemented) |
| Wrong current password, fifth time (UC-03 EF-03) | Ends the session and locks the account | AQ-04 throttling applies; the session is not ended |
| Locked sign-in message (UC-01 EF-04) | Distinct message MSG10 for a locked account | UQ-1: same generic `401` as wrong credentials |
| Audit content | Workstation IP; old and new values of changed fields (UC-04, UC-08) | security.md audit fields: no IP, no old/new values |
| Recent activity on account detail (UC-06) | Each entry shows time, action, and IP | AQ-18: time and event type only; no IP |
| Audit retention (JOB-02) | Deleted after 90 days | security.md: append-only until legal review sets a retention period |
