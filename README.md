# Outpatient Clinic Management System

This repository is the engineering foundation for a small private outpatient clinic management system. Business capabilities are intentionally not defined here until they are confirmed and documented.

## Monorepo Structure

- `backend/` — Java 21 and Spring Boot backend.
- `frontend/` — React, TypeScript, and Vite frontend.
- `docs/` — source-of-truth documents for business context, architecture, interfaces, data, security, permissions, state transitions, and use cases.
- `AGENTS.md` — primary engineering instructions for coding agents.
- `CLAUDE.md` — Claude Code entry point to the shared instructions.

The intended architecture is a package-by-layer monolith backed by PostgreSQL.

## Local Development

Prerequisites: Java 21, Node.js 24.15+, PostgreSQL 17 (or Docker), and Docker for backend integration tests.

Start the development PostgreSQL instance from the repository root:

```shell
docker compose up -d postgres
docker compose ps
```

Docker Compose automatically reads the local `.env` file. Create it from `.env.example`, replace
all placeholder secrets, and keep it uncommitted. Spring Boot does not automatically import this
file: when running the backend from IntelliJ, load the repository-root `.env` through the Run
Configuration's EnvFile support, or copy the same values into its Environment variables field.

The Compose defaults create database `clinic`, user `clinic`, password `clinic`, and host port `5432`.
Run the backend with `DB_URL=jdbc:postgresql://localhost:5432/clinic`, `DB_USERNAME=clinic`, and
`DB_PASSWORD=clinic`. Override `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, or `POSTGRES_PORT`
in the shell when needed. Data is retained in the named volume `clinic_postgres_data`. Stop the
container with `docker compose down`; add `--volumes` only when you intentionally want to delete the
local database.

Backend environment variables:

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Yes | PostgreSQL connection (default URL `jdbc:postgresql://localhost:5432/clinic`) |
| `JWT_SIGNING_KEY` | Yes | Base64-encoded key of at least 256 bits; startup fails without it. Generate one per environment, e.g. `openssl rand -base64 32` |
| `APP_FRONTEND_ORIGIN` | Deployed environments | Origin allowed to call refresh/logout (default `http://localhost:5173`) |
| `REFRESH_COOKIE_SECURE` | Local HTTP only | Set to `false` only for plain-HTTP local development |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | First start | Creates the first Admin when none exists; the password must be changed at first login |
| `INSURANCE_BASE_SALARY` | No | Mức lương cơ sở in VND for BR-27; default `2340000`. An insurance visit whose total cost within insurance scope is below 15% of it is covered at 100% |
| `VISIT_END_OF_DAY_ENABLED`, `VISIT_END_OF_DAY_CRON`, `VISIT_END_OF_DAY_RUN_ON_STARTUP` | No | End-of-day job (BR-22) that closes unfinished visits of earlier days; defaults `true`, `0 5 0 * * *` (00:05, Asia/Ho_Chi_Minh) and `true` (also run once at startup to catch up). Safe when several backends share one database: a visit is closed once and later runs skip it |

| `SIGNING_SIMULATOR_KEYSTORE_PASSWORD` | To sign e-prescriptions | Password of the simulated signing CA's key file (UC-90); the `dev` profile sets a development value. Without it, signing answers "service unavailable" |
| `SIGNING_SIMULATOR_KEYSTORE`, `SIGNING_TRUSTED_CA_FILES` | No | Simulated CA key file (default `.signing-simulator/simulator.p12`, created on first use, git-ignored) and comma-separated PEM files of trusted CAs (the `dev` profile trusts `.signing-simulator/trusted-ca.pem`) |
| `SIGNING_SIMULATOR_FAILURE_MODE`, `SIGNING_SIMULATOR_RESPONSE_DELAY` | No | `NONE`, `UNAVAILABLE` or `BAD_SIGNATURE`, and a delay, to demonstrate UC-90 EF-15 and EF-16 |
| `NATIONAL_PRESCRIPTION_SIMULATOR_FAILURE_MODE`, `NATIONAL_PRESCRIPTION_SIMULATOR_RESPONSE_DELAY`, `NATIONAL_PRESCRIPTION_SIMULATOR_PRESCRIBER_CODES` | No | Simulated national prescription system: `NONE` or `NO_RESPONSE`, a delay, and the prescriber link codes it knows (empty accepts all) |

Run the backend with `./mvnw spring-boot:run` in `backend/` and the frontend with `npm run dev` in `frontend/` (the dev server proxies `/api` to `http://localhost:8080`). Add `-Dspring-boot.run.profiles=dev` to expose local API documentation.

### Sign an e-prescription in development (UC-90)

