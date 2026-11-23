# IADM-02 Slice S4 Demo Report (+ cutover)

**Date:** 2026-11-23  
**Owner gate:** **`APPROVED S4`** + owner **commit and push** (2026-11-23)  
**Prior:** `APPROVED S3 COMMIT` (`a66eed2`); owner authorized **start cutover** then S4  
**DEV project:** `azulbaxszsqxymqmlwfe`

---

## Cutover (executed before S4 stories)

| Step | Result | Evidence |
|---|---|---|
| Gateway 410 `/api/v1/admin/configuration` | **PASS** | `S4_CUTOVER_SMOKE.json` → 410 `IADM02_CUTOVER_GONE` |
| Gateway 410 `/v1/platform-configs` | **PASS** | same |
| Gateway 410 `/api/v1/admin/policies` | **PASS** | same |
| Freeze catalogue keys in `setting_values` writers | **PASS** | Java guards (institute + college) |
| FORCE RLS `cfg.policies` | **PASS** | `relforcerowsecurity=true` [SQL]; migration `20261123010300_*` |
| Plan doc updated | **PASS** | `docs/iadm/iadm-02/CUTOVER_PLAN.md` |

---

## S4 stories

| Story | Expected | Result | Evidence |
|---|---|---|---|
| US-027 Corrective republish | New immutable version from prior; audit; consumers pull | **PASS** | API `VER-005`/`VER-006`; audit `IADM02_CONFIG_ROLLBACK_PUBLISHED`; headed `s4_corrective.png` |
| US-028 R28 auditor read-only | Mutations denied with auditor message | **partially completed** | Matrix + `assertMutationAllowed` for AUDITOR/R28; no dedicated R28 headed persona seeded this turn |
| US-029 R03/R04 cannot change platform defaults | API + screen deny | **PASS (API)** | College Admin draft → `403 IADM02_TENANT_ADMIN_DENIED`; gateway cutover blocks monolith writes |
| US-030 COM deferred notification | Non-blocking deferred status | **PASS** | `notificationDeferred=true` on publish + corrective |
| US-031 Overrides | PARKED | **PARKED** | unchanged |
| US-032 Full demo checklist | End-to-end evidence pack | **partially completed** | Cutover + S4 corrective headed + smokes recorded; full section-15 checklist not re-run as one film |

---

## Headed demo

| Artifact | Path |
|---|---|
| Versions before | `docs/iadm/iadm-02/evidence/s4_versions_before.png` |
| Corrective result | `docs/iadm/iadm-02/evidence/s4_corrective.png` (`Published VER-006 from VER-004 · notification delayed`) |
| Headed JSON | `docs/iadm/iadm-02/evidence/S4_HEADED.json` |
| API smoke | `docs/iadm/iadm-02/evidence/S4_CUTOVER_SMOKE.json` |

Script: `node scripts/demo/iadm02_s4_headed.mjs`

---

## Tests

| Suite | Result |
|---|---|
| Maven `iadm-02-platform-config-service` | **14 run, 0 fail** |
| Frontend `tsc --noEmit` | **0 errors** |

---

## Stop gate

**Closed:** `APPROVED S4` (no commit).  
Do **not** start IADM-03 or further modules until newly authorized. Residual partials (US-024/028/032) remain open until owner asks to close them.
