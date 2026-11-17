# Go-live readiness for student account setup (STM-07)

**Date:** 2026-11-17  
**Audience:** product owner  
**Dev project only:** `azulbaxszsqxymqmlwfe`  
**Status of this checklist:** honest readiness document for Security tests, logs and go-live readiness for account setup (STM-07-EN-006)

## Ready enough for Dev pilot (with gaps listed)

| Area | Plain status |
|---|---|
| Link student to existing login (needs VERIFIED) | Ready in student-service |
| Request / retry login setup | Ready (staff + IAM operator / Path C) |
| Staff desk `/college/account-provisioning` | Ready for College Admin / Platform Admin UI; Registrar JWT needs Auth `tenant_id` |
| Assign / revoke student role R25 | Ready via institute-admin |
| Cut access on SUSPENDED / DETAINED / WITHDRAWN | Ready on student-status path; STM-04 notify is best-effort when `STM01_SERVICE_URL` set |
| Student portal shows own link status | Ready |
| Audit who changed accounts and roles | Ready for R01–R03 from existing audit logs |
| Cross-tenant security harness | Last run **34/34 PASS** |
| End-to-end gateway smoke | Last run **32/32 PASS** |

## Known gaps (not done — do not treat as complete)

| Gap | Plain words |
|---|---|
| Event bus / Kafka | Notifications and full cross-service event fan-out held (CROSS-EN-007) |
| Role-configuration decision | Hosting student role assign/revoke on institute-admin is owner-approved; broader role-config matrix items remain open (FLAG-002/003/007) |
| Self-request “if enabled” setting | Student self-request login (US-014) not defined |
| Commercial antivirus | Live AV for documents gated |
| Academic history / service requests | New services STM-08 / STM-09 blocked |
| Pilot Registrar JWT | Missing `app_metadata.tenant_id` → gateway malformed token before Java delegation check |
| Admission auto-provision | Not automatic after handover (needs VERIFIED + policy) |
| Known red suites | institute-admin ~12 errors; api-gateway 6f+7e; student-service 1 RLS `persons` error (identical at older commits) |

## Logging & audit

- Privileged account actions write through the existing audit repository (`IAM_LINKED`, `IAM_ACCESS_REVOKED`, `STUDENT_ROLE_*`, provisioning markers).
- CampXLogger is used on student-service and lifecycle-service paths; secrets must never appear in logs.
- Staff audit desk reads filtered existing logs only (no new audit store).

## Security tests to run before any wider rollout

```powershell
mvn test "-pl" "services/student-services/student-service" "-Dtest=Stm07AccountProvisioningTest,Stm07Us003StaffLinkStatusTest,Stm07Us005RetryProvisioningTest,Stm07Us006ProvisioningHistoryTest,Stm07Us012AccountRoleAuditTest,Stm07En002IamIntegrationGuardTest,Stm07En004ProvisioningRecoveryTest,Stm07En005IntegrationContractTest"
mvn test "-pl" "services/student-services/lifecycle-service" "-Dtest=Stm07En005LifecycleAccountNotifyTest"
cd frontend; npx tsc --noEmit
node scripts/demo/run_cross_tenant_pen_test.mjs
node scripts/demo/run_stm_cross_en_010_e2e.mjs
```

Clear `CAMPX_IAM_DELEGATION_BASE_URL` in the shell before Maven if the local stack left it set (otherwise a fail-closed delegation unit test fails for environment pollution).

## Go / no-go recommendation

**Dev pilot:** GO with the gaps above listed to operators.  
**Production:** NO-GO until Auth metadata for Registrar, known-red suite policy, and parked infrastructure decisions are closed by the owner.
