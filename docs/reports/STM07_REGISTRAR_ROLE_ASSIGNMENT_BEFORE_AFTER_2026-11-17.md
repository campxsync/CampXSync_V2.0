# Pilot Registrar (R31) role-assignment row — before/after

**Date:** 2026-11-17  
**Project confirmed:** `azulbaxszsqxymqmlwfe`  
**Runbook followed:** `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md` §3.B step 2 (campus-scoped REGISTRAR assignment only)  
**One account / one row:** Auth/IAM user `c31a0000-0000-4000-8000-000000000031` → assignment id `c31a0000-0000-4000-8000-000000000032`

## Before

| Field | Value |
|---|---|
| Assignment id | `c31a0000-0000-4000-8000-000000000032` |
| User | `c31a0000-0000-4000-8000-000000000031` |
| Tenant | `a0000000-0000-0000-0000-000000000001` |
| College | `a0000000-0000-0000-0000-000000000002` |
| Role | REGISTRAR (`a72bb0de-18ee-421c-b2db-2d89fdb0d773`) |
| `deleted_at` | null |
| `created_at` / `updated_at` | 2026-11-15 13:26:56.937487+00 |

**Row was already present** (not soft-deleted).

## After (runbook `INSERT … ON CONFLICT (id) DO NOTHING`)

Same values as before. Insert was a no-op conflict — **no new row created, timestamps unchanged**.

## Why the earlier “missing row” diagnosis was wrong

1. The assignment row **was not missing** at the time of the `GATEWAY_NO_ROLE_ASSIGNED` demo failure.
2. The gateway error message *“No active **recognized** business role assignment”* means: rows were found, but none matched the gateway’s recognized-role filters.
3. In `api-gateway/.../JdbcIdentityResolver.java`, effective role resolution only accepts College Admin–family, Faculty–family, or Student codes. **`REGISTRAR` / R31 is not included**, so `resolvedRole` stays null and the gateway returns `GATEWAY_NO_ROLE_ASSIGNED` even with a valid REGISTRAR assignment.
4. Live tests that `DELETE FROM iam.role_assignments WHERE user_id = ?` (e.g. `PostgresDelegationRepositoryTest` teardown) only delete **synthetic test users** they create — they do **not** target `…000031`. **No test change made.**

## Demo impact

Re-running “delegated Registrar succeeds” after this restore still fails with the same `GATEWAY_NO_ROLE_ASSIGNED` until the gateway recognizes Registrar (R31) as a business role. That code change was **not** authorized in this answer set.
