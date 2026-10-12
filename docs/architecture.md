# Architecture

## Document Authority

This document is the source of truth for system structure, module boundaries, dependency direction, and architectural decisions.

## Architecture Style

- Modular monolith.
- Package-by-feature.
- Layer dependency direction: **API -> Application -> Domain**.
- Infrastructure provides adapters that implement required ports.
- PostgreSQL is the system database.

See [`AGENTS.md`](../AGENTS.md) for mandatory implementation rules.

## System Context

> **TODO:** Add a context diagram after external actors and systems are confirmed.

## Module Boundaries

> **TODO:** Define modules only after business capabilities are confirmed.

## Dependency Rules

> **TODO:** Document allowed cross-module collaboration and enforcement mechanisms.

## Architecture Decisions

> **TODO:** Record consequential decisions and their rationale, preferably as focused ADRs if needed.

## Open Questions

> **TBD:** Deployment topology, integration points, and non-functional requirements.
