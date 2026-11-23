# IADM-02 Cutover Plan

**Date:** 2026-11-23  
**Status:** **EXECUTED (DEV)** — owner authorized “start cutover” after `APPROVED S3 COMMIT`.  
**DEV project:** `azulbaxszsqxymqmlwfe`  
**Evidence:** `docs/iadm/iadm-02/evidence/S4_CUTOVER_SMOKE.json`

---

## Dual-path state (after cutover)

| Path | Status | Notes |
|---|---|---|
| IADM-02 `/api/v1/iadm/02` (:8100) | **Active** | Sole platform catalogue publish + consumer path |
| Monolith `/api/v1/admin/configuration` | **410 Gone** (gateway) | `IADM02_CUTOVER_GONE` |
| Alias `/v1/platform-configs` | **410 Gone** (gateway) | Same cutover code |
| Monolith `/api/v1/admin/policies` | **410 Gone** (gateway) | Platform policies via IADM-02 |
| `cfg.setting_values` writers | **Frozen for catalogue keys** | Java guard in institute + college repositories |
| `cfg.policies` | **FORCE RLS** + Java write retired | Migration `20261123010300_iadm02_cutover_force_policies.sql` |

---

## Steps completed

1. **Reader migration** — IADM-01 + consumers use effective-config; no `setting_values` as IADM-02 effective catalogue.  
2. **Publish proof** — Platform catalogue keys published only through IADM-02.  
3. **Freeze platform-catalogue keys** — `PostgresGlobalSettingRepository` / `PostgresCollegeSettingRepository` deny writes when key exists in `cfg.setting_definitions`. `adm05` grants not dropped.  
4. **FORCE RLS on `cfg.policies`** — Applied on DEV; rollback script in repo. Institute/college policy JDBC writers throw retired.  
5. **Retire monolith routes + alias** — Gateway longest-prefix `gone://` → HTTP **410**.  
6. **Rollback** — `20261123010300_rollback__iadm02_cutover_force_policies.sql`; re-enable gateway routes by reverting `GatewayConfig` / `ReverseProxyHandler` if needed.

---

## Residual notes

- College-admin **non-catalogue** `setting_values` overrides remain possible (US-031 PARKED).  
- Finding `FINDING-IADM02-WRITE-01` → **MITIGATED** for platform catalogue / policies path; college non-catalogue rows still open by design until US-031.
