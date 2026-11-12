# CampXSync Platform Engineering Standard: Logging & Error Handling

**Document Reference**: `ENG-STD-001`  
**Version**: 2.0.0  
**Effective Date**: 2026-11-12  
**Status**: APPROVED & ACTIVE  

---

## 1. Architectural Scope & Principles

CampXSync enforces an end-to-end transactional path:
$$\text{Frontend UI / Next.js BFF} \longrightarrow \text{API Gateway (8080)} \longrightarrow \text{Java Domain Microservices} \longrightarrow \text{PostgreSQL (Dev Ref: \texttt{azulbaxszsqxymqmlwfe})}$$

### Core Requirements
1. **Zero Raw System Logging**: No `System.out.println`, `System.err.println`, or `printStackTrace()` in production Java paths. No unformatted `console.log` in Next.js server-side production code.
2. **Unified RFC 7807 Problem Details**: All errors across the Gateway, Java services, and Next.js BFF routes conform to RFC 7807 (`application/problem+json`).
3. **End-to-End Distributed Trace Correlation**: Exactly one distributed `traceId` is assigned to each inbound request at the boundary, propagated through HTTP headers (`X-Trace-Id`, `X-Correlation-Id`), bound to Mapped Diagnostic Context (MDC), persisted across log lines, and echoed to client responses.
4. **Security & Data Minimization**: Mandatory redaction of secrets, tokens, passwords, and sensitive PII from log output. Only technical IDs (`tenantId`, `userId`) appear in MDC context.
5. **No Swallowed Exceptions**: Every exception is handled, translated to a domain problem response, and logged with its correlation context.

---

## 2. Distributed Tracing & MDC Propagation

### Header Lifecycle
- **Client $\to$ Gateway / Next.js**: If `X-Trace-Id` or `X-Correlation-Id` is present, it is preserved. If absent, a cryptographically strong 32-character hexadecimal UUID is generated.
- **Gateway $\to$ Java Microservices**: Propagated via `X-Trace-Id` and `X-Correlation-Id` headers alongside verified HMAC identity (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`).
- **Java Microservices**: Bound into `com.campx.logger.context.LogContext` upon request entry:
  ```java
  LogContext.setTraceId(traceId);
  LogContext.setTenantId(tenantId);
  LogContext.setUserId(userId);
  LogContext.setUserRole(userRole);
  LogContext.put(LogContext.KEY_SERVICE, "document-service");
  ```
  Cleared in `finally` via `LogContext.clear();`.

---

## 3. Standard RFC 7807 Error Envelope

All HTTP errors emit the following schema (`Content-Type: application/problem+json; charset=utf-8`):

```json
{
  "timestamp": 1790000570281,
  "status": 409,
  "error": "Conflict",
  "title": "Conflict",
  "code": "DUPLICATE_STAGE_REQUIREMENT",
  "errorCode": "DUPLICATE_STAGE_REQUIREMENT",
  "message": "A stage requirement for academicStage 'ADMISSION' and documentType 'DOC-TRANSCRIPT' already exists",
  "path": "/api/v1/documents/requirements",
  "traceId": "9f2b84cf3a01490ea3f8b0304a919241",
  "correlationId": "9f2b84cf3a01490ea3f8b0304a919241"
}
```

### Semantic Status Code Mapping

| Status | Title | Trigger Condition | Code Example |
| :---: | :--- | :--- | :--- |
| `400` | Bad Request | Syntactically invalid request or malformed payload | `MALFORMED_PAYLOAD` |
| `401` | Unauthorized | Missing or expired caller session / JWT | `IDENTITY_REQUIRED`, `UNAUTHORIZED` |
| `403` | Forbidden | Authenticated actor lacks required RBAC role or violates SoD | `FORBIDDEN`, `ROLE_NOT_PERMITTED` |
| `404` | Not Found | Entity or route does not exist within the tenant partition | `ROUTE_NOT_FOUND`, `DOCUMENT_TYPE_NOT_FOUND` |
| `409` | Conflict | Uniqueness constraint violation or version concurrency conflict | `DUPLICATE_DOCUMENT_TYPE_CODE`, `STATE_CONFLICT` |
| `422` | Unprocessable Entity | Domain validation failure (file size, mime type, lifecycle) | `UNSUPPORTED_MIME_TYPE`, `FILE_TOO_LARGE` |
| `502` | Bad Gateway | Upstream communication or protocol error | `GATEWAY_PROXY_ERROR` |
| `503` | Service Unavailable | Downstream microservice offline or circuit breaker tripped | `GATEWAY_SERVICE_UNAVAILABLE` |
| `504` | Gateway Timeout | Microservice response exceeded timeout threshold | `GATEWAY_DOWNSTREAM_TIMEOUT` |
| `500` | Internal Server Error | Unhandled internal runtime exception (generic message returned) | `INTERNAL_ERROR` |

---

## 4. Implementation Guidelines

### Java Microservice Implementation
```java
// DocumentController.java
public Response handle(String method, String path, Map<String, String> headers, String body) {
    String traceId = resolveTraceId(headers);
    LogContext.setTraceId(traceId);
    LogContext.setTenantId(header(headers, "x-tenant-id"));
    LogContext.setUserId(header(headers, "x-user-id"));
    LogContext.setUserRole(header(headers, "x-user-role"));
    LogContext.put(LogContext.KEY_SERVICE, "document-service");

    try (FlowTracker tracker = logger.flow(method + " " + path, traceId)) {
        // Business logic execution
        return json(200, resultNode, traceId);
    } catch (DocumentException e) {
        return problem(e.getStatusCode(), e.getCode(), e.getMessage(), traceId, path);
    } catch (Exception e) {
        logger.error("Unhandled operation exception", e);
        return problem(500, "INTERNAL_ERROR", "The document service could not complete the request", traceId, path);
    } finally {
        LogContext.clear();
    }
}
```

### Next.js BFF Route Implementation
```typescript
// frontend/src/app/api/.../route.ts
import { NextRequest } from 'next/server';
import { apiError, apiSuccess, extractTraceId } from '@/lib/errors/api-error';
import { appLogger } from '@/lib/logging/logger';

export async function POST(req: NextRequest) {
  const traceId = extractTraceId(req);
  // Authorization check
  if (!authorized) {
    return apiError(req, {
      status: 403,
      code: 'FORBIDDEN',
      message: 'Access restricted to College Administrators',
    });
  }

  // Audit emission
  appLogger.audit('document.upload', 'Student submitted required document', { traceId });

  // Call Gateway with trace headers
  const res = await fetch(`${GATEWAY_URL}/api/v1/documents/submissions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': traceId,
      'X-Correlation-Id': traceId,
    },
    body: JSON.stringify(payload),
  });

  return new Response(await res.text(), {
    status: res.status,
    headers: {
      'Content-Type': res.status >= 400 ? 'application/problem+json; charset=utf-8' : 'application/json',
      'X-Trace-Id': traceId,
      'X-Correlation-Id': traceId,
    },
  });
}
```

### Frontend UI Error Display
When any API call returns an RFC 7807 error or the UI hits an error boundary, the UI surfaces:
1. Human-readable diagnostic message.
2. Form field specific error message (where applicable).
3. **Reference ID**: Displays `traceId` clearly (e.g. `Reference ID: 9f2b84cf3a01490ea3f8b0304a919241`) so the user can quote it for support.

