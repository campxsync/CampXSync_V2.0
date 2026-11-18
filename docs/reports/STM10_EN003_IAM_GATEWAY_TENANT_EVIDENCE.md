# STM-10-EN-003 — Existing IAM, Gateway and tenant context (evidence)

**Dev project:** `azulbaxszsqxymqmlwfe`  
**Date:** 2026-11-18  

## Contract kept (no new infra)

1. **Channel A:** Student portal BFFs use `getGatewayHeaders` → `Authorization: Bearer` only; gateway injects tenant/user/role under HMAC. No client-spoofable identity headers from the browser.
2. **Student portal aggregate:** `decidePortalDashboardAccess` requires authenticated **student** + tenant + user; denies faculty, college admin, and **Platform Admin (R01)** (owner decision D9 — no staff/college selector).
3. **IDOR:** Client `studentId` query rejected on portal-dashboard.
4. **Tenant:** Missing tenant → 400; cross-tenant still enforced downstream by Java + RLS.

## Gaps intentionally not “fixed” here

| Gap | Why left |
|---|---|
| Platform Admin calling student-service without college tenant | D9: Platform Admin must not use college staff screens; no college selector |
| Student One IAM link | BLOCKED earlier by `doc.verifications` RLS (item A) |
| Registrar student-row SELECT without matrix `V` | BLOCKED item C |

## Tests

- `stm10En003_platformAdminDeniedStaffPortalAggregate_D9`
- `stm10En003_collegeAdminDeniedStudentPortalAggregate`
- `stm10En003_studentMissingTenantDenied`
- Existing EN-002 access suite
