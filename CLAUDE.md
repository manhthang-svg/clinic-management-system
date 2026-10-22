# Claude Code Instructions

Read and follow [`AGENTS.md`](AGENTS.md) before making any change. It is the primary engineering instruction file for this repository.

Read the relevant source-of-truth documents under [`docs/`](docs/) before planning or editing. If a requirement is missing or unclear, label it **ASSUMPTION** or **TBD** rather than inventing behavior.

This file adds Claude-specific orientation. It does not override `AGENTS.md` or `docs/`; if they disagree, they win.

## Source-of-Truth Map

| Topic | Document |
| --- | --- |
| Engineering rules for all agents | [`AGENTS.md`](AGENTS.md) |
| Architecture, layers, module boundaries | [`docs/architecture.md`](docs/architecture.md) |
| Business scope, actors, glossary, business rules (BR-xx) | [`docs/business-context.md`](docs/business-context.md) |
| Use cases (UC-xx) | [`docs/source/Report-3_Software_Requirement_Specification.docx`](docs/source/Report-3_Software_Requirement_Specification.docx) |
| Roles and permissions | [`docs/permission-matrix.md`](docs/permission-matrix.md) |
| Statuses and transitions | [`docs/state-transitions.md`](docs/state-transitions.md) |
| HTTP API contracts and errors | [`docs/api-conventions.md`](docs/api-conventions.md) |
| Database and migrations | [`docs/database-conventions.md`](docs/database-conventions.md) |
| Authentication, tokens, logging, privacy | [`docs/security.md`](docs/security.md) |
| Visual design and UI conventions | [`DESIGN.md`](DESIGN.md) |

## Repository Layout

```text
backend/    Java 21, Spring Boot, Maven wrapper, Flyway, PostgreSQL
  src/main/java/clinic/backend/
    config/ (+properties/)  controller/  dto/request|response/  entity/  enums/
    exceptions/  mapper/  repository/  security/ (+jwt/, user/)  service/ (+impl/)  utils/
  src/main/resources/db/migration/                    Flyway V<n>__*.sql
frontend/   React 19, TypeScript (strict), Vite, Vitest, Testing Library
  src/app/        App shell, ErrorBoundary
  src/features/   <feature>/ components, *-api.ts adapters, tests (e.g. auth/, account/)
  src/shared/     api/ (ApiClient, ApiError, ApiResponse/ErrorResponse types), config/ (environment)
  src/styles/     global.css — the only stylesheet
  src/test/       Vitest setup
docs/       Source-of-truth documents
```

## Commands

| Task | Command (run in the directory shown) |
| --- | --- |
| Backend full verification | `./mvnw verify` in `backend/` |
| Backend run | `./mvnw spring-boot:run` in `backend/` (environment variables in `README.md`) |
| Frontend full verification | `npm run verify` in `frontend/` (lint, test, build) |
| Frontend dev server | `npm run dev` in `frontend/` (proxies `/api` to the backend) |
| Frontend tests only | `npm run test` in `frontend/` |

## Frontend Conventions

These describe the existing code. Follow them unless a documented decision changes them.

- **Feature folders.** Code for a capability lives in `src/features/<feature>/`. Cross-feature, purely technical code goes in `src/shared/`. Do not put feature logic in `shared/`.
- **API access.** Each feature exposes an interface plus a factory, e.g. `createAccountApi(client: ApiClient): AccountApi`. Components never call `fetch` directly. Paths are relative to `VITE_API_BASE_URL` (default `/api/v1`).
- **Responses and errors.** `ApiClient` unwraps the backend `ApiResponse` envelope and returns its `data`. On failure it throws `ApiError` with `status` and the parsed `errorResponse` (`code`, `message`, `details`, `requestId`). Components map `errorResponse.code` / `status` to Vietnamese user messages; never show raw backend messages.
- **Dependency injection for tests.** Components and the app accept collaborators as props with production defaults (`App({ sessionManager, accountApi })`, `LoginPage({ onLogin })`).
- **Session.** `SessionManager` keeps the access token in memory only; the refresh token is an HttpOnly cookie. Never store tokens, patient data, or medical data in `localStorage`, `sessionStorage`, IndexedDB, or URLs (`docs/security.md`).
- **Components.** Function components, named exports, `readonly` props interfaces, a short doc comment naming the use case (`/** UC-3 Change Password. */`).
- **Forms.** `<label htmlFor>` for every input, `noValidate`, one `role="alert"` error, disabled submit while incomplete or submitting, progress label `Đang …`. Client validation mirrors backend rules for feedback only.
- **Copy.** UI text is Vietnamese. Use the role labels in `src/app/App.tsx` and terms from the glossary in `docs/business-context.md`.
- **Tests.** Vitest + Testing Library + user-event, colocated as `*.test.ts(x)`. Query by role and Vietnamese label text; assert observable behavior. Vitest globals are disabled — import `describe`, `it`, `expect`, `vi` explicitly.
- **TypeScript.** Strict mode, `verbatimModuleSyntax` (use `import type` for types), no unused locals or parameters.
- **Undecided (TBD):** routing, global state management, API type generation, end-to-end test scope. Do not introduce a library for these without a recorded decision.

## UI and Styling Rules

- [`DESIGN.md`](DESIGN.md) is the single source of truth for frontend visual design (palette, typography, spacing, component states, accessibility). Read it before UI work. Do not restate or redefine design rules here or elsewhere; change `DESIGN.md` instead.
- The running login, change-password, and signed-in screens use the adopted styles in `DESIGN.md`. Do not restyle them as a side effect of unrelated work; further adoption follows the plan in that document.
- The current implementation is plain CSS in `src/styles/global.css` with semantic HTML; keep using it. A UI framework such as Tailwind CSS or shadcn/ui is not prohibited, but may be introduced only after explicit approval and a compatibility evaluation, as described under UI Framework Adoption in `DESIGN.md`. Do not install, configure, or generate code for one on your own initiative.
- Do not invent statuses, labels, or business rules in the UI. Statuses and their labels come from the source-of-truth documents; badge tones for them are TBD until approved.
- The frontend hides functions a role cannot use, but authorization is enforced by the backend. Do not infer permissions not listed in `docs/permission-matrix.md`.

## Change Discipline

- Do not modify `package.json`, lockfiles, dependencies, the database schema, or backend code unless the task requires it.
- Never commit or push unless the user asks.
- Preserve unrelated uncommitted work in the working tree.
