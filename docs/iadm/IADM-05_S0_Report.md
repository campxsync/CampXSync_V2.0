# IADM-05 S0 Report — Scaffold, keys, schema, shared audit

**Slice:** S0 (IADM-05-US-021 … US-025)  
**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**DEV:** `azulbaxszsqxymqmlwfe` only

## Stories

| Story ID | Plain name | Status |
|---|---|---|
| IADM-05-US-021 | Preflight inventory and reconciliation | completed — `docs/iadm/iadm-05/US021_PREFLIGHT_REPORT.md` |
| IADM-05-US-022 | IADM-05 service scaffold | API-ready — service `:8103`, gateway `/api/v1/iadm/05` |
| IADM-05-US-023 | Permission-key catalogue and role mapping | API-ready — `iadm05.*` keys + matrix tests |
| IADM-05-US-024 | Schema, tenant scoping and row-level security | API-ready — schema `gov` + FORCE RLS on DEV |
| IADM-05-US-025 | Shared audit integration | API-ready — `DurableAuditWriter` → `audit.log_event`; fail-closed test |

## Conventions chosen (from preflight)

| Item | Value |
|---|---|
| Port | 8103 |
| Prefix | `/api/v1/iadm/05` |
| Package | `com.campx.iadm.governance` |
| Keys | `iadm05.*` (newest IADM-04 precedent) |
| Schema | `gov` |
| Audit | reuse shared `audit.log_event` |

## Evidence

- Unit tests: `GovernancePermissionMatrixTest`, `GovernanceAuditFailClosedTest` — Maven exit 0
- Migrations applied on DEV via Supabase MCP project `azulbaxszsqxymqmlwfe`:
  - `iadm05_foundation_keys`
  - `iadm05_gov_schema`
- Rollback scripts in repo: `*_rollback__iadm05_*.sql`
- Gateway route + `start_stack` / `check_stack` / `restart_service` registered

## Build defaults recorded

- Approver = Super Admin (R01) only; author ≠ approver (G-15)
- Institution Admin (R03) adherence view-only (G-16)
- College Admin (R04) no trigger (G-02)

## Next

Continue S1 without stopping (US-026, US-001…004, US-018, US-013, US-030).
