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
| `CLOUDINARY_URL` | For UC-67 result images | `cloudinary://<api_key>:<api_secret>@<cloud_name>` of the Cloudinary account (secret, never commit). Without it uploads answer `FILE_STORAGE_NOT_CONFIGURED`; `CLOUDINARY_FOLDER` sets the folder (default `clinic/paraclinical`) |
| `REFRESH_COOKIE_SECURE` | Local HTTP only | Set to `false` only for plain-HTTP local development |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | First start | Creates the first Admin when none exists; the password must be changed at first login |
| `INSURANCE_BASE_SALARY` | No | Mức lương cơ sở in VND for BR-27; default `2530000`. An insurance visit whose total cost within insurance scope is below 15% of it is covered at 100% |
| `VISIT_END_OF_DAY_ENABLED`, `VISIT_END_OF_DAY_CRON`, `VISIT_END_OF_DAY_RUN_ON_STARTUP` | No | End-of-day job (BR-22) that closes unfinished visits of earlier days; defaults `true`, `0 5 0 * * *` (00:05, Asia/Ho_Chi_Minh) and `true` (also run once at startup to catch up). Safe when several backends share one database: a visit is closed once and later runs skip it |

| `FOLLOW_UP_REMINDER_ENABLED`, `FOLLOW_UP_REMINDER_DAYS_BEFORE`, `FOLLOW_UP_REMINDER_CRON` | For UC-75 reminder emails | Turns on the job that emails each patient with an email address one reminder before the follow-up date; defaults `false`, `1` (day before) and `0 0 8,14,20 * * *` (08:00, 14:00, 20:00 Asia/Ho_Chi_Minh; later runs retry a failed send, a sent reminder is never repeated). See [Follow-up reminder emails](#follow-up-reminder-emails-uc-75) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | For UC-75 reminder emails | Free SMTP server. Defaults `localhost`, `1025`, no account, no TLS (Mailpit). Gmail: `smtp.gmail.com`, `587`, the Gmail address, its App Password (secret, never commit), the same address, `true`, `true` |
| `SIGNING_SIMULATOR_KEYSTORE_PASSWORD` | To sign e-prescriptions | Password of the simulated signing CA's key file (UC-90); the `dev` profile sets a development value. Without it, signing answers "service unavailable" |
| `CLINIC_HEALTH_DEPARTMENT` | No | "Sở Y tế" printed in the header of the medical-record PDF, form 15/BV1 (UC-53); default empty |
| `CLINIC_ARCHIVE_ROOT` | No | Directory of the write-once archive packages of closed medical records (UC-58, BR-126); default `.clinic-archive` (git-ignored). Back it up, with one off-site copy (CV 365 mục III.2) |
| `SIGNING_SIMULATOR_KEYSTORE`, `SIGNING_TRUSTED_CA_FILES` | No | Simulated CA key file (default `.signing-simulator/simulator.p12`, created on first use, git-ignored) and comma-separated PEM files of trusted CAs (the `dev` profile trusts `.signing-simulator/trusted-ca.pem`) |
| `SIGNING_SIMULATOR_FAILURE_MODE`, `SIGNING_SIMULATOR_RESPONSE_DELAY` | No | `NONE`, `UNAVAILABLE` or `BAD_SIGNATURE`, and a delay, to demonstrate UC-90 EF-15 and EF-16 |
| `NATIONAL_PRESCRIPTION_SIMULATOR_FAILURE_MODE`, `NATIONAL_PRESCRIPTION_SIMULATOR_RESPONSE_DELAY`, `NATIONAL_PRESCRIPTION_SIMULATOR_PRESCRIBER_CODES` | No | Simulated national prescription system: `NONE` or `NO_RESPONSE`, a delay, and the prescriber link codes it knows (empty accepts all) |

Run the backend with `./mvnw spring-boot:run` in `backend/` and the frontend with `npm run dev` in `frontend/` (the dev server proxies `/api` to `http://localhost:8080`). Add `-Dspring-boot.run.profiles=dev` to expose local API documentation.

### Sign an e-prescription in development (UC-90)

The digital signature uses a simulated remote-signing CA (test keys, no legal value; see
[`docs/uc-59-e-prescription.md`](docs/uc-59-e-prescription.md#signing-simulator)). With the `dev` profile:

1. As Admin, open the Doctor's account, click Edit and enter the 12-digit "Số định danh cá nhân"; the
   certificate is issued for that number.
2. Open `http://localhost:8080/dev/ca-sim/`, enter the Doctor's 12-digit personal identification number and a
   6-digit PIN, and click "Cấp chứng thư". This issues a test certificate and registers it as the signing
   certificate of the Doctor whose account holds that number.
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

### Follow-up reminder emails (UC-75)

The backend emails a reminder of the follow-up appointment (UC-61) to the patient's email address, once, the day before
the appointment. It uses only free tools: Spring Boot Mail over SMTP, Mailpit on a developer machine and a personal
Gmail account with an App Password in a deployment (free, about 500 recipients a day). No paid email service is used.
The email carries the date, slip number, Doctor and clinic contact, never the diagnosis or advice. Patients without an
email address get no reminder; the "Hẹn tái khám" tab tells the Doctor which case applies.

**Local test with Mailpit** (nothing leaves the machine):

```powershell
docker compose up -d mailpit
```

Run the backend with `FOLLOW_UP_REMINDER_ENABLED=true` (the `MAIL_*` defaults point at Mailpit) and open the inbox at
`http://localhost:8025`. To send at once instead of waiting for 08:00, set for example
`FOLLOW_UP_REMINDER_CRON=0 * * * * *` (every minute) while testing. `docker compose up -d` of the whole system already
starts Mailpit and turns the job on.

**Real email with Gmail (free):**

1. Turn on 2-Step Verification for the Gmail account, then create an App Password at
   `https://myaccount.google.com/apppasswords` (16 characters).
2. Set `FOLLOW_UP_REMINDER_ENABLED=true`, `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME` and `MAIL_FROM`
   to the Gmail address, `MAIL_PASSWORD` to the App Password, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true` — in
   `.env` for Docker, or in the Elastic Beanstalk environment properties for a deployment. Never commit the password.

Elastic Beanstalk (EC2) blocks only outbound port 25, so Gmail on port 587 works there without AWS SES.

## Run the whole system with Docker

`compose.yml` builds the backend (`backend/Dockerfile`) and the frontend (`frontend/Dockerfile`, served by nginx, which
forwards `/api` to the backend) next to PostgreSQL.

1. Copy `.env.example` to `.env` and fill at least `POSTGRES_PASSWORD`, `JWT_SIGNING_KEY`, the three `PAYOS_*` keys
   and, for the first start, `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`. Add `CLOUDINARY_URL` to upload
   result images (UC-67).
2. Build and start:

   ```powershell
   docker compose up -d --build
   ```

3. Open `http://localhost:8081` (`FRONTEND_PORT`). The API is also on `http://localhost:8080` (`BACKEND_PORT`).

The database stays in the `clinic_postgres_data` volume; archived medical-record packages and the signing simulator
files stay in `clinic_backend_data`. `docker compose logs -f backend` shows the backend log; `docker compose down`
stops everything (`--volumes` also deletes the data). Running only the database for IDE development still works with
`docker compose up -d postgres`.

## Deploy to AWS Elastic Beanstalk

`.github/workflows/deploy-beanstalk.yml` deploys on every push to `main` that touches `backend/`, `frontend/` or
`deploy/beanstalk/` (and from the Actions tab with "Run workflow"). It runs the same checks as the CI workflows, builds
the backend jar and the frontend, packages them with `deploy/beanstalk` (a `Procfile` and an nginx config that serves
the React app and forwards `/api` to Spring Boot on port 5000) and deploys the zip to the Java SE environment.

One-time setup:

1. In IAM, create a user for GitHub with the managed policy `AdministratorAccess-AWSElasticBeanstalk` and an access key.
2. In GitHub, **Settings → Secrets and variables → Actions**, add the secrets `AWS_ACCESS_KEY_ID` and
   `AWS_SECRET_ACCESS_KEY`. Variables `AWS_REGION`, `EB_APPLICATION` and `EB_ENVIRONMENT` are optional (defaults
   `ap-northeast-3`, `clinic-management-system`, `clinic-management-system-env`).
3. In the Beanstalk environment properties set `SERVER_PORT=5000`, the database, JWT, PayOS and Cloudinary values, and
   `APP_FRONTEND_ORIGIN` to the environment URL.
   For UC-75 reminder emails add the Gmail values of [Follow-up reminder emails](#follow-up-reminder-emails-uc-75).

Without the secrets the workflow is skipped, so forks and branches without AWS access are unaffected.

## Demo Server (Windows laptop + Cloudflare Tunnel)

For demos and shared team testing, not for real patient data. `deploy/compose.yml` runs PostgreSQL, the backend,
Caddy (serves the built frontend and forwards `/api/` to the backend, the same-origin topology of
`docs/security.md`) and `cloudflared`. HTTPS ends at Cloudflare; no port of the laptop is opened to the internet.
Because the database and the archive directory live on the same machine, every user can export closed records.

1. **Laptop:** install Docker Desktop (WSL 2 backend) and enable "Start Docker Desktop when you sign in". In Windows
   power settings set sleep to "Never" when plugged in and "Do nothing" when the lid is closed. Turn on BitLocker
   (`docs/security.md`: storage encrypted at rest).
2. **Tunnel:** in Cloudflare Zero Trust, Networks → Tunnels → Create a tunnel (Cloudflared). Copy its token. Add a
   public hostname, for example `clinic.<your-domain>`, with service `HTTP` and URL `web:80`. The domain must be on
   Cloudflare.
3. **Configuration:** copy `deploy/.env.example` to `deploy/.env` (git-ignored) and fill it in. `APP_FRONTEND_ORIGIN`
   must be exactly `https://` plus the public hostname, otherwise refresh and logout answer `403`. The backend does not
   start without the three `PAYOS_*` values.
4. **Start** from the repository root (Compose reads `deploy/.env` by itself):

   ```shell
   docker compose -f deploy/compose.yml up -d --build
   docker compose -f deploy/compose.yml logs -f backend
   ```

   The containers restart with Docker Desktop. `http://127.0.0.1:8088` on the laptop itself is a local check only;
   sign-in there fails because the origin differs from `APP_FRONTEND_ORIGIN`.
5. **Reference lists** (once per database, in this order):

   ```shell
   docker compose -f deploy/compose.yml run --rm -e SPRING_PROFILES_ACTIVE=icd10-import backend
   docker compose -f deploy/compose.yml run --rm -e SPRING_PROFILES_ACTIVE=long-term-disease-import backend
   docker compose -f deploy/compose.yml run --rm -e SPRING_PROFILES_ACTIVE=medicine-import backend
   ```

   Admin recovery uses the same form with `SPRING_PROFILES_ACTIVE=admin-recovery`.
6. **Update** after pulling new code: `docker compose -f deploy/compose.yml up -d --build`.
7. **Backup** the database and the archive volume together (closed-record packages must have an off-site copy):

   ```powershell
   docker compose -f deploy/compose.yml exec postgres pg_dump -U clinic -Fc -f /tmp/clinic.dump clinic
   docker compose -f deploy/compose.yml cp postgres:/tmp/clinic.dump .\clinic.dump
   docker run --rm -v clinic-demo_backend_data:/data -v "${PWD}:/backup" alpine tar czf /backup/backend-data.tgz -C /data .
   ```

   Do not use `>` to save `pg_dump` output in Windows PowerShell 5.1: it re-encodes the bytes and corrupts the dump.

To sign e-prescriptions in the demo, set the three UC-90 simulator values shown in `deploy/.env.example`; the
simulated CA app is then public at `<APP_FRONTEND_ORIGIN>/dev/ca-sim/`. Keep it off when not needed.

**TBD:** the backend sees the proxy's address, not the user's, so `ip_address` in audit logs is the Caddy container
until a trusted-proxy boundary is decided (`SecurityAuditServiceImpl`).

## Testing

- Backend: `./mvnw verify` in `backend/` (integration tests use PostgreSQL Testcontainers and are skipped when Docker is unavailable).
- Frontend: `npm run verify` in `frontend/` (lint, tests, build).

## Architecture

Read [`docs/architecture.md`](docs/architecture.md) for architectural decisions and boundaries. Engineering agents must also follow [`AGENTS.md`](AGENTS.md).

## Contribution Workflow

> **TODO:** Define branch, review, verification, and pull-request expectations before feature development begins.

Do not commit secrets or undocumented business assumptions. Record unresolved requirements as **ASSUMPTION** or **TBD** in the relevant source-of-truth document.
