# Database Conventions

## Document Authority

This document is the source of truth for PostgreSQL schema, naming, migration, and data-lifecycle conventions.

## Schema Management

- All schema changes use versioned Flyway migrations.
- Hibernate `ddl-auto=create` and `ddl-auto=update` must not be used in production.
- **TODO:** Define migration naming and review conventions.

## Naming and Types

> **TODO:** Define table, column, key, index, constraint, enum/status, and timestamp conventions.

## Integrity and Transactions

> **TODO:** Define integrity rules, concurrency strategy, and transaction expectations based on confirmed use cases.

## Sensitive Data and Retention

> **TODO:** Classify stored data and define retention, deletion, and audit requirements with stakeholders.
