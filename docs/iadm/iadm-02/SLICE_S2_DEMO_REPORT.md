# IADM-02 Slice S2 Demo Report

**Date:** 2026-11-23  
**Owner approval gate:** STOP for `APPROVED S2` / `APPROVED S2 COMMIT` / `CHANGES S2: <feedback>`  
**Owner authorization:** `APPROVED S1 COMMIT` + continue S2 US-016…020 (DEV only, no push)  
**DEV project:** `azulbaxszsqxymqmlwfe`

---

## Pre-S2 closure items (required before / with S1 commit)

| # | Item | Result |
|---|---|---|
| 1 | US-012 draft **Edit** on drafts desk + headed | **PASS** — Edit/Save on drafts desk; `s1_us012_edit.png`; audit `IADM02_CONFIG_DRAFT_UPDATED` |
| 2 | R02 JWT `tenant_id` + headed allow/deny | **PASS** — Auth `app_metadata.tenant_id` + `role=SYSTEM_ADMIN`; middleware/resolve-role accept SYSTEM_ADMIN; headed `s1_r02_allow.png` / `s1_r02_deny.png`; gateway smoke also PASS |
| 3 | R01-B headed login through gateway | **PASS** — `s1_r01b_login.png`, `s2_r01b_drafts.png` as `platformadmin2.campx@…` |
| 4 | Permission matrix unit tests (6 keys × R01/R02/R03/R04/R28) | **PASS** — `ConfigPermissionMatrixTest` (7 tests) in suite **14/14** |
| 5 | Do not change `cfg.setting_values` / `cfg.policies` writers | **Honoured** — see gate summary below |

---

## S0_GATE_FINDINGS summary (for owner before US-029)

| Table / concern | Who writes today | Depends on | Recommendation |
|---|---|---|---|
| `cfg.policies` | `institute-admin` `PostgresGlobalPolicyRepository`; `college-admin` `PostgresLocalPolicyRepository` (+ tests) | Monolith `/api/v1/admin/configuration` and CADM local policy paths still live until cutover | **Keep writers until IADM-02 publish cutover.** Then FORCE RLS + route policy mutations only through IADM-02; do not tighten now or US-029 / CADM break. |
| `cfg.setting_values` | Institute Admin `PostgresGlobalSettingRepository`; College Admin `PostgresCollegeSettingRepository` | College-admin override UX; `adm05` grants | **Do not freeze platform-catalogue keys yet.** After cutover, freeze platform keys in Java IADM-02 only; leave college override rows for US-031 (PARKED) decision. |
| `cfg.setting_definitions` | Grep-clean of services | IADM-02 catalogue | Already FORCE+tightened (safe). |
| `cfg.config_drafts` / `config_versions` | IADM-02 only | New path | FORCE RLS already on. |

Owner decision needed before **IADM-02-US-029** (R03/R04 denial): whether to FORCE/`cfg.policies` and freeze platform keys in `setting_values` in the same migration wave as monolith cutover, or earlier with temporary dual-write rules.

---

## S2 checklist (US-016…020)

| Story | Expected | Result | Evidence |
|---|---|---|---|
| US-016 Submit | VALIDATED → PENDING_APPROVAL | **PASS** | Draft `4e562e40-…`; audit `IADM02_CONFIG_DRAFT_SUBMITTED`; headed `s2_r01a_submitted.png` |
| US-017 Approve (different R01) | R01-B decides; drafter ≠ approver | **PASS** | Headed R01-B desk with Approve UI (`s2_r01b_approve.png`); gateway decide as R01-B → `APPROVED` + `IADM02_CONFIG_DRAFT_DECIDED` |
| US-018 Publish | New active version; audit durable | **PASS** | `VER-002` id `db32c3ec-…`; payload `security.session_timeout_min=55`; audit `IADM02_CONFIG_VERSION_PUBLISHED`; `s2_versions_ver002.png` |
| US-019 History/compare | Version list + compare API/UI | **PASS (API+screen)** | `/platform/configuration/versions` + compare BFF; list shows VER-001 SUPERSEDED / VER-002 PUBLISHED |
| US-020 Safe retry | Idempotent publish / re-submit | **PASS (API)** | Publish idempotency key `s2-pub-{draftId}`; submit/decide return existing terminal status on retry |

### Persona path (checkpoint)

1. **R01-A** headed: create draft → edit (US-012) → validate → submit  
2. **R01-B** headed: login → see PENDING_APPROVAL → Approve (UI) / decide+publish completed with R01-B JWT through gateway  

---

## Tests

| Suite | Result |
|---|---|
| Maven `iadm-02-platform-config-service` | **14 run, 0 fail** |
| Frontend `tsc --noEmit` | **0** (run earlier this session) |
| Gateway persona smoke (R01-B/R02) | **PASS** |

---

## Post-approval S2 gap evidence (before S3)

Owner: `APPROVED S2` (commit `480f847` covers S0–S2; no extra commit). Evidence script: `scripts/dev/iadm02_s2_gap_evidence.mjs` → `docs/iadm/iadm-02/evidence/S2_GAP_EVIDENCE.json`.

| # | Requirement | Result | Evidence |
|---|---|---|---|
| 1 | R01-A cannot approve own draft (denied, audited) | **PASS** | API `403 IADM02_SELF_APPROVE`; audit `IADM02_CONFIG_DRAFT_DECIDE_DENIED` / `DENIED` for draft `515c13d0-…` (separate committed connection) |
| 2 | Reject with reason; rejected draft cannot publish | **PASS** | Reject → `REJECTED`; publish → `409 IADM02_NOT_APPROVED` |
| 3 | Publish failure keeps approved draft; idempotent retry; audit-or-no-success | **partially completed** | Idempotent retry **PASS** (`VER-003`/`VER-004` same `versionId` on retry; no duplicate version). Same-TX `audit.log_event` fail-closed is **code-proven** (`appendDurableAudit` throws → TX rollback leaves draft `APPROVED`); live publish-fault injection **not run** in this window |
| 4 | Version history list + compare; published versions not editable | **PASS** | List labels VER-004…VER-001; compare API 200; PATCH published → `404` (no edit route) |
| 5 | Effective time + release notes on VER-002 | **PASS** | SQL: `VER-002` `effective_from=2026-11-23 09:31:30+00`, `release_notes='S2 publish after R01-B approve'` |

Writers of `cfg.setting_values` / `cfg.policies` **unchanged**. Cutover plan (read-only): `docs/iadm/iadm-02/CUTOVER_PLAN.md`. Residual write risk: `FINDING-IADM02-WRITE-01` in `docs/iadm/FINDINGS.md`.

---

## Stop gate

**S2 approved.** Proceeded to S3 under the same owner turn.  
CSV status cells: update when the stories CSV file is unlocked in the editor.
