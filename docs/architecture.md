# Architecture

## Document Authority and Scope

This document is the source of truth for the system's technical structure, dependency rules, and architecture boundaries. Confirmed business capabilities, actors, workflows, permissions, and state transitions belong in their respective documents and must not be inferred here.

The system is a modular monolith. It is deployed as one backend application unless a later, evidenced requirement changes that decision.

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

Use package-by-feature. A future feature should follow this conceptual structure; `<base-package>` and `<feature>` are placeholders, not approved package or business-module names.

```text
<base-package>/
├── <feature>/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
└── shared/        # Small, technical building blocks with proven cross-feature use
```

Do not create feature packages until the corresponding business capability is confirmed. Do not use `shared` as a dumping ground for feature-specific models or behavior.

### Layer Responsibilities

| Layer | Responsibility | May depend on |
| --- | --- | --- |
| `api` | HTTP transport, request validation, DTO mapping, and response/error translation | `application` |
| `application` | Use-case orchestration, authorization boundary, transaction boundary, and port definitions | `domain` |
| `domain` | Business behavior, invariants, value objects, and explicit state transitions | Domain code and Java standard library |
| `infrastructure` | JPA persistence, external integrations, security adapters, and implementations of required ports | `application`, `domain`, and frameworks |

The required dependency direction is:

```text
API -> Application -> Domain
Infrastructure -> Application/Domain ports
```

Additional rules:

- Domain code must not depend on Spring, HTTP, JPA, or infrastructure implementations.
- Infrastructure implements ports required by the application or domain layers.
- Controllers remain thin and never call repositories directly.
- REST DTOs and persistence models do not cross into the domain as transport or storage concerns.
- Transaction boundaries belong to application/use-case operations, not controllers.
- Important state changes are explicit domain behavior; they are not arbitrary field assignments.

## Feature and Module Boundaries

A feature owns its internal API, application, domain, and infrastructure packages. Code in one feature must not import another feature's internal packages.

Cross-feature collaboration must use a small, explicit application-level contract. The exact synchronous or asynchronous collaboration pattern is **TBD** until concrete use cases demonstrate the need. An in-process contract is the default for this modular monolith; no message broker is implied.

Package visibility and architecture tests should enforce boundaries once backend code exists. This structure describes logical modules and does not require Java Platform Module System modules.

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

Cross-cutting code must not become a route around feature boundaries.

## Testing Boundaries

- Domain tests are fast unit tests without Spring or a database.
- Application tests exercise use cases with controlled test doubles for ports where appropriate.
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
