# Security

## Document Authority

This document is the source of truth for security, privacy, authentication, audit, and sensitive-data handling requirements. Authorization assignments belong in [`permission-matrix.md`](permission-matrix.md).

## Baseline Rules

- Do not hardcode secrets.
- Do not log passwords, tokens, or sensitive medical data.
- Treat patient and medical information as sensitive by default.
- **TODO:** Confirm applicable legal, regulatory, and contractual requirements.

## Authentication

> **TBD:** Authentication method, session/token lifecycle, and account recovery policy.

## Authorization

> **TBD:** Authorization model. Do not infer roles or permissions before stakeholder confirmation.

## Data Protection

> **TODO:** Define encryption, masking, retention, backup, and deletion requirements.

## Audit and Monitoring

> **TODO:** Define auditable actions, event content, access controls, and retention without recording sensitive payloads.

## Threats and Open Questions

> **TODO:** Maintain a lightweight threat model as interfaces and workflows become known.
