# Architecture

## Document Authority and Scope

This document is the source of truth for the system's technical structure, dependency rules, and architecture boundaries. Confirmed business capabilities, actors, workflows, permissions, and state transitions belong in their respective documents and must not be inferred here.

The system is a monolith organized by technical layer. It is deployed as one backend application unless a later, evidenced requirement changes that decision.

## Technology Baseline

- Backend: Java 21 and Spring Boot.
- Frontend: React, TypeScript, and Vite.
- Database: PostgreSQL.
- Authentication framework: Spring Security with JWT access tokens.
- Database migrations: Flyway.
- Source control and collaboration: GitHub.
- Supported coding agents: Codex and Claude Code, governed by [`AGENTS.md`](../AGENTS.md).

## Monorepo Structure

```text
/
├── backend/       # Backend application; not initialized yet
├── frontend/      # Browser application; not initialized yet
├── docs/          # Source-of-truth engineering and business documents
├── AGENTS.md      # Primary instructions for all coding agents
├── CLAUDE.md      # Claude Code entry point
└── README.md      # Repository overview and future development commands
```

The backend and frontend have independent build systems and dependency graphs. Neither application may import source files or internal implementation types from the other.

## Backend Organization

Use package-by-layer. All backend code lives under the base package `clinic.backend`, grouped by technical layer:

```text
clinic.backend/
├── controller/    # REST controllers and HTTP-only helpers (e.g., cookies)
├── dto/           # API contracts: request and response models
├── service/       # Use-case orchestration, transaction boundary, use-case results
├── repository/    # Repository interfaces and their JPA/Spring Data implementations
├── model/         # Domain models: business behavior, invariants, value objects, state transitions
├── entity/        # JPA entities (persistence models)
├── config/        # Spring configuration, configuration properties, beans, runners, schedulers, filters
├── security/      # Security contracts and adapters (password hashing, token issuing, auth handlers)
├── exception/     # Global error handling and the standard API error model
└── util/          # Small technical helpers with proven reuse
```

Business capabilities (for example, account management) are not separate top-level packages; their classes live in the layer packages above. Use clear, capability-specific class names (for example, `LoginService`, `StaffAccountRepository`) so that each layer stays navigable as it grows. Do not use `util` as a dumping ground for business behavior.

### Layer Responsibilities

| Layer | Responsibility | May depend on |
| --- | --- | --- |
| `controller` | HTTP transport, request validation, DTO mapping, and response/error translation | `dto`, `service`, `model`, `security`, `exception` |
| `dto` | Explicit request and response contracts | `service` results, `model` |
| `service` | Use-case orchestration, authorization boundary, and transaction boundary | `model`, `repository` interfaces, `security` contracts, `util` |
| `repository` | Repository contracts used by services and their JPA/Spring Data implementations | `model`, `entity`, frameworks |
| `model` | Business behavior, invariants, value objects, and explicit state transitions | Model code and Java standard library |
| `entity` | JPA mapping of persistence tables | `model` types, `repository` contract types, JPA |
| `config` / `security` / `exception` | Cross-cutting technical concerns | Any layer they wire, plus frameworks |

The required dependency direction is:

```text
Controller -> Service -> Repository (interface) -> Model
                 \-> Model
Repository implementation -> Entity
```

Additional rules:

- Model code must not depend on Spring, HTTP, JPA, or any other layer.
- Services depend on repository interfaces and security contracts, never on JPA implementations, entities, controllers, or DTOs.
- Controllers remain thin and never call repositories or touch JPA entities directly.
- JPA entities are never exposed through REST APIs; DTOs must not reference entities.
- Transaction boundaries belong to service operations, not controllers.
- Important state changes are explicit model behavior; they are not arbitrary field assignments.

These rules are enforced by `ArchitectureRulesTest` (ArchUnit).

## Frontend and Backend Separation

- The frontend communicates with the backend only through the documented HTTP API.
- The frontend must not depend on JPA models, domain internals, or backend implementation classes.
- Backend responses use explicit DTO contracts defined by [`api-conventions.md`](api-conventions.md).
- Client-side types may mirror published API contracts, but the generation or maintenance approach is **TBD**.
- Browser-side validation improves usability but never replaces backend validation or authorization.

## Cross-Cutting Concerns

Cross-cutting technical concerns should be implemented consistently without leaking business rules into generic infrastructure:

- authentication and authorization;
- request validation and standard error handling;
- transaction management;
- structured logging and correlation identifiers;
- protection of sensitive data;
- configuration and secret loading;
- time and clock abstraction where deterministic testing requires it;
- database migrations;
- health and operational observability.

Cross-cutting code must not become a route around layer boundaries.

## Testing Boundaries

- Model tests are fast unit tests without Spring or a database.
- Service tests exercise use cases with controlled test doubles for repository and security contracts where appropriate.
- API tests verify HTTP contracts, validation, status codes, and authorization behavior.
- Persistence and database integration tests use PostgreSQL Testcontainers and apply Flyway migrations.
- Architecture tests may enforce package dependencies after the backend is initialized.
- Frontend tests cover components, user interactions, and API adapters; end-to-end scope is **TBD** until workflows are confirmed.
- Backend changes must pass Maven `verify`; frontend changes must pass the configured build and tests once those toolchains exist.

## Decisions Deferred Until Business Requirements Are Confirmed

The following are intentionally **TBD**:

- concrete feature/module names and boundaries;
- aggregates, domain entities, value objects, and use cases;
- business workflows, state machines, actors, roles, and permissions;
- domain-specific API resources, commands, events, and error codes;
- database tables, relationships, constraints, and retention rules;
- external system integrations;
- deployment topology and environment-specific scaling requirements;
- availability, performance, audit, and regulatory requirements;
- frontend routing, screens, and state-management approach;
- API client type-generation strategy and end-to-end test scope.

## Explicit Exclusions

Do not introduce Redis, Keycloak, Kafka, Elasticsearch, Kubernetes, microservices, CQRS, or event sourcing unless an explicit, approved requirement justifies the change. Do not add speculative infrastructure or framework abstractions.
