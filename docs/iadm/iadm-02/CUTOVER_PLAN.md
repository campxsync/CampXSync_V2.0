# IADM-02 Cutover Plan (READ-ONLY — do not execute)

**Date:** 2026-11-23  
**Status:** Plan only. No DDL, route retirement, or FORCE RLS on `cfg.policies` / freeze of `cfg.setting_values` until the owner explicitly authorizes each step.  
**DEV project:** `azulbaxszsqxymqmlwfe`

---

## Current dual-path state

| Path | Status | Notes |
|---|---|---|
| IADM-02 `/api/v1/iadm/02` (:8100) | Live (S0–S3) | Drafts → approve → publish → `cfg.config_versions`; consumer effective-config |
| Monolith `/api/v1/admin/configuration` | Still running | Institute-admin service; left unchanged per owner |
| Alias `/v1/platform-configs` | Still running | Same monolith family; do not extend |
| Writers of `cfg.setting_values` | Institute Admin + College Admin JDBC | Residual write risk until freeze |
| Writers of `cfg.policies` | Institute + college policy repositories | Residual write risk until FORCE/tighten |

---

## Reader confirmation (pre-cutover, 2026-11-23)

| Reader | Uses `cfg.setting_values` as effective platform catalogue? | Notes |
|---|---|---|
| IADM-02 consumer / versions | **No** | Effective values from `cfg.config_versions.payload` only |
| IADM-01 `Iadm02PlatformDefaultsClient` | **No** | Pulls `GET /api/v1/iadm/02/consumer/effective-config` (US-026) |
| Institute Admin `PostgresGlobalSettingRepository` | Reads/writes `setting_values` for **monolith** configuration path only | Residual dual-path until route retirement |
| College Admin `PostgresCollegeSettingRepository` | College override rows (US-031 PARKED) | Not treated as IADM-02 platform effective |

**Conclusion:** No service currently uses `cfg.setting_values` as the IADM-02 effective platform value that bypasses `config_versions`. Monolith readers remain for legacy UI until cutover.

---

## Recommended cutover order (when authorized — do not execute)

1. **Reader migration**  
   - Point every platform-default reader (IADM-01 already wired, future IADM-03, other consumers) at `GET /api/v1/iadm/02/consumer/effective-config` with a seeded `iam.api_clients` grant.  
   - Confirm no code path treats `cfg.setting_values` as the **effective** platform catalogue (college overrides may remain for US-031 PARKED).  
   - Consumer contract returns `schemaVersion`, `configVersionId`, `fetchedAt`, `keys` (incl. `iam.delegation.max_validity_days` when granted). Clients cache ≤5 minutes and revalidate by version id; fail closed when stale refetch fails.

2. **Publish proof window**  
   - Operate exclusively through IADM-02 publish for platform catalogue keys for one DEV soak period.  
   - Keep monolith routes up but stop **new** feature work against them.

3. **Freeze platform-catalogue keys in `cfg.setting_values`**  
   - Java (or DB) write-block for keys that appear in `cfg.setting_definitions` platform catalogue only.  
   - Do **not** drop `adm05` grants. College-scoped override rows stay until US-031 decision.

4. **FORCE RLS / tighten `cfg.policies`**  
   - Only after institute-admin and college-admin writers are retired or routed through IADM-02.  
   - Same wave as removing dual-write risk for US-029 denials.

5. **Retire monolith routes + alias**  
   - Gateway: remove or 410 **`/api/v1/admin/configuration`** (institute-admin :8081) and the **`/v1/platform-configs`** alias after UI BFF cutover.  
   - Frontend: ensure all desks use `/api/platform/configuration*` → `/api/v1/iadm/02`.  
   - Do not extend the alias while dual-path is live.

6. **Rollback**  
   - Re-enable monolith routes; leave IADM-02 tables intact; reverse freeze/FORCE with numbered rollback scripts prepared at authorization time.

---

## Residual write risk (findings)

See `docs/iadm/FINDINGS.md` → **FINDING-IADM02-WRITE-01**. Until steps 3–5 run, college/institute services can still mutate `cfg.setting_values` / `cfg.policies` and confuse operators about “effective” platform defaults.
