# Engineering Instructions for Coding Agents

This file is the primary engineering instruction source for every coding agent working in this repository. Read it before making changes. More specific requirements belong in the source-of-truth documents under `docs/`.

## Working Agreement

Do not invent missing business requirements. Mark uncertainty explicitly as **ASSUMPTION** or **TBD**, and request clarification when it would materially affect behavior, data, security, or architecture.

Before modifying code:

1. Read this file and the relevant documents under `docs/`.
2. Inspect existing patterns and conventions.
3. Identify the affected files and modules.
4. Produce a concise implementation plan.

After modifying code:

1. Run the relevant tests.
2. Run Maven `verify` for backend changes.
3. Run the frontend build and tests when relevant.
4. Inspect the Git diff.
5. Confirm that no unrelated files changed.

Never weaken, delete, or bypass tests merely to make a build pass.

## Architecture Rules

- Build a monolith organized package-by-layer under `clinic.backend`: `controller`, `dto`, `service`, `repository`, `model`, `enums`, `entity`, `config`, `security`, `exception`, `util` (see `docs/architecture.md`).
- Dependencies flow **Controller -> Service -> Repository interface -> Model**, with shared domain enum types in `enums`.
- The `model` package may depend on `enums`, but neither package may depend on Spring, JPA, or any other layer.
- Services depend on repository interfaces and security contracts, never on JPA implementations, entities, controllers, or DTOs.
- Keep controllers thin: translate transport concerns, invoke services, and translate results.
- Controllers must never access repositories or JPA entities directly.
- Do not expose JPA entities through REST APIs; use explicit request and response models.
- Business rules belong in `model` or `service`, according to whether they are domain invariants or use-case orchestration.
- Important state transitions must be explicit model behavior, not scattered field assignments.
- Do not use magic strings for business statuses; put reusable business enum types in `clinic.backend.enums` and use value objects in `model` when behavior or validation is required.
- Transaction boundaries belong in the `service` layer.
- Keep layer boundaries explicit; `ArchitectureRulesTest` enforces them and must not be weakened.

## Persistence and Database

- PostgreSQL is the production database.
- Every database schema change must be represented by a versioned Flyway migration.
- Do not use Hibernate `ddl-auto=create` or `ddl-auto=update` in production.
- Follow `docs/database-conventions.md` once conventions are defined there.

## API Rules

- Follow `docs/api-conventions.md`.
- List APIs must support pagination where appropriate.
- Keep API contracts independent from persistence models.

## Security and Privacy

- Never hardcode secrets. Use environment variables or an approved secret-management mechanism.
- Never log passwords, authentication tokens, or sensitive medical data.
- Treat patient and medical information as sensitive by default.
- Follow `docs/security.md` and `docs/permission-matrix.md`; do not infer permissions that are not documented.

## Testing and Verification

- Important use cases require unit and/or integration tests appropriate to the risk.
- Integration tests involving persistence should use PostgreSQL Testcontainers.
- Test observable behavior and business rules, not implementation details.
- A change is incomplete until its relevant verification commands pass, or any blocker is clearly reported.

## Scope Discipline

- Do not introduce Kafka, Redis, Keycloak, Elasticsearch, Kubernetes, microservices, CQRS, or event sourcing unless explicitly required.
- Do not add infrastructure, dependencies, abstractions, or frameworks without a demonstrated current need.
- Keep changes focused and preserve unrelated user work.
