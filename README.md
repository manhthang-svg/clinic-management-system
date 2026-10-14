# Outpatient Clinic Management System

This repository is the engineering foundation for a small private outpatient clinic management system. Business capabilities are intentionally not defined here until they are confirmed and documented.

## Monorepo Structure

- `backend/` — Java 21 and Spring Boot backend.
- `frontend/` — React, TypeScript, and Vite frontend.
- `docs/` — source-of-truth documents for business context, architecture, interfaces, data, security, permissions, state transitions, and use cases.
- `AGENTS.md` — primary engineering instructions for coding agents.
- `CLAUDE.md` — Claude Code entry point to the shared instructions.

The intended architecture is a package-by-feature modular monolith backed by PostgreSQL.

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

Run the backend with `./mvnw spring-boot:run` in `backend/`, and the frontend with `npm run dev` in `frontend/` (the dev server proxies `/api` to `http://localhost:8080`).

## Testing

- Backend: `./mvnw verify` in `backend/` (integration tests use PostgreSQL Testcontainers and are skipped when Docker is unavailable).
- Frontend: `npm run verify` in `frontend/` (lint, tests, build).

## Architecture

Read [`docs/architecture.md`](docs/architecture.md) for architectural decisions and boundaries. Engineering agents must also follow [`AGENTS.md`](AGENTS.md).

## Contribution Workflow

> **TODO:** Define branch, review, verification, and pull-request expectations before feature development begins.

Do not commit secrets or undocumented business assumptions. Record unresolved requirements as **ASSUMPTION** or **TBD** in the relevant source-of-truth document.
