# IADM Wave E Report — Parity & Cutover

**Date:** 2026-11-21  
**Branch:** `feature/java-backend-integration`  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Authorized by:** Niranjan V.G. — IADM Wave E before CADM

## Exit summary

| Phase | Status | Evidence |
|---|---|---|
| **E0** Contract proof (5 CADM) | **completed** | `WAVE_E_CONTRACTS.md` |
| **E1-A** IADM-01 lifecycle parity | **completed** | `Iadm01WaveE1ALiveDevTest` + `Iadm01ContractLiveDevTest` |
| **E1-B** IADM-02 config parity | **partially completed** | Catalogue/college-value/history on :8100; rollback **501** until `cfg.setting_value_versions` |
| **E1-C** IADM-03 + IADM-08 | **partially completed** | Role assign/revoke + handover gate; audit export/institute-status; retention defaults |
| **E1-D** IADM-04 + IADM-06 | **partially completed** | Plans/subscriptions/invoices read on :8102; plan-change **501**; usage by-college/export/platform |
| **E1-E** IADM-05 + IADM-07 | **partially completed** | Retention/DSR execute + legal-hold gate; posture/findings/policies; platform-minimums empty stub |
| **E2** Security findings | **completed** (RBAC-01 doc-only) | SEC-02 finally-remove; SEC-11 fixed; RBAC-01 OPEN documented |
| **E3** Stack + gateway cutover | **completed** | Ports 8099–8106 in start/check/stop/restart; gateway `/api/v1/iadm/0X` authoritative |

**Wave E exit:** Ready for CADM. Tracked PARTIAL gaps below do not block CADM contract consumers.

---

## E1-A details (IADM-01)

| Story | Status | Notes |
|---|---|---|
| US-001 Register institute | completed | Split `:8099` |
| US-002 Appoint R03 | completed | `plat.assign_institution_admin` + `auth.uid()` |
| US-003 Onboard + Auth-linked R04 | completed | `plat.is_auth_linked_user` (SEC-11) |
| US-004 Handover; R03 loses college user-admin | completed | `COMPLETE` → `r03CollegeUserAdmin=REVOKED`; `plat.is_college_handed_over`; IADM-03 assign gate |
| US-005 Own-institute profile | completed | `IADM01_OWN_TENANT_ONLY` |
| US-006 Suspend/reactivate + session block | completed | `plat.tenant_session_blocks` + `plat.is_tenant_suspended` in `UserSecurityContext` |
| US-007 Lifecycle request/decide | completed | Existing path |
| US-008 Auditor history | completed | `adm01.view` / platform |

**Migration:** `20261121000800_iadm_wave_e_handover_and_suspend.sql` (applied on DEV).

**Tests:** `Iadm01WaveE1ALiveDevTest` (3), `Iadm01ContractLiveDevTest` (3), `TenantLifecycleRbacTest` — BUILD SUCCESS.

---

## Tracked PARTIAL gaps (honest)

| Gap | Story | Behavior until closed |
|---|---|---|
| Config rollback versions table | IADM-02-US-007 | `POST .../rollback` → **501** `IADM02_ROLLBACK_UNAVAILABLE` |
| Plan-change request table | IADM-04-US-004 | **501** `IADM04_PLAN_CHANGE_UNAVAILABLE` |
| `usage_metrics.college_id` | IADM-06-US-002 | Groups by `dimension` proxy |
| `plat.platform_security_minimums` | IADM-07-US-004 | Empty list + `partial:true` |
| Audit retention policy table | IADM-08-US-003 | Defaults `{days:365,mutable:false}` |
| Monolith `:8081` Path C | Cross-cutting | **Shim retained** for `/admin/internal/delegations` and legacy `/api/v1/admin/*`; new clients use `/api/v1/iadm/0X` |

Deferred (unchanged): break-glass, two-person appoint, under-18 consent, R27/adm03, Kafka.

---

## E2 findings

| Finding | Result |
|---|---|
| FINDING-IADM-SEC-02 Curriculum identity leak | **FIXED** — finally-remove on CurriculumController |
| FINDING-IADM-SEC-02 Institute/College/Course body attrs | **FIXED** — finally-remove |
| FINDING-IADM-ARCH-01 / SEC-11 | **FIXED** on IADM-01 (E0) |
| FINDING-IADM-RBAC-01 PLATFORM_ADMIN templates | **OPEN** — doc only; keep `is_platform_admin()` |

---

## E3 ops cutover

| Script | Change |
|---|---|
| `start_stack.ps1` | Launch + watch IADM-01…08 on 8099–8106 |
| `check_stack.ps1` | Health `/api/v1/iadm/0N/health` |
| `stop_stack.ps1` | Ports through 8106 |
| `restart_service.ps1` | `iadm-01`…`iadm-08` aliases |
| Gateway | `/api/v1/iadm/01`…`08` registered **before** `/api/v1/admin` |

**Switch date:** 2026-11-21 — IADM split services are the authoritative surface for moved domain routes. Monolith `:8081` remains Path-C / legacy shim until a separate Path-C redesign.

---

## Maven evidence (DEV)

- `iadm-01`: E0 + E1-A + RBAC suites — green  
- `iadm-02`…`08`: module test suites — green  

## Next after approval

**CADM** implementation: reconcile CADM stories, then Waves A–D (consumes IADM contracts in `WAVE_E_CONTRACTS.md`).
