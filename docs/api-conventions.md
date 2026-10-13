# API Conventions

## Document Authority and Scope

This document is the source of truth for HTTP API design and contract conventions. It defines technical consistency only; no business-specific resource, command, or endpoint is approved by this document.

## Base Contract

- All version 1 endpoints use the `/api/v1` prefix.
- JSON is the default request and response representation.
- Use `application/json` for successful JSON payloads and `application/problem+json` for errors.
- JSON property names use `camelCase`.
- Dates and times use ISO 8601 strings. Instants include an offset and should normally be returned in UTC with `Z`.
- UUIDs are represented as canonical strings.
- Do not place secrets, tokens, or sensitive medical data in URLs.

## Resource and URL Naming

- Use plural nouns for resource collections.
- Use lowercase kebab-case for path segments.
- Use path parameters for resource identity: `/api/v1/{resources}/{id}`.
- Keep nesting shallow. Use nested URLs only when the child is meaningfully scoped by the parent.
- Do not encode transport actions as arbitrary verbs when standard resource semantics are sufficient.
- Query parameter names use `camelCase`.

Examples in this document use placeholders and do not define business resources:

```text
GET /api/v1/{resources}
GET /api/v1/{resources}/{id}
POST /api/v1/{resources}
```

## HTTP Methods

| Method | Intended use | Idempotent |
| --- | --- | --- |
| `GET` | Read a resource or collection without changing server state | Yes |
| `POST` | Create a resource or execute an explicit command | No by default |
| `PUT` | Replace the complete client-editable representation at a known URI | Yes |
| `PATCH` | Apply a documented partial update | Depends on the patch contract |
| `DELETE` | Remove or deactivate a resource only when the documented semantics permit it | Yes |

Safe methods must not produce business state changes. Any idempotency requirement for a specific `POST` command is **TBD** with that use case and must be documented in its API contract.

## HTTP Status Codes

Use the most specific standard status code without exposing implementation details:

| Status | Use |
| --- | --- |
| `200 OK` | Successful read, update, or command with a response body |
| `201 Created` | Resource created; include a `Location` header when a resource URI exists |
| `204 No Content` | Successful operation with no response body |
| `400 Bad Request` | Malformed input, type mismatch, or request validation failure |
| `401 Unauthorized` | Authentication is missing, invalid, or expired |
| `403 Forbidden` | Authentication succeeded but the caller lacks permission |
| `404 Not Found` | The addressed resource is unavailable to the caller |
| `409 Conflict` | The request conflicts with current state, uniqueness, or concurrency rules |
| `422 Unprocessable Content` | Syntactically valid input cannot be processed according to documented semantics |
| `500 Internal Server Error` | Unexpected server failure; do not expose stack traces or internals |

Whether a domain rejection maps to `409` or `422` must be specified with the use case. Do not return `200` with an error payload.

## Request and Response DTOs

- Define explicit request and response DTOs at the API boundary.
- Never expose JPA entities, persistence projections, Spring Data `Page`, or domain internals directly.
- Request DTOs contain transport validation only; domain invariants remain in domain/application behavior.
- Response DTOs expose only fields required by the contract.
- Treat omitted, explicit `null`, and empty values as distinct unless the endpoint contract says otherwise.
- Do not silently ignore unknown fields when doing so could hide client mistakes; the final ObjectMapper policy is **TBD** before backend initialization.
- Do not include sensitive fields merely because they exist in a source model.

## Pagination, Sorting, and Filtering

Collection endpoints use zero-based page pagination where pagination is appropriate:

```text
GET /api/v1/{resources}?page=0&size=20&sort=createdAt,desc
```

Conventions:

- `page` defaults to `0`.
- `size` defaults to `20` and must not exceed `100`.
- `sort` uses `field,direction`, where direction is `asc` or `desc`.
- Multiple sort values may be supplied only when the endpoint documents support for them.
- Each endpoint must allow-list sortable and filterable fields; clients must not pass database column or expression names.
- Filtering syntax is defined per resource and is **TBD** until that resource exists.

Return an API-owned pagination shape rather than framework types:

```json
{
  "content": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## Command-Style Endpoints

Use a command-style endpoint when an important state-changing business operation has intent, guards, or side effects that are not accurately represented as generic field updates:

```text
POST /api/v1/{resources}/{id}/commands/{command-name}
```

- Command names use lowercase kebab-case.
- The request DTO contains only inputs required by the command.
- The application invokes explicit domain behavior; it must not patch a status field directly.
- The response uses `200` when returning a representation or `204` when no body is required.
- Invalid transitions use the standard error format and an endpoint-documented `409` or `422` mapping.
- No concrete command names are defined until state transitions and use cases are approved.

## Standard Error Response

Errors follow RFC 9457 Problem Details and may add stable extension fields:

```json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/{resources}",
  "code": "REQUEST_VALIDATION_FAILED",
  "traceId": "correlation-id",
  "errors": [
    {
      "field": "fieldName",
      "code": "INVALID_VALUE",
      "message": "The value is invalid."
    }
  ]
}
```

Rules:

- `code` is stable and machine-readable; business-specific codes are **TBD**.
- `traceId` supports operational correlation and contains no sensitive data.
- `errors` is included for field or request validation failures and omitted otherwise.
- Messages must not reveal stack traces, SQL, class names, secrets, authorization internals, or sensitive medical data.
- Do not echo rejected sensitive values.

## API Versioning and Compatibility

- Major API versions are encoded in the URL, beginning with `/api/v1`.
- Backward-compatible additive changes remain in the current version.
- Removing or changing the meaning or type of an existing field, status, or operation requires a new major API version unless a previously documented compatibility rule permits it.
- Deprecation periods and client migration policy are **TBD** before the first public or external integration.
- Do not create additional versions preemptively.

## Remaining Business-Dependent Decisions

The following remain **TBD** until the related use cases are approved:

- resource names, endpoint inventory, request fields, and response fields;
- command names and command idempotency requirements;
- endpoint-specific filters and sortable fields;
- business error codes and the `409` versus `422` mapping;
- endpoint-specific authorization and data visibility rules;
- whether any operation requires asynchronous processing;
- compatibility and deprecation timelines for external consumers.
