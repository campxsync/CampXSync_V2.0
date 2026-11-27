# IADM-03 Platform Identity & Access Governance — Final Report

**Date:** 2026-11-27  
**Branch:** `feature/java-backend-integration` (local commits only; **not pushed**)  
**Database Target:** DEV Supabase (`azulbaxszsqxymqmlwfe`)  
**Service / Port / Route:** `iadm-03-identity-access-service` · Port `8101` · Route `/api/v1/iadm/03`  
**Owned Schema:** `iadm03` (6 tables, all with `ENABLE` + `FORCE ROW LEVEL SECURITY` and `DELETE` denied)

---

## 1. Before / After Catalogue Snapshot (Boundary 3 & 8 Proof)

Captured from DEV Supabase (`azulbaxszsqxymqmlwfe`) in `docs/iadm/iadm-03/evidence/s0_catalogue_snapshot.json` and `docs/iadm/iadm-03/evidence/v2/final_db_verification.json`:

| Metric | Before | After | Diff |
|---|---:|---:|---|
| Non-`iadm03.*` active permission keys (`iam.permissions`) | `663` | `663` | `0` (SHA-256 `3e9f16d43435c59ad307ad540816c2a0e0f08731ea31f88b910d9b691d497379` identical) |
| Non-`iadm03.*` template grants (`iam.role_template_permissions`) | `2223` | `2223` | `0` (SHA-256 `81f23604bbca64b0dbd2e3df9323cd32c5b838156ff92103462024be6b443b5c` identical) |
| Additive `iadm03.*` keys (`iam.permissions`) | `0` | `12` | `+12` additive only |
| Legacy `adm01.*`–`adm08.*` keys (`47` keys) | `47` | `47` | Untouched |

---

## 2. Coexistence of Legacy V1.4 & V2.0 (D-03 Owner-Open)

- **Prior state:** IADM-03 V1.4 already existed on port `8101` (`/api/v1/iadm/03`) prior to this kickoff. V2.0 now **coexists** with V1.4 until the owner decides **D-03 (apply versus record-only)** deliberately. V2 code never calls legacy V1.4 services, and legacy V1.4 services never write to V2 `iadm03.*` tables.
- **Legacy V1.4 inventory (kept until D-03 is resolved):**
  - **Tables:** `iam.governance_idempotency`, `iam.access_requests`, `iam.catalog_change_requests`, `iam.access_reviews`, `iam.access_review_items`, `iam.delegations`.
  - **Keys:** 20 `iam.*` keys (labeled **`IADM-03 V1.4 (legacy)`** in the V2 permission-key register).
  - **Restored Legacy V1.4 Endpoints (`/api/v1/iadm/03`):** `POST /grants/appoint-r03`, `POST /grants/assign`, `POST /grants/revoke`, `POST /grants/r03-change/{id}/decide`, `GET /catalog-changes`, `POST /catalog-changes`, `POST /catalog-changes/{id}/decide` (plus existing V1.4 `/profiles/**`, `/access-requests/**`, `/delegations/**`, `/access-reviews/**`).
  - **Legacy V1.4 Pages:** `/institution/access/**` and `/platform/identity/catalog-changes`, `/platform/identity/requests`, `/platform/identity/profiles/**`, `/platform/identity/technical`.
- **V2.0 inventory (record-only, `iadm03` schema):**
  - **12 additive `iadm03.*` keys:**
  - `iadm03.register.view` (Super Admin `R01`, System Admin `R02`, Auditor `R28`)
  - `iadm03.matrix.view` (Super Admin `R01`, System Admin `R02`, Auditor `R28`)
  - `iadm03.holders.view` (Super Admin `R01`, System Admin `R02`, Institution Admin `R03` own institute, Auditor `R28`)
  - `iadm03.grant.request.submit` (Super Admin `R01`, System Admin `R02`, Institution Admin `R03` own institute, College Admin `R04` own college)
  - `iadm03.grant.review` (Super Admin `R01`, System Admin `R02`)
  - `iadm03.grant.approve` (Super Admin `R01` only)
  - `iadm03.review.manage` (Super Admin `R01`, System Admin `R02`)
  - `iadm03.delegation.manage` (Super Admin `R01`, Institution Admin `R03` own institute)
  - `iadm03.registration.submit` (Super Admin `R01`, System Admin `R02`)
  - `iadm03.registration.approve` (Super Admin `R01` only)
  - `iadm03.handover.view` (Super Admin `R01`, System Admin `R02`, Institution Admin `R03` own institute, Auditor `R28`)
  - `iadm03.evidence.view` (Super Admin `R01`, System Admin `R02`, Auditor `R28`)
