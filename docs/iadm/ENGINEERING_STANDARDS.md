# IADM Microservices — Engineering Standards (Agreement §3.2)

This document describes how Institute Administration (IADM) microservices **01–08** use the shared **campx-logger** module (`logger/`), map exceptions to HTTP problem JSON, and avoid leaking internal details to clients (Agreement §3.2).

## Logging (`campx-logger`)

- **Dependency:** Each IADM service declares `com.campx.platform:campx-logger` in its Maven POM.
- **Controllers** obtain a static `CampXLogger` via `CampXLoggerFactory.getLogger(Class)`.
- **Per request:**
  1. Resolve `correlationId` from `X-Correlation-Id`, then `X-Trace-Id`, else generate a UUID.
  2. `LogContext.put(KEY_SERVICE, "IADM-0X")` and `LogContext.put(KEY_TRACE_ID, correlationId)`.
  3. Open `FlowTracker tracker = logger.flow(method + " " + path, correlationId)`.
  4. After successful gateway HMAC: `LogContext.put(KEY_USER_ID, …)`, `LogContext.put(KEY_TENANT_ID, …)`, `tracker.step("authenticated")`.
  5. In `finally`: `tracker.close()` and `LogContext.clear()`.
- **Domain services** log successful **mutations** with `logger.info` plus `logger.audit(AuditEvent.builder()…)` via a private `auditMutation` helper. Read-only paths use `logger.debug` where useful.
- **Never log** passwords, JWTs, API keys, or raw request bodies that may contain secrets.

## Exception → problem JSON

| Situation | HTTP | Response body | Server log |
|-----------|------|---------------|------------|
| Gateway HMAC / auth failure | Verifier status | `errorCode` + safe `detail` from verifier | `logger.warn` |
| `UserProfileAccessDeniedException` | 403 | Domain message | `tracker.markFailed`, `logger.warn` |
| `InstituteAdminException` (and peers) | Exception status | `errorCode` + domain message | `tracker.markFailed`, `logger.warn` |
| Any other `Exception` | **500** | **`errorCode`: `IADM0X_INTERNAL`, `detail`: `Request could not be completed`** | `tracker.markFailed`, `logger.error` with stack trace |

Problem JSON shape (IADM controllers today):

```json
{"errorCode":"IADM05_NOT_FOUND","detail":"Route not found: /api/v1/iadm/05/unknown"}
```

## 500 responses never leak internals

Clients must **not** receive `e.getMessage()`, stack traces, SQL errors, or class names on HTTP 500. Only the generic phrase **"Request could not be completed"** is returned; engineers use structured logs and correlation IDs for diagnosis.

## Documentation

- Controller and domain service class Javadoc references **Agreement §3.2**, `CampXLogger`, and the no-secrets rule.
- Each service root package includes `package-info.java` summarizing gateway path and logging expectations.

## Verification

From the repo root (Windows JDK 8 + Maven on PATH):

```powershell
$env:Path = "C:\Java\jdk\bin;C:\Users\niran\Downloads\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin;" + $env:Path
mvn -pl services/administration-services/iadm-01-tenant-lifecycle-service,services/administration-services/iadm-02-platform-config-service,services/administration-services/iadm-03-identity-access-service,services/administration-services/iadm-04-billing-entitlements-service,services/administration-services/iadm-05-data-governance-service,services/administration-services/iadm-06-platform-analytics-service,services/administration-services/iadm-07-security-compliance-service,services/administration-services/iadm-08-audit-ops-service -am test -q
```

## Agreement §3.2 checklist (this pass)

| Item | Status |
|---|---|
| A. Architecture UI→Gateway→Java→Supabase | Unchanged; IADM routes already on gateway |
| B. Exception handling | Controllers map domain exceptions; 500 is generic |
| C. Documentation | This file + class/`package-info` Javadoc |
| D. Logging service | `campx-logger` on all IADM-01…08 controllers and domain services |