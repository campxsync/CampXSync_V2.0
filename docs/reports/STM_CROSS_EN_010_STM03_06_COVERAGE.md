# STM-CROSS-EN-010 — STM-03..06 coverage ledger

Delivery-first vertical slice: **named, grep-discoverable** tenant-isolation and RBAC negative tests in module unit suites.  
**Out of scope:** unified multi-service e2e harness, admissions (STM-02), STM-07..STM-10.

| STM | Module (Maven) | Cross-tenant mutation | Named CROSS-EN-010 entry | Wrong role | Unauthenticated | Additional isolation / RLS |
|-----|----------------|----------------------|--------------------------|------------|-----------------|---------------------------|
| STM-03 | `enrollment-service` | `EnrollmentDomainServiceTest.stm03Us004_crossTenantStatusUpdateFailsClosed` | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm03En002_wrongRoleCannotMutateStatus` · `stmCrossEn010_wrongRoleDenied` (alias) | `stm03En002_unauthenticatedActorRejected`, `stm03Us004_unauthenticatedStatusHttpRejected`, … | `crossTenantReadFailsClosed`, `LivePostgresEnrollmentPersistenceTest.liveEnrolmentPersistReadBackAndCrossTenantDenial` |
| STM-04 | `lifecycle-service` | `LifecycleDomainServiceTest.stmCrossEn010_crossTenantMutationDenied` (`decideRequest` foreign tenant) | same | `stmCrossEn010_wrongRoleDenied` (`REGISTRAR_FORBIDDEN` on faculty) | *No dedicated unauthenticated domain test* (HTTP 401 covered indirectly via gateway/controller paths in other modules) | `stm04En006_crossTenantHistoryIsolation`, `LivePostgresLifecyclePersistenceTest.stm04En001_liveLifecyclePersistReadBackAndCrossTenantDenial` |
| STM-05 | `guardian-service` | `GuardianDomainServiceTest.stm05En002_crossTenantRegistrarCannotSeePilotGuardians` (`updateGuardian`) | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm05Us007_wrongRoleDenied` · `stmCrossEn010_wrongRoleDenied` (alias) | `stm05Us007_nullActorDenied` (401 `TENANT_REQUIRED`) | `stm05En002_crossTenantCannotGrantOrViewAccess`, `stm05Us007_crossTenantCannotDesignate`, live RLS in `LivePostgresGuardianPersistenceTest` |
| STM-06 | `document-service` | `DocumentDomainServiceTest.stm06En004_crossTenantUploadDenied` | `stmCrossEn010_crossTenantMutationDenied` (alias) | `stm06Us010_unauthenticatedAndWrongRoleDenied` (faculty branch) · `stmCrossEn010_wrongRoleDenied` (alias) | `unauthenticatedCallerIsRejected`, `stm06En006_unauthenticatedRoleCannotConfigureTypes` | `crossTenantStudentUploadIsIsolated`, `LivePostgresStudentDocumentUploadTest` RLS read-back, verification cross-tenant in `LivePostgresDocumentVerificationTest` |

## Gaps (honest)

1. **No single harness** exercises UI → Gateway → all four services in one Playwright/JUnit flow for CROSS-EN-010.
2. **STM-04** lacks an explicit unauthenticated negative in `LifecycleDomainServiceTest` (controller-only / future HTTP contract test).
3. **Live Postgres** suites require env JDBC; skipped locally when `campx.live.jdbc.url` is unset (documented in respective test classes).
4. **STM-07..STM-10** not included in this slice.

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