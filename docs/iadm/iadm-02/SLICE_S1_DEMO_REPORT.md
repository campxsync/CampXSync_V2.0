# IADM-02 Slice S1 Demo Report

**Date:** 2026-11-23  
**Owner approval gate:** STOP for `APPROVED S1` or `CHANGES S1: <feedback>`  
**Owner authorization:** Niranjan V.G. — IADM-02 S0 decisions D1/D3/D6/D7/D9/D10 + build US-002…007 + continue through S1 (DEV only, no push)  
**DEV project:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)  
**Plain language:** Prove Platform Operator (R01) can open the configuration catalogue, create and validate drafts (pass + fail), view and propose policy drafts; prove R02 can draft only `security.session_timeout_min`.

---

## Environment (CONFIRMED)

| Check | Result |
|---|---|
| IADM-02 `:8100` health | UP (`iadm-02-platform-config-service`) |
| API Gateway `:8080` | UP; route `/api/v1/iadm/02/**` |
| Next.js `:3000` | HTTP 200 |
| Monolith `/api/v1/admin/configuration` | Unchanged (left running; no cutover) |
| Persona | R01 `platformadmin.campx@gmail.com` (headed); R02 via HMAC smoke |

---

## Checklist results

| Step | Persona | Expected | Result | Evidence |
|---|---|---|---|---|
| US-008 / US-010 catalogue + dashboard | R01 | 5 keys; active VER-001; pending + validation counters | **PASS** | Headed UI; `s1_catalogue.png` — pending **4**, validation failures **1**, keys include CFG-PLATFORM-001/002 + `iam.delegation.max_validity_days` |
| US-009 key detail | R01 | Detail for `security.session_timeout_min` | **PASS** | Create-draft form used in headed demo |
| US-011 create draft | R01 | Reason required; draft id; audit | **PASS** | Draft `ec2bde4c-cc00-46bb-86a6-9b19c47d1a53` (`security.session_timeout_min`=45); audit `IADM02_CONFIG_DRAFT_CREATED` |
| US-013 validate DRAFT-001 path | R01 | Valid value → VALIDATED | **PASS** | Demo draft `ec2bde4c-…` → **VALIDATED**; seed DRAFT-001 already VALIDATED |
| US-013 validate DRAFT-002 | R01 | Invalid `attendance.min_pct=150` stays invalid | **PASS** | UI: “does not match the required type or bounds (0..100)”; status remains **DRAFT**; audit `IADM02_CONFIG_DRAFT_VALIDATED` |
| US-014 policy catalogue + draft | R01 | List baseline policy; propose draft | **PASS** | `IADM02_POL_PLATFORM_BASELINE` listed; policy draft `bb3fc79a-1afb-45e4-93fe-93c7e6fda63b`; audit `IADM02_POLICY_DRAFT_CREATED`; `s1_policies.png` |
| US-015 R02 ceiling | R02 | Deny non-session keys; allow `security.session_timeout_min` | **PASS** | `node scripts/dev/iadm02_r02_ceiling_smoke.mjs` — deny `attendance.min_pct` **403** `IADM02_R02_CEILING`; allow session timeout **201** draft `cd586c85-…` |
| US-012 edit draft | R01 | PATCH unpublished draft | **completed** (post-approval) | Edit/Save on drafts desk; headed `s1_us012_edit.png`; audit `IADM02_CONFIG_DRAFT_UPDATED` |
| Durable audit (D9) | — | No success without audit row | **PASS** | `audit.events` rows for create/validate/policy (see SQL below) |

### Screenshots

| File | What |
|---|---|
| `docs/iadm/iadm-02/evidence/s1_catalogue.png` | Catalogue + dashboard counters |
| `docs/iadm/iadm-02/evidence/s1_drafts_validate.png` | Drafts list / validate flow |
| `docs/iadm/iadm-02/evidence/s1_policies.png` | Policy catalogue + draft created |

### Audit rows (CONFIRMED SQL, DEV)

| action | status | resource_id (prefix) |
|---|---|---|
| `IADM02_CONFIG_DRAFT_CREATED` | SUCCESS | `ec2bde4c-…` (R01 headed) |
| `IADM02_CONFIG_DRAFT_VALIDATED` | SUCCESS | `0bedc23e-…` (DRAFT-002 invalid) |
| `IADM02_CONFIG_DRAFT_VALIDATED` | SUCCESS | `ec2bde4c-…` (valid) |
| `IADM02_POLICY_DRAFT_CREATED` | SUCCESS | `bb3fc79a-…` |
| `IADM02_CONFIG_DRAFT_CREATED` | SUCCESS | `cd586c85-…` (R02 allow) |

---

## S0 build carried into this checkpoint (US-002…007)

| Item | Status |
|---|---|
| Service `:8100` + gateway path | completed |
| Permissions `cfg.catalog.view\|draft\|approve\|publish\|policy.draft\|consumer.read` | completed (no wildcard); counts in `evidence/PERMISSION_COUNTS_*.json` |
| Tables `cfg.config_drafts` + `cfg.config_versions` FORCE RLS | completed |
| `setting_definitions` FORCE + tighten | completed after grep-clean gate |
| `cfg.policies` / `setting_values` freeze | **not applied** (gate: other writers) — see `S0_GATE_FINDINGS.md` |
| D7 `iam.api_clients` seed-only | completed; no create/elevate UI |
| Seed US-007 (2× R01, R02, consumer, VER-001, DRAFT-001/002) | completed |
| Port / schemaVersion / freshness contract | as owner-approved |

### Migrations (DEV)

| Version | File |
|---|---|
| `20261123010000` | `iadm02_foundation.sql` + rollback |
| `20261123010100` | `iadm02_demo_seed.sql` + rollback |

---

## Automated tests

| Suite | Result |
|---|---|
| `iadm-02-platform-config-service` Maven | **7 run, 0 fail** |
| Frontend `npx tsc --noEmit` | **0 errors** |
| R02 ceiling smoke | **PASS** |

---

## Findings (non-blocking for S1 stop)

1. **FINDING-IADM02-GW-R02-01:** Gateway JWT for seeded R02 rejects with `GATEWAY_MALFORMED_JWT` (missing `tenant_id` in `app_metadata`). Ceiling proven via HMAC → `:8100`. Browser test-session has no R02 persona yet.
2. **FINDING-IADM02-UI-012:** Draft edit is API-ready; drafts desk is validate-focused only.
3. Monolith configuration routes intentionally untouched until owner cutover after publish works (S2+).

---

## Story readiness (CSV Implementation status)

| Story | Status |
|---|---|
| IADM-02-US-002…007 | completed (S0 build) |
| IADM-02-US-008…011, 013…015 | completed |
| IADM-02-US-012 | completed |
| S2+ (approve/publish/consumer) | **not started** — awaiting `APPROVED S1` |

Note: in-place write to `CampXSync_IADM-02_User_Stories.csv` failed with `EBUSY` (file locked in editor). Statuses above + `COMPLETION_REPORT.md` are authoritative until the CSV can be updated.

---

## Stop gate

Do **not** start S2 (approve / publish / consumer pull) until the owner replies:

- `APPROVED S1` — proceed to prepare S2  
- `APPROVED S1 COMMIT` — local commit then prepare S2  
- `CHANGES S1: <feedback>` — rework  

**No push.** DEV only.
