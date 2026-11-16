# STM-03 Enrollment Service

Java domain service for student enrolment & registration (port **8095**).

## Architecture boundary

`UI / BFF → API Gateway (:8080) → enrollment-service (:8095) → Supabase PostgreSQL (dev ref azulbaxszsqxymqmlwfe)`

Business rules, RBAC, and status transitions live in `EnrollmentDomainService`. Persistence uses HikariCP JDBC with RLS session context. No direct DB access from Next.js.

## Contracts

- OpenAPI: [`openapi.yaml`](./openapi.yaml)
- Gateway route prefix: `/api/v1/enrollment`
- Path C delegation: set `CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081` (fail-closed when unset)

## Local run

```text
java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=true ^
  -cp target\classes;<classpath.txt> com.campx.enrollment.EnrollmentApplication 8095
```

Preferred ops runners:

- Cross-service: `scripts/demo/run_cross_en_010_stack.ps1`
- Env guard: `node scripts/dev/validate_dev_env.mjs`
- Ops pack: `docs/runbooks/STM-03-EN-007_ENROLLMENT_OPS.md`

## Tests

```text
mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest
```

Operational readiness markers: `stm03En007_*`. Cross-tenant aliases: `stmCrossEn010_*`.

## Health semantics

With internal HMAC enabled, unsigned `GET /health` may return **401**. That still means the listener is up (see CROSS-EN-010 / `check_stack.ps1`).

## Related

- R31 Phase A notes: `README-R31-PHASE-A.md`
- Cross ops: `docs/runbooks/STM_CROSS_OPS_READINESS.md`