- **V2.0 API endpoints (`/api/v1/iadm/03`):**
  - `GET /health`, `GET /register`, `GET /register/{key}/holders`, `GET /matrix`, `GET /evidence`
  - `GET /grant-requests`, `POST /grant-requests`, `POST /grant-requests/{id}/validate`, `POST /grant-requests/{id}/decision`
  - `GET /v2/access-reviews`, `POST /v2/access-reviews`, `POST /v2/access-reviews/{id}/items/{itemId}/decision`
  - `GET /v2/delegations`, `POST /v2/delegations`, `GET /v2/delegations/{id}/status`, `POST /v2/delegations/{id}/revoke`
  - `GET /service-registrations`, `POST /service-registrations`, `POST /service-registrations/{id}/validate`, `POST /service-registrations/{id}/decision`
  - `GET /handover/first-admin`
- **Shared audit events (`audit.log_event` -> `audit.events`):**
  - `IADM03_REGISTER_READ`, `IADM03_REGISTER_DENIED`, `IADM03_HOLDERS_READ`, `IADM03_HOLDERS_DENIED`, `IADM03_MATRIX_READ`, `IADM03_MATRIX_DENIED`
  - `IADM03_GRANT_REQUEST_SUBMITTED`, `IADM03_GRANT_REQUEST_VALIDATED`, `IADM03_GRANT_DECISION_RECORDED`, `IADM03_GRANT_REQUEST_REJECTED`
  - `IADM03_SELF_GRANT_DENIED`, `IADM03_SELF_APPROVAL_DENIED`, `IADM03_SCOPE_ESCALATION_DENIED`, `IADM03_CROSS_TENANT_DENIED`, `IADM03_CEILING_DENIED`
  - `IADM03_REVIEW_CAMPAIGN_CREATED`, `IADM03_REVIEW_ITEM_DECIDED`, `IADM03_DELEGATION_CREATED`, `IADM03_DELEGATION_REVOKED`
  - `IADM03_REGISTRATION_SUBMITTED`, `IADM03_REGISTRATION_VALIDATED`, `IADM03_REGISTRATION_DECIDED`, `IADM03_HANDOVER_READ`

---

## 3. All 25 User Stories — Target vs Actual Status & Named Evidence

