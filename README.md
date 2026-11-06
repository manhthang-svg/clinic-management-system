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
| `VISIT_END_OF_DAY_ENABLED`, `VISIT_END_OF_DAY_CRON`, `VISIT_END_OF_DAY_RUN_ON_STARTUP` | No | End-of-day job (BR-22) that closes unfinished visits of earlier days; defaults `true`, `0 5 0 * * *` (00:05, Asia/Ho_Chi_Minh) and `true` (also run once at startup to catch up). Safe when several backends share one database: a visit is closed once and later runs skip it |

Run the backend with `./mvnw spring-boot:run` in `backend/` and the frontend with `npm run dev` in `frontend/` (the dev server proxies `/api` to `http://localhost:8080`). Add `-Dspring-boot.run.profiles=dev` to expose local API documentation.

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

The CSV is extracted from the official appendix PDF with the scripts in `backend/tools/icd10/` (header `ma,ten,ma_cha,ghi_chu`; `ghi_chu` holds the column 18 and 24–29 rules as `|`-separated flags).

## Testing

- Backend: `./mvnw verify` in `backend/` (integration tests use PostgreSQL Testcontainers and are skipped when Docker is unavailable).
- Frontend: `npm run verify` in `frontend/` (lint, tests, build).

## Architecture

Read [`docs/architecture.md`](docs/architecture.md) for architectural decisions and boundaries. Engineering agents must also follow [`AGENTS.md`](AGENTS.md).

## Contribution Workflow

> **TODO:** Define branch, review, verification, and pull-request expectations before feature development begins.

Do not commit secrets or undocumented business assumptions. Record unresolved requirements as **ASSUMPTION** or **TBD** in the relevant source-of-truth document.
