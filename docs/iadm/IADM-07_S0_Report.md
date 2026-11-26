# IADM-07 S0 — Foundation (US-021 … US-025)

**Date:** 2026-11-26  
**Branch:** `feature/java-backend-integration`  
**DEV DB:** `azulbaxszsqxymqmlwfe`

## Stories

| ID | Title | Actual |
|---|---|---|
| IADM-07-US-021 | Preflight inventory | **API-ready** — `docs/iadm/iadm-07/US021_PREFLIGHT_REPORT.md` |
| IADM-07-US-022 | Service scaffold | **API-ready** — `:8105` `/api/v1/iadm/07` health UP |
| IADM-07-US-023 | Permission keys | **API-ready** — 11 `iadm07.*` keys; legacy `adm07.*` (8) untouched |
| IADM-07-US-024 | Schema + FORCE RLS | **API-ready** — schema `sec`, 10 tables, FORCE RLS |
| IADM-07-US-025 | Shared audit | **API-ready** — `DurableAuditWriter` → `audit.log_event` fail-closed |

## Exact values found / decided

| Item | Value |
|---|---|
| Port | **8105** |
| Route | **`/api/v1/iadm/07`** |
| Schema | **`sec`** (short-name convention; blueprint `iadm07` overridden) |
| Key family | **`iadm07.*`** |
| Audit writer | **`audit.log_event`** |

## Migrations (DEV only)

- `20261126130000_iadm07_foundation_keys.sql` (+ rollback)
- `20261126130100_iadm07_sec_schema.sql` (+ rollback)
- `20261126130200_iadm07_s1_demo_seed.sql` (+ rollback)

## Permission catalogue before/after

| Check | Before | After |
|---|---|---|
| `iadm07.*` keys | 0 | **11** |
| `adm07.*` keys | 8 | **8** (unchanged) |