| Story ID | Title (Plain Meaning) | Target | Actual | Named Evidence |
|---|---|---|---|---|
| `IADM-03-US-016` | IAM inventory and preflight | API-ready | **Completed** | `docs/iadm/iadm-03/IAM_INVENTORY.md` (live DEV `azulbaxszsqxymqmlwfe` read-backs) |
| `IADM-03-US-017` | IADM-03 service scaffold | API-ready | **Completed** | `IdentityAccessScaffoldTest` (`:8101`, `/api/v1/iadm/03`), `docs/iadm/iadm-03/TECHNICAL_DOCUMENTATION.md` |
| `IADM-03-US-018` | IADM-03 permission keys (`iadm03.*`) and role mapping | API-ready | **Completed** | `20261127010000_iadm03_v2_keys_and_schema.sql`, `Iadm03V2S0S1Test` (45/45 JUnit pass) |
| `IADM-03-US-019` | Schema, tenant scoping and FORCE Row-Level Security | API-ready | **Completed** | `s0_catalogue_snapshot.json` (6 `iadm03.*` tables `relforcerowsecurity=true`), `rls_two_institutes.json` (`t1OwnCount=2, t2CrossReadT1Count=0`) |
| `IADM-03-US-020` | Shared audit integration with refusal | API-ready | **Completed** | `DurableAuditWriter.java`, `Iadm03V2S0S1Test.demoAuditDisabledSwitchFailsClosed`, 20 `IADM03_*` rows in `final_db_verification.json` |
| `IADM-03-US-021` | Catalogue, holders and role-matrix read engine | API-ready | **Completed** | `CatalogueReadService.java` (20 `iam.*` keys labeled `IADM-03 V1.4 (legacy)`), `v14_restore_result.json` (`allLabeledLegacy: true`) |
| `IADM-03-US-001` | Inspect the permission-key register (R01, R02) | Demo-verified | **Demo-verified** | `01_r01_register.png` (`Live catalogue total: 675 · Reconciled: YES`), `v14_restore_result.json` (`iamCount: 20, allLabeledLegacy: true`) |
| `IADM-03-US-002` | Inspect the role-to-permission matrix (R01, R02, R28) | Demo-verified | **Demo-verified** | `02_r01_matrix.png` (39 roles `R01`–`R39`, `RBAC v3.0`, flagged differences), `results.json` |
| `IADM-03-US-012` | Auditor (R28) read-only access evidence | Demo-verified | **Demo-verified** | `12_r28_readonly.png`, `results.json` (`PASS \| US-012 R28 Read-Only \| mutate=403`) |
| `IADM-03-US-015` | Runtime enforcement unaffected by IADM-03 | Demo-verified | **Demo-verified** | `13_us015_iadm06_with_iadm03_stopped.png`, `us015_result.json` (`r01_iadm06: 200, r04_iadm06: 403` with `:8101` stopped) + `GatewayConfig.java` |
| `IADM-03-US-022` | Grant request workflow engine (record-only) | API-ready | **Completed** | `RecordOnlyGrantWorkflowService.java`, `Iadm03V2FailureAndIsolationTest.v2GrantWorkflowNeverMutatesCatalogueOrGrants` & `restoredLegacyEndpointsAreMarkedLegacyAndIsolatedFromV2` |
| `IADM-03-US-005` | System Admin (R02) validate and prepare diff | Demo-verified | **Demo-verified** | `07_r02_validate_and_approve_deny.png`, `results.json` (`val=200, catalogueTouched=false, approve=403`) |
| `IADM-03-US-003` | Institution Admin (R03) view and request own institute | Demo-verified | **Demo-verified** | `10_r03_institute_scope.png`, `results.json` (`matrix=403, crossHandover=403`), `rls_two_institutes.json` |
| `IADM-03-US-004` | College Admin (R04) submit access request only | Demo-verified | **Demo-verified** | `11_r04_request_only.png`, `results.json` (`submit=200, register=403`) |
| `IADM-03-US-006` | Super Admin (R01) approve or reject another account's request | Demo-verified | **Demo-verified** | `08_r01b_approve_recorded.png`, `results.json` (`status="RECORDED"`, `statement="recorded, not applied"`, `auditEventId="40e8bbf4-243c-4d57-9c43-82496f53c272"`) |
| `IADM-03-US-007` | Reject self-grant, self-approval and scope escalation | Demo-verified | **Demo-verified** | `Iadm03V2FailureAndIsolationTest.ceilingMatrixCoversAllGrantorRows`, `GrantorCeilingDenialTest`, `results.json` (`R02 approve=403, R03 crossHandover=403`) |
| `IADM-03-US-013` | Refuse when audit cannot be recorded | Demo-verified | **Demo-verified** | `08_r01b_approve_recorded.png`, `results.json` (`statuses=503,503,503,503,503,503, recovered=200` across all 6 governed ops) |
| `IADM-03-US-008` | Run an access review campaign | Demo-verified | **Demo-verified** | `04_r01_reviews.png`, `results.json` (`Created campaign 19e1d8ae-8172-4067-9432-b5af26cc783a (10 items, DEMO_DEFAULT_90D) \| items=10`, Attest + Revoke + Exception) |
| `IADM-03-US-009` | Create a recorded, scoped, time-bound delegation | Demo-verified | **Partial (by design)** | `05_r01_delegations.png` (UI create `ACTIVE`, `REVOKED`, `EXPIRED`); partial by design because no consuming service reads delegations yet |
| `IADM-03-US-010` | Revoke or expire a delegation | Demo-verified | **Partial (by design)** | `05_r01_delegations.png` (`REVOKED (valid=false)` and `EXPIRED (valid=false)` on read); partial by design until a consuming service adopts it |
| `IADM-03-US-011` | Register a new service's permission keys | Demo-verified | **Demo-verified** | `06_r01_registrations.png` (`IADM-09-DEMO · APPROVED`), `us011_result.json` (`sub: 200, val: 200, dec: 200, appliedMigration: false`) |
| `IADM-03-US-014` | View first-admin handover status | Demo-verified | **Demo-verified** | `03_r01_handover.png`, `results.json` (`PASS \| US-014 R01 Handover \| rows=1` from `plat.college_cadm_handovers`) |
| `IADM-03-US-023` | Demo data check (no invention) | Demo-verified | **Completed** | Reused existing `.env.audit` + `seed_accounts.json` (`r01a`, `r01b`, `r02`, `r03demo`, `auditor`, `COLLEGE_ADMIN`); zero invented emails |
| `IADM-03-US-024` | Failure, isolation and role test suite | API-ready | **Completed** | `Iadm03V2FailureAndIsolationTest` + full 10-class JUnit suite: **45/45 PASS**; `frontend npx tsc --noEmit` **0 errors** |
| `IADM-03-US-025` | Evidence pack and close-out | Demo-verified | **Completed** | `IADM-03_FINAL_REPORT.md`, `s0_catalogue_snapshot.json`, `final_db_verification.json`, `rls_two_institutes.json`, `us015_result.json`, `us011_result.json`, 12 headed PNGs + `.webm` video |

