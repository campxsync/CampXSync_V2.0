# API Contract Template: {ENDPOINT_NAME}

## Endpoint Details
- **Method:** `{GET | POST | PUT | DELETE}`
- **Path:** `/api/v1/{resource}`
- **BFF Path:** `/api/{persona}/{resource}`
- **Auth Role:** `{COLLEGE_ADMIN | STUDENT | FACULTY | PLATFORM_ADMIN}`
- **Tenant Scope:** Multi-tenant isolated via `X-Tenant-Id` header and PostgreSQL RLS.

## Request Headers
| Header | Required | Description |
|---|:---:|---|
| `Authorization` | Yes | Bearer JWT or Session Cookie |
| `X-Tenant-Id` | Yes | Target tenant UUID |
| `X-User-Id` | Yes | Calling user UUID |
| `X-User-Role` | Yes | Caller role code |
| `X-Trace-Id` | No | Distributed trace UUID |

## Request Body Schema (JSON)
```json
{
  "field": "value"
}
```

## Success Response (HTTP 200 / 201)
```json
{
  "id": "uuid",
  "status": "SUCCESS"
}
```

## Error Responses (RFC 7807 Problem Details)
| Status | Code | Message / Condition |
|---|---|---|
| `400` | `VALIDATION_FAILED` | Malformed body or missing mandatory fields |
| `401` | `UNAUTHORIZED` | Missing or invalid authentication token |
| `403` | `FORBIDDEN` | Insufficient role or unauthorized tenant access |
| `404` | `NOT_FOUND` | Target entity does not exist in tenant |
| `409` | `CONFLICT` | Unique constraint violation or lifecycle lock |