The digital signature uses a simulated remote-signing CA (test keys, no legal value; see
[`docs/uc-59-e-prescription.md`](docs/uc-59-e-prescription.md#signing-simulator)). With the `dev` profile:

1. As Admin, open the Doctor's account, click Edit and enter the 12-digit "Số định danh cá nhân"; the
   certificate is issued for that number.
2. Open `http://localhost:8080/dev/ca-sim/`, enter the Doctor's user name and a 6-digit PIN, and click
   "Cấp chứng thư". This issues a test certificate and registers it as the Doctor's signing certificate.
3. In the clinic app, save the prescription and click "Ký và gửi đơn", then "Ký số".
4. On the `/dev/ca-sim/` page (another tab or a phone on the same network), sign in with the certificate serial and
   the PIN, and click "Chấp thuận ký". The clinic app verifies the signature and sends the prescription.

With the `dev` profile, the backend API contract is available at `http://localhost:8080/swagger-ui.html`,
`http://localhost:8080/v3/api-docs`, and `http://localhost:8080/v3/api-docs.yaml`. OpenAPI remains disabled by
default outside development; set `OPENAPI_ENABLED=true` and `SWAGGER_UI_ENABLED=true` only in an environment where
the documentation should be exposed.

### Recover the bootstrap Admin

When the only Admin password is lost, stop the normal backend instance, then run it once in the operator-only recovery profile using the same environment variables:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=admin-recovery"
```

For a packaged deployment:

```text
java -jar clinic-backend-<version>.jar --spring.profiles.active=admin-recovery
```

The command opens no HTTP port. It generates and displays one temporary password, requires that password to be changed at the next login, clears login throttling, revokes every Admin session, writes `PASSWORD_RESET`, and exits. Do not redirect or retain its console output. It does not unlock a `LOCKED` Admin.

### Load the ICD-10 list

The ICD-10 catalog (TT 06/2026/TT-BYT, 15,844 codes) is not loaded by a migration. Load or refresh it once per database with the operator-only import profile, using the same environment variables as the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=icd10-import"
```

The command opens no HTTP port and exits. It validates the whole file first and writes nothing if any row is invalid; one run is one transaction. It adds new codes, updates changed ones, and retires codes no longer in the list (they stay for existing records but can no longer be selected). Running it again changes nothing.

| Variable | Default | Purpose |
| --- | --- | --- |
| `ICD10_IMPORT_FILE` | `classpath:db/reference/icd10.csv` | List to load; use `file:C:/path/icd10.csv` for another file |
| `ICD10_IMPORT_EFFECTIVE_FROM` | `2026-07-01` | Day the list takes effect; retired codes end the day before |
| `ICD10_IMPORT_SOURCE` | `TT 06/2026/TT-BYT` | Document recorded on each code |

A running backend serves ICD-10 search from memory and reloads the list every hour (`clinic.icd10.search-cache.ttl`, default `1h`), so it sees a new import within an hour; restart the backend to see it at once. Saving a code always checks the database.

### Load the long-term prescription disease list

The diseases whose medicines may be prescribed for more than 30 days (TT 26/2025/TT-BYT Phụ lục VII, 252 rows) are not loaded by a migration. Load them **after** the ICD-10 list, because each row's codes are expanded against the `icd10` table:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=long-term-disease-import"
```

The command opens no HTTP port and exits. It validates the whole file first and writes nothing if any row is invalid; one run is one transaction. Each row's codes are stored code by code, child codes included (`E10 - E14 (trừ E10.0, ...)` becomes every code from E10 to E14 except the excluded ones). Rows of the same document no longer in the file are retired. Running it again changes nothing.

Codes the appendix prints that are not in the ICD-10 list are skipped and listed at the end: `D75.2` (moved to D47.3 by WHO), `Z69.64` and `Z69.65` (do not exist). Rows 6.26 and 10.6 are printed without a code and match no diagnosis.

| Variable | Default | Purpose |
| --- | --- | --- |
| `LONG_TERM_DISEASE_IMPORT_FILE` | `classpath:db/reference/long-term-prescription-diseases.csv` | List to load |
| `LONG_TERM_DISEASE_IMPORT_EFFECTIVE_FROM` | `2025-07-01` | Day the list takes effect; retired rows end the day before |
| `LONG_TERM_DISEASE_IMPORT_SOURCE` | `TT 26/2025/TT-BYT` | Document recorded on each row |

The CSV keeps the printed code text in `ma_icd_goc` and the same codes in a fixed form in `ma_icd`: items separated by spaces, `E11` (code and child codes), `F70..F79` (range), `-E11.0` (excluded).

### Load the medicine catalog

The medicine catalog has no rows after the migrations. The bundled list `db/reference/medicine-catalog.csv` holds 66 common outpatient medicines taken from the public registry of the Drug Administration of Vietnam (dichvucong.dav.gov.vn, registrations in effect), including four special-control medicines for testing that they cannot be prescribed. Load it with:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=medicine-import"
```

The command opens no HTTP port and exits. It validates the whole file against BR-79 first and writes nothing if any row is invalid or a registration number already belongs to another medicine. New medicines are active; existing ones, matched by `ma_thuoc`, are updated but keep their status, and medicines not in the file are left alone (activating and deactivating stays with the Admin). Running it again changes nothing. Set `MEDICINE_IMPORT_FILE` (for example `file:C:/path/medicines.csv`) to load another list.

The columns are `ma_thuoc,so_dang_ky,ma_thuoc_bhyt,ten_thuoc,hoat_chat,ham_luong,duong_dung,don_vi_tinh,nhom_kiem_soat`; any further columns record the source and are not stored. In the bundled list `duong_dung` and `don_vi_tinh` are derived from the dosage form, the special-control groups are assigned by active ingredient, and `ma_thuoc_bhyt` is empty until its format is confirmed for UC-80 (I1-D45).

The CSV is extracted from the official appendix PDF with the scripts in `backend/tools/icd10/` (header `ma,ten,ma_cha,ghi_chu`; `ghi_chu` holds the column 18 and 24–29 rules as `|`-separated flags).

## Testing

- Backend: `./mvnw verify` in `backend/` (integration tests use PostgreSQL Testcontainers and are skipped when Docker is unavailable).
- Frontend: `npm run verify` in `frontend/` (lint, tests, build).

## Architecture

Read [`docs/architecture.md`](docs/architecture.md) for architectural decisions and boundaries. Engineering agents must also follow [`AGENTS.md`](AGENTS.md).

## Contribution Workflow

> **TODO:** Define branch, review, verification, and pull-request expectations before feature development begins.

Do not commit secrets or undocumented business assumptions. Record unresolved requirements as **ASSUMPTION** or **TBD** in the relevant source-of-truth document.
