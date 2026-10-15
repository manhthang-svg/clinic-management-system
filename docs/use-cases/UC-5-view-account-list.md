# UC-5 — View Account List

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; Report 3 UC-05; [`../api-conventions.md`](../api-conventions.md#pagination-sorting-and-filtering) |
| Shared model | [`staff-account.md`](staff-account.md) |

## Goal

Admin views the list of staff accounts and searches or filters them by name, role, department, or status.

## Trigger and Preconditions

- The caller is authenticated with role `ADMIN`.

## Primary Flow

1. Admin opens the account list.
2. The system returns the first page of accounts.
3. Admin enters a search term and/or chooses filters.
4. The system returns the matching accounts, paginated.

## Filters and Sorting

| Parameter | Meaning |
| --- | --- |
| `q` | Partial match on username, full name, phone number, or email, ignoring case and Vietnamese diacritics (Report 3 UC-05 step 4) |
| `role` | One of the five roles |
| `departmentId` | Doctors and Technicians whose active room belongs to that department (BR-05, AQ-12) |
| `status` | `ACTIVE` or `LOCKED` |
| `sort` | Allow-list: `username`, `fullName`, `role`, `status`, `lastLoginAt`, `createdAt`. Default `createdAt,desc`, newest first (Report 3 UC-05) |

Pagination follows api-conventions (`page`, `size` default 20, at most 100). The UI offers 10, 20, and 50 rows per page.

The search term and the filters combine with AND.

## Alternate and Error Flows

| Case | Result |
| --- | --- |
| Caller is not Admin | `403` |
| Unknown filter value or sort field | `400` |
| No match | `200` with an empty page |

## Business Rules and Permissions

- Admin only (permission matrix, UC-5).

## State Changes and Transaction

- Read-only transaction.

## Inputs and Outputs

- Output per row: ID, username, full name, role, room, department, phone number, email, status, last sign-in time (Report 3 UC-05).
- **ASSUMPTION (proposed API):** `GET /api/v1/staff-accounts`.

## Acceptance Criteria

1. Each filter narrows the result correctly, and filters combine with AND.
2. Searching `nguyen van a` or `NGUYEN` finds `Nguyễn Văn A`.
3. Non-Admin roles receive `403`.
4. No password hash or token data appears in the response.

## Test Considerations

- Repository/integration test against PostgreSQL for diacritic-insensitive search.
- API test for the sort allow-list and the page-size limit.

## Open Questions

- Decisions applied: AQ-12 in [`staff-account.md`](staff-account.md#decisions).
- **TBD (technical):** how to normalize for diacritic-insensitive search, for example the PostgreSQL `unaccent` extension or a normalized search column. `unaccent` must also map `đ` to `d`. Decided when UC-5 is designed.
