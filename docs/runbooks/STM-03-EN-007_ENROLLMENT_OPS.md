# STM-03-EN-007 — Enrollment operational readiness

**Service:** enrollment-service (`:8095`)  
**Dev database:** `azulbaxszsqxymqmlwfe` only  
**Personas:** credentials by name in `.env.audit` (never commit/print passwords)

## Start / stop

| Action | Command |
|--------|---------|
| Minimal STM stack (incl. enrollment) | `powershell -ExecutionPolicy Bypass -File scripts/demo/run_cross_en_010_stack.ps1` |
| Health / listen check | `powershell -ExecutionPolicy Bypass -File scripts/dev/check_stack.ps1` |
| Stop CROSS ports | handled by stack runner; or stop listeners on 8095 |

## Environment (names only)

| Variable | Purpose |
|----------|---------|
| `CAMPX_PERSISTENCE_MODE=postgres` | Real JDBC |
| `CAMPX_INTERNAL_AUTH_ENABLED=true` | HMAC required |
| `CAMPX_INTERNAL_SECRET` / `CAMPX_GATEWAY_HMAC_SECRET` | Gateway↔service HMAC |
| `CAMPX_IAM_DELEGATION_BASE_URL` | Path C (`http://127.0.0.1:8081`) |
| JDBC / Supabase vars | Loaded for postgres profile from local audit env |

## Verification

1. `node scripts/dev/validate_dev_env.mjs` → exit 0  
2. `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` → 0 failures  
3. Gateway read (via CROSS-EN-010): `GET /api/v1/enrollment/terms` with registrar Bearer  
4. Negative: unauthenticated gateway call → `GATEWAY_MISSING_AUTH`

## Docs

- Service README: `services/student-services/enrollment-service/README.md`
- OpenAPI: `services/student-services/enrollment-service/openapi.yaml`
- Cross pack: `docs/runbooks/STM_CROSS_OPS_READINESS.md`

## Explicit deferrals

- Kafka (STM-CROSS-EN-007)
- Live commercial AV (STM-06-EN-003)
- Production Supabase writes
- STM-07..STM-10 enrollment adjacency
