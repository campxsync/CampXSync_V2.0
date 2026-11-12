# CampXSync Platform Definition of Done (DoD)

**Scope**: Mandatory quality gate for every user story and feature delivery.  
**Effective Date**: 2026-11-12  
**Status**: ACTIVE  

A user story is **NOT READY FOR REVIEW** until every criterion below passes with verifiable evidence.

---

## A. Logging Standard
1. **Design Compliance**: All services (Java and Next.js server-side) log exclusively through the designed logging framework (`com.campx.logger` / `campx-logger` reactor module in Java, `appLogger` in Next.js).
2. **Zero Raw System Output**: Absolutely zero `System.out.println`, `System.err.println`, or `printStackTrace()` in production paths. No unformatted `console.log` in server-side Next.js routes.
3. **Structured Logs**: Every log entry records:
   - `timestamp` (ISO-8601 or epoch)
   - `level` (`DEBUG`, `INFO`, `WARN`, `ERROR`, `AUDIT`)
   - `service` (e.g. `document-service`, `api-gateway`, `frontend`)
   - `traceId` / `correlationId`
   - `tenantId`
   - `userId` (ID only, no personal names or emails in MDC)
   - `operation` / `route`
   - `durationMs` (on flow completion)
4. **End-to-End Trace Propagation**: Exactly one distributed `traceId` flows:
   $$\text{Browser Client} \longrightarrow \text{Next.js BFF} \longrightarrow \text{API Gateway} \longrightarrow \text{Java Service} \longrightarrow \text{PostgreSQL}$$
   The trace ID appears in every log line of the request and is returned in client HTTP response headers (`X-Trace-Id`, `X-Correlation-Id`).
5. **Security & Redaction**: Strict masking of secrets, passwords, Bearer tokens, and JWTs. Correct log levels applied.
6. **Business & Security Auditing**: State changes and security events (e.g. upload, approval, rejection, RLS denial, auth failure) emit structured `AUDIT` events and outbox messages where required.

---

## B. Exception Handling & Resilience
1. **Unified Error Contract**: All errors conform to RFC 7807 problem details:
   - `timestamp`, `status`, `title`, `error`, `code`, `errorCode`, `message`, `path`, `traceId`, `correlationId`.
2. **Global Exception Mapping**: Every Java microservice and Next.js route maps errors cleanly:
   - `400` / `422`: Validation & domain rule violations
   - `401`: Missing or invalid authentication
   - `403`: RBAC role permission or tenant segregation denial
   - `404`: Partitioned entity not found
   - `409`: Uniqueness or state conflict
   - `502` / `503`: Downstream microservice offline or circuit breaker open
   - `504`: Gateway timeout
   - `500`: Generic internal server error (never leak SQL or stack traces to client)
3. **Zero Swallowed Exceptions**: No empty `catch {}` blocks. No silent fallbacks. Fallbacks must log warnings and present visible UI banners.
4. **UI Reference ID Display**: Every UI error message presents the `Reference ID` (matching the `traceId`) and field-level validation feedback.
5. **Transactional Integrity**: Multi-table operations commit atomically or roll back completely.

---

## C. Documentation
1. **Traceability**: `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, and `docs/project-control/PROGRESS.md` updated with exact story status.
2. **API Specification**: Document every endpoint with request schema, response schema, error catalog, required roles, and sample JSON.
3. **Design Delta**: Document any behavior or schema nuances in relevant DDDs under `docs/architecture/`.
4. **Catalogs**: Update log-event and error-code catalogs.
5. **Code Documentation**: Javadoc / TSDoc on public classes and functions.
6. **Demo Evidence**: Complete demo pack saved in `docs/demos/<STORY-ID>/`.

---

## D. Verification & Evidence
Every pull request or review handover must present the DoD Table:
| Criterion | Status | Verifiable Evidence |
| :--- | :---: | :--- |
| A. Logging Standard | PASS | Trace-correlated log lines from Next.js, Gateway, and Java service |
| B. RFC 7807 Error Contract | PASS | Problem+json response body and UI error display with Reference ID |
| C. Automated Verification | PASS | Unit, controller, and live PostgreSQL test suite logs |
| D. Visible Headed Demo | PASS | 1280x720 video, numbered screenshots, overlays, warm load <= 1.5s |
| E. Real Data & Persistence | PASS | Database read-back query against `azulbaxszsqxymqmlwfe` |
| F. Tenant & RBAC Isolation | PASS | Cross-tenant 404/RLS denial and unauthorized role 403 test output |

