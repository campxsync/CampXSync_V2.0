# IADM-03 S4 — Migration approval request (US-032 RLS)

**Date:** 2026-11-24  
**Trigger:** Owner `approved` after `APPROVED S3b COMMIT`  
**DEV only:** `azulbaxszsqxymqmlwfe`

## Why

`iam.access_reviews` / `iam.access_review_items` already exist. Current RLS is bound to legacy **`adm09.*`** keys. IADM-03 uses **`iam.review.manage`**. Without replacing those policies, R03/R01 sessions cannot start or decide reviews. **No FORCE RLS. No new tables.**

## Files (repo only — not applied)

- Forward: `database/migrations/20261124140000_iadm03_access_review_rls.sql`
- Rollback: `database/migrations/20261124140000_rollback__iadm03_access_review_rls.sql`

## What it changes

| Object | Notes |
|---|---|
| Drop legacy `adm09.*` policies on review tables | Scripts_V3.0 originals |
| Create platform / tenant + `iam.review.manage` policies | SELECT/INSERT/UPDATE |
| Grants to `authenticated` / `service_role` | Same pattern as S3 |

## No DDL needed for

- US-034 — audit vs notification messaging (code only)
- US-035 — R27 denial tests (code only)
- US-036/037 — WAITS CADM-03 / STM-07
- US-038/039 — PARKED

## Approve phrase

Reply exactly:

`APPROVE MIGRATION 20261124140000`

Then the agent will apply on DEV and finish the S4 headed demo.

## Parallel (no DDL)

While waiting, S4 implements US-034/035 and access-review Java/UI against the existing tables.
