# CampXSync Microservice Template & Architecture Checklist

Use this template whenever scaffolding a new Java domain microservice in CampXSync.

---

## 1. Directory Structure Convention
```
services/{domain}-services/{service-name}/
├── pom.xml
├── src/
│   ├── main/java/com/campx/{service}/
│   │   ├── {Service}Application.java              (HTTP Server, /actuator/health, router)
│   │   ├── {Service}Controller.java               (RFC 7807 problem details, JSON (de)serialization)
│   │   ├── {Service}DomainService.java            (Business rules, RBAC checks, idempotency, intelligence hooks)
│   │   ├── {Service}Exception.java                (HTTP status, stable error code catalog)
│   │   ├── config/
│   │   │   └── DatabaseConnectionManager.java     (HikariCP pool, maxPoolSize 3-5, leakDetection 2000ms)
│   │   ├── persistence/
│   │   │   ├── {Service}Persistence.java          (Interface)
│   │   │   ├── Postgres{Service}Persistence.java  (HikariCP JDBC, SET LOCAL ROLE, transactions)
│   │   │   └── InMemory{Service}Persistence.java  (For fast unit tests)
│   │   ├── security/
│   │   │   └── {Service}SecurityContext.java      (Tenant RLS claims binding, actor extraction)
│   │   └── intelligence/
│   │       └── {Service}IntelligenceService.java  (ControlledIntelligenceAdapter emitter)
│   └── test/java/com/campx/{service}/
│       ├── {Service}DomainServiceTest.java
│       └── {Service}ControllerTest.java
```

---

## 2. Mandatory Wiring Checklist

1. **Parent Maven POM (`services/{domain}-services/pom.xml`):**
   - Register `<module>{service-name}</module>`.
2. **API Gateway (`api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`):**
   - Add route mapping: `routeTable.put("/api/v1/{resource}", "http://localhost:{port}/api/v1/{resource}");`.
3. **Database & Connection Pooling:**
   - HikariCP pool initialized with development pooler URL (`aws-0-ap-south-1.pooler.supabase.com:6543`).
   - Transaction helper executes `SET LOCAL ROLE authenticated` and `set_config('request.jwt.claims', ...)`.
4. **Error Handling (RFC 7807):**
   - Output `application/problem+json` with fields: `type`, `title`, `status`, `code`, `message`, `traceId`, `path`.
5. **Distributed Tracing & Logging:**
   - Propagate `X-Trace-Id` and `X-Correlation-Id` across all log events and JSON error responses.
6. **CampXSync Intelligence Layer:**
   - Emit versioned JSON events (`ControlledIntelligenceAdapter.emit(...)`) upon successful domain mutations.
7. **Stack Scripts (`scripts/dev/start_stack.ps1`, `restart_service.ps1`, `check_stack.ps1`):**
   - Add service port and health check endpoint.