---

## 4. Decisions D-01 to D-12 (Build Assumptions)

- **D-01 (No per-request runtime call):** IADM-03 is never called per request by Gateway or other services.
- **D-02 (Computed views):** Register, holders, and role matrix are computed live on request, never stored.
- **D-03 (Apply vs record-only):** Owner-open. V2 (`iadm03.*`) is strictly record-only (`status = RECORDED`, `"recorded, not applied"`); legacy V1.4 apply endpoints are restored and isolated until D-03 is decided deliberately.
- **D-04 (Key family & roles):** 12 additive `iadm03.*` keys mapped to R01/R02/R03/R04/R28 only; zero wildcards; `R04` request-only confirmed in RBAC v3.0 §8.
- **D-05 (Review cadence):** No review cadence setting found in IADM-02 (`cfg.setting_definitions`); used `DEMO ONLY` runtime default `90` days (`DEMO_DEFAULT_90D`), reviewers R01 and R02.
- **D-06 (Delegation storage & max validity):** Owned `iadm03.delegations` table with `FORCE RLS`; reads `iam.delegation.max_validity_days` from IADM-02 when available, else `DEMO ONLY` default `90` days; partial by design (no consuming service yet).
- **D-07 (Catalogue ownership):** Prefix derivation (`iadm01..08` -> `IADM-01..08`, `adm*` -> `Legacy`, else `Unknown`), reconciled live (`663` + `12` = `675`).
- **D-08 (Handover):** Read-only from existing `plat.college_cadm_handovers`; no duplicate handover table or appointment mechanism created.
- **D-09 (Service registration):** Additive manifest intake + R02 validate + R01 approve (`appliedMigration = false`).
- **D-10 (Shared audit):** Reuses `audit.log_event` / `audit.events` exclusively; fails closed (`503 IADM03_AUDIT_UNAVAILABLE`) on audit failure.
- **D-11 (Demo data):** Existing DEV institutes and personas only (`r01a`, `r01b`, `r02`, `r03demo`, `auditor`, `COLLEGE_ADMIN`).
- **D-12 (Port, route, schema):** Kept wired port `8101` and `/api/v1/iadm/03`; created `iadm03` schema with `FORCE RLS`.

---

## 5. Git Status (Local Commits Only — Not Pushed)

- Branch: `feature/java-backend-integration`
- S0: `2c348a4` — `feat(iadm03): S0 foundation — IAM inventory, additive iadm03.* keys, FORCE RLS schema, fail-closed audit`
- S1: `30ed281` — `feat(iadm03): S1 read engine — permission register, holder lookup, R01-R39 matrix, R28 read-only UI`
- S2: `2892540` — `feat(iadm03): S2 record-only grant workflow — submit, R02 validate diff, R01 approve/reject, self/scope/audit refusals`
- S3: `03def57` — `feat(iadm03): S3 reviews, delegations, and additive service registrations`
- S4: `61d2f53` — `feat(iadm03): S4 handover, failure/isolation suite, headed evidence pack, and final report`
- Close-out fix & verification commit: committed locally on `feature/java-backend-integration` (**not pushed**).
