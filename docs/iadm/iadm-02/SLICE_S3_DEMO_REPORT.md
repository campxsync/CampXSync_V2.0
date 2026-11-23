# IADM-02 Slice S3 Demo Report

**Date:** 2026-11-23  
**Owner gate:** **`APPROVED S3 COMMIT`** (2026-11-23)  
**Prior:** `APPROVED S2` (commit `480f847`; no extra S2 commit)  
**DEV project:** `azulbaxszsqxymqmlwfe`

---

## Stories US-021…026

| Story | Expected | Result | Evidence |
|---|---|---|---|
| US-021 Consumer identity | Authenticated service identity via `iam.api_clients` (+ gateway accepts active api_client without role_assignment) | **PASS** | Gateway `JdbcIdentityResolver` SERVICE path; client `SVC-CONSUMER-01` / user `69822238-…` |
| US-022 Consumer read | Effective keys + version + schema + fetchedAt; includes `iam.delegation.max_validity_days` | **PASS** | `S3_CONSUMER_SMOKE.json` — schemaVersion=1, 3 keys, delegation=90, session=55 |
| US-023 Denied domains / no drafts | `fees` denied; keys only from published payload | **PASS** | `403 IADM02_CONSUMER_DOMAIN_DENIED` + audit `IADM02_CONSUMER_READ_DENIED`; no draft fields in response |
| US-024 Stale / unavailable | Explicit states; fail closed; 5-min window | **partially completed** | Contract: `fetchedAt` + client `freshnessWindowSeconds=300`; IADM-01 client returns `cacheStatus` FRESH/STALE/UNAVAILABLE. Live outage simulation (stop publish / break gateway mid-demo) **not filmed**; 503 path code-proven (`IADM02_CONFIG_UNAVAILABLE` / `IADM01_PLATFORM_DEFAULTS_UNAVAILABLE`) |
| US-025 Diagnostics desk | Last fetch vs active; FRESH/STALE; read-only | **PASS** | Headed `s3_diagnostics.png`; API row principal `69822238-…` status `FRESH`; UI `iadm02-diagnostics-desk` |
| US-026 IADM-01 platform defaults | Read via IADM-02; no local catalogue copy; fail closed on mandatory key | **PASS** | `GET /api/v1/iadm/01/platform-defaults` → `S3_IADM01_DEFAULTS.json` pass; `Iadm02PlatformDefaultsClient` |

---

## Headed demo

| Step | Artifact |
|---|---|
| Catalogue + diagnostics link | `docs/iadm/iadm-02/evidence/s3_catalogue.png` |
| Diagnostics FRESH row | `docs/iadm/iadm-02/evidence/s3_diagnostics.png` |
| Versions desk | `docs/iadm/iadm-02/evidence/s3_versions.png` |
| Headed summary | `docs/iadm/iadm-02/evidence/S3_HEADED.json` (`pass: true`) |

Script: `node scripts/demo/iadm02_s3_headed.mjs`

---

## API / DB smokes

| Check | Result |
|---|---|
| `iadm02_s3_consumer_smoke.mjs` | **PASS** (`S3_CONSUMER_SMOKE.json`) |
| `iadm02_s3_iadm01_defaults_smoke.mjs` | **PASS** (`S3_IADM01_DEFAULTS.json`) |
| Consumer denial audit | **PASS** — `IADM02_CONSUMER_READ_DENIED` for client `19d4bacf-…` |
| `cfg.setting_values` as effective platform bypass | **None found** (see CUTOVER_PLAN reader confirmation) |

---

## Tests

| Suite | Result |
|---|---|
| Maven `iadm-02-platform-config-service` | **14 run, 0 fail** |
| Maven `iadm-01` `TenantLifecycleRbacTest` | **7 run, 0 fail** |
| Frontend `tsc --noEmit` | **0 errors** |

---

## Cutover / residual risk

- Plan only: `docs/iadm/iadm-02/CUTOVER_PLAN.md` (not executed).  
- Findings: `FINDING-IADM02-WRITE-01`.  
- Monolith `/api/v1/admin/configuration` and `/v1/platform-configs` **unchanged**.

---

## CSV

Update `docs/user-stories/CampXSync_IADM-02_User_Stories.csv` Implementation status for US-021…026 when the file is unlocked (`completed` only where acceptance + automated evidence above; US-024 → `partially completed` until outage demo filmed).

---

## Stop gate

**Closed:** `APPROVED S3 COMMIT`.  
Do **not** start S4 (corrective republish) or execute cutover until the owner authorizes the next task.
