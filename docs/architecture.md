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
├── config/          # Spring configuration, filters, startup runners
│   └── properties/  # @ConfigurationProperties records
├── controller/      # REST controllers; thin, call service interfaces only
├── dto/
│   ├── request/     # Request bodies with transport validation
│   └── response/    # Response bodies, ApiResponse envelope, ErrorResponse
├── entity/          # JPA entities: persistent state, business rules, explicit state transitions
├── enums/           # Shared enum types, including ErrorCode
├── exceptions/      # AppException and the global exception handler
├── mapper/          # MapStruct mappers between entities/service results and DTOs
├── repository/      # Spring Data JPA repositories
├── security/        # Password hashing, origin check, REST auth handlers
│   ├── jwt/         # Access-token issuing, claims, authority conversion
│   └── user/        # The authenticated principal of a request
├── service/         # Service interfaces and service result types
│   └── impl/        # Service implementations and scheduled jobs; transaction boundary
└── utils/           # Small technical helpers with proven reuse (cookies, tokens, IDs)
```

Business capabilities (for example, account management) are not separate top-level packages; their classes live in the layer packages above. Use clear, capability-specific class names (for example, `AuthService`, `StaffAccountRepository`) so that each layer stays navigable as it grows. Do not use `utils` as a dumping ground for business behavior.

### Layer Responsibilities

| Layer | Responsibility | May depend on |
| --- | --- | --- |
| `controller` | HTTP mapping, `@Valid` request validation, wrapping results in `ApiResponse` | `dto`, `service` interfaces, `security.user`, `config` constants |
| `dto` | Explicit request and response contracts | `enums`, Java and Jackson/validation annotations |
| `service` | Service interfaces and the result types they return | `dto`, `entity`, `enums`, `security.user` |
| `service.impl` | Use-case orchestration, authorization boundary, transaction boundary, business rejections as `AppException` | `repository`, `entity`, `enums`, `mapper`, `dto`, `security`, `utils`, `exceptions`, `config` |
| `repository` | Spring Data JPA repositories and their queries | `entity`, `enums`, Spring Data |
| `entity` | JPA mapping of persistence tables, invariants, and explicit state transitions | `enums`, JPA, Lombok |
| `enums` | Reusable classifications, roles, statuses, types, and error codes | Java standard library, `org.springframework.http` for `ErrorCode` |
| `mapper` | Conversion to response DTOs | `dto`, `entity`, `service` result types, `enums` |
| `config` / `security` / `exceptions` / `utils` | Cross-cutting technical concerns | Any layer they wire, plus frameworks, within the rules below |

The required dependency direction is:

```text
Controller -> Service (interface) <- Service implementation -> Repository -> Entity
                                          \-> Mapper -> DTO
Entity / Repository / Service / DTO -> Enums
```

Additional rules:

- Controllers remain thin, depend on service interfaces (never on `service.impl`), and never call repositories or touch JPA entities directly.
- Only services use repositories. Repositories know only entities and enums.
- Entities keep business rules and state transitions as methods (for example `StaffAccount.registerFailedLogin`); they have no setters for state that a rule governs, and they must not depend on services, repositories, DTOs, or web code.
- Enums must not depend on JPA or any application layer.
- JPA entities are never exposed through REST APIs; DTOs must not reference entities. Mappers produce response DTOs.
- Transaction boundaries belong to service implementations, not controllers. A business rejection is an `AppException` carrying an `ErrorCode`; when a rejection must still commit state (failed-login counters, audit events, refresh-token reuse revocation), the method declares `@Transactional(noRollbackFor = AppException.class)`.
- Lombok is used for boilerplate (`@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`); MapStruct for DTO mapping.

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

- Entity tests are fast unit tests of business rules without Spring or a database.
- Service tests exercise use cases with controlled test doubles (Mockito) for repositories and security collaborators where appropriate.
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
