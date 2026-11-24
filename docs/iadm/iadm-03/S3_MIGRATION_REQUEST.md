# IADM-03 S3 — Migration approval request

**Date:** 2026-11-24  
**Trigger:** Owner `proceed` after `APPROVED S2 COMMIT`  
**DEV only:** `azulbaxszsqxymqmlwfe`

## Why

S0 D2 reserved `iam.access_requests` for US-023…025. Table is still missing. Access-request stories cannot persist without it. FORCE RLS on all `iam.*` is **not** in this migration.

## Files (repo only — not applied)

- Forward: `database/migrations/20261124120000_iadm03_access_requests.sql`
- Rollback: `database/migrations/20261124120000_rollback__iadm03_access_requests.sql`

## What it creates

| Object | Notes |
|---|---|
| `iam.access_requests` | Status machine DRAFT→SUBMITTED/PENDING→APPROVED/REJECTED/EXPIRED |
| ENABLE RLS + tenant/platform policies | **No FORCE RLS** |
| Grants to `authenticated` / `service_role` | Same pattern as `iam.governance_idempotency` |

## No DDL needed for

- US-026/027/029 — reuse existing `iam.delegations` + IADM-02 `iam.delegation.max_validity_days`
- US-028 — reuse Path C active-check on `:8081`

## Approve phrase

Reply exactly:

`APPROVE MIGRATION 20261124120000`

Then the agent will apply on DEV via Supabase MCP and continue US-023…025.

## Parallel (no DDL)

While waiting, S3 can implement **delegations** (US-026/027/029) against existing tables.
