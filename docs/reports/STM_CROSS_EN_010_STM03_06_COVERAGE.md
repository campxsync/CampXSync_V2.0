# STM-CROSS-EN-010 — STM-03..06 coverage ledger

Delivery-first vertical slice: **named, grep-discoverable** tenant-isolation and RBAC negative tests in module unit suites.  
**Out of scope:** unified multi-service e2e harness, admissions (STM-02), STM-07..STM-10.

| STM | Module (Maven) | Cross-tenant mutation | Named CROSS-EN-010 entry | Wrong role | Unauthenticated | Additional isolation / RLS |
|-----|----------------|----------------------|--------------------------|------------|-----------------|---------------------------|
| STM-03 | `enrollment-service` | `EnrollmentDomainServiceTest.stm03Us004_crossTenantStatusUpdateFailsClosed` | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm03En002_wrongRoleCannotMutateStatus` · `stmCrossEn010_wrongRoleDenied` (alias) | `stm03En002_unauthenticatedActorRejected`, `stm03Us004_unauthenticatedStatusHttpRejected`, … | `crossTenantReadFailsClosed`, `LivePostgresEnrollmentPersistenceTest.liveEnrolmentPersistReadBackAndCrossTenantDenial` |
| STM-04 | `lifecycle-service` | `LifecycleDomainServiceTest.stmCrossEn010_crossTenantMutationDenied` (`decideRequest` foreign tenant) | same | `stmCrossEn010_wrongRoleDenied` (`REGISTRAR_FORBIDDEN` on faculty) | *No dedicated unauthenticated domain test* (HTTP 401 covered indirectly via gateway/controller paths in other modules) | `stm04En006_crossTenantHistoryIsolation`, `LivePostgresLifecyclePersistenceTest.stm04En001_liveLifecyclePersistReadBackAndCrossTenantDenial` |
| STM-05 | `guardian-service` | `GuardianDomainServiceTest.stm05En002_crossTenantRegistrarCannotSeePilotGuardians` (`updateGuardian`) | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm05Us007_wrongRoleDenied` · `stmCrossEn010_wrongRoleDenied` (alias) | `stm05Us007_nullActorDenied` (401 `TENANT_REQUIRED`) | `stm05En002_crossTenantCannotGrantOrViewAccess`, `stm05Us007_crossTenantCannotDesignate`, live RLS in `LivePostgresGuardianPersistenceTest` |
| STM-06 | `document-service` | `DocumentDomainServiceTest.stm06En004_crossTenantUploadDenied` | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm06Us010_unauthenticatedAndWrongRoleDenied` (faculty branch) · `stmCrossEn010_wrongRoleDenied` (alias) | `unauthenticatedCallerIsRejected`, `stm06En006_unauthenticatedRoleCannotConfigureTypes` | `crossTenantStudentUploadIsIsolated`, `LivePostgresStudentDocumentUploadTest` RLS read-back, verification cross-tenant in `LivePostgresDocumentVerificationTest` |

## Cross-service E2E harness (2026-11-16)

| Artifact | Path |
|----------|------|
| Harness | `scripts/demo/run_stm_cross_en_010_e2e.mjs` |
| Stack runner | `scripts/demo/run_cross_en_010_stack.ps1` |
| Evidence | `docs/reports/STM_CROSS_EN_010_E2E_2026-11-16.json` (**31/31 PASS**) |

Covers: gateway health; STM-03..06 listen/health; Supabase JWT personas; `GATEWAY_MISSING_AUTH`; spoofed identity header rejection; registrar/student gateway reads; wrong-role denials; loopback HMAC cross-tenant document-types; Path C IAM active-check; service-failure detection probe.

## Gaps (honest)

1. ~~No single harness~~ **Resolved** for Gateway → STM-03..06 HTTP E2E (Bearer JWT). Playwright UI desk tour remains optional / per-story demos.
2. **STM-04** lacks an explicit unauthenticated negative in `LifecycleDomainServiceTest` (HTTP 401 covered by harness via gateway).
3. **Live Postgres** suites require env JDBC; skipped locally when `campx.live.jdbc.url` is unset (documented in respective test classes).
4. **STM-07..STM-10** not included in this slice.
5. ~~Full cross-tenant penetration matrix~~ **2026-11-16:** `scripts/demo/run_cross_tenant_pen_test.mjs` + `run_cross_tenant_pen_stack.ps1` → `docs/reports/CROSS_TENANT_PEN_2026-11-16.json` (complements unit + `run_stm_cross_en_010_e2e.mjs`).

## Discovery

```text
rg stmCrossEn010 services/student-services/{enrollment,lifecycle,guardian,document}-service/src/test
```

## Suite run (2026-11-15, `mvn clean test -pl` per module)

| Module | Exit code | Tests | Failures | Errors |
|--------|-----------|-------|----------|--------|
| `enrollment-service` | 0 | 108 | 0 | 0 |
| `lifecycle-service` | 0 | 51 | 0 | 0 |
| `guardian-service` | 0 | 75 | 0 | 0 |
| `document-service` | 0 | 115 | 0 | 0 |

_Note:_ A non-`clean` multi-module `mvn test` hit stale `document-service` bytecode (`NoSuchMethodError` in live Postgres tests); `clean test` cleared it. Live JDBC suites still require `campx.live.jdbc.url` when enabled in CI.