# UC-5 — View Account List

| Item | Value |
| --- | --- |
| Feature | Account Management |
| Actors | Admin |
| Source | `usecases.xlsx` sheet `Mới`; [`../api-conventions.md`](../api-conventions.md#pagination-sorting-and-filtering) |
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
| `q` | Matches full name or username. Partial match, ignoring case and Vietnamese diacritics — **ASSUMPTION** |
| `role` | One of the five roles |
| `departmentId` | Staff assigned to a room of that department (BR-05; AQ-12) |
| `status` | `ACTIVE` or `LOCKED` |
| `sort` | Allow-list: `fullName`, `username`, `role`, `status`. Default `fullName,asc` — **ASSUMPTION** |

Pagination follows api-conventions (`page`, `size` ≤ 100).

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

- Output per row: ID, username, full name, role, department(s), status. Phone and email are optional in the list — **ASSUMPTION:** include them, since Admin may edit them.
- **ASSUMPTION (proposed API):** `GET /api/v1/staff-accounts`.

## Acceptance Criteria

1. Each filter narrows the result correctly, and filters combine with AND.
2. Non-Admin roles receive `403`.
3. No password hash or token data appears in the response.

## Test Considerations

- Repository/integration test against PostgreSQL for diacritic-insensitive search, if confirmed.
- API test for the sort allow-list and the page-size limit.

## Open Questions

- AQ-12 in [`staff-account.md`](staff-account.md#open-questions).
- **TBD:** Is diacritic-insensitive search required? It affects the PostgreSQL extension or index choice (`unaccent`).
