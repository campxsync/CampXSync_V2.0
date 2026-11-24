# IADM-03 S3b — Migration approval request (US-030)

**Date:** 2026-11-24  
**Trigger:** Owner `proceed for next slice` after `APPROVED S3 COMMIT`  
**DEV only:** `azulbaxszsqxymqmlwfe`

## Why

US-030 needs a durable propose→approve store for role↔permission mapping changes. No such table exists (`to_regclass('iam.catalog_change_requests')` is null). S0 D2 requires owner approval before new IAM tables. FORCE RLS is **not** in this migration.

## Files (repo only — not applied)

- Forward: `database/migrations/20261124130000_iadm03_catalog_change_requests.sql`
- Rollback: `database/migrations/20261124130000_rollback__iadm03_catalog_change_requests.sql`

## What it creates

| Object | Notes |
|---|---|
| `iam.catalog_change_requests` | ADD_MAPPING / REMOVE_MAPPING only; status PROPOSED→… |
| ENABLE RLS + platform-admin policies | **No FORCE RLS** |
| Grants to `authenticated` / `service_role` | Same pattern as `iam.access_requests` |

## No DDL needed for

- US-031 — governance / ceiling / claims read contract (reuse existing tables + in-process ceiling data)

## Approve phrase

Reply exactly:

`APPROVE MIGRATION 20261124130000`

Then the agent will apply on DEV via Supabase MCP and finish US-030 headed demo.

## Parallel (no DDL)

While waiting, S3b implements **US-031 governance read** and US-030 Java/UI against the pending schema.
