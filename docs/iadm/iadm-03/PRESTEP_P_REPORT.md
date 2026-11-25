# IADM-03 Pre-step P — US-033 + US-018

**Date:** 2026-11-25  
**Trigger:** IADM-04 kickoff (owner accepted S0; Pre-step P before S1)  
**Service:** `:8101` `/api/v1/iadm/03`  
**Environment:** DEV `azulbaxszsqxymqmlwfe`  
**Commit:** (see git log after `feat(iadm03): Pre-step P …`)

---

## Stories

| Story | API-ready | Screen-ready | Demo-verified | Notes |
|---|---|---|---|---|
| **US-033** Auditor (R28) read-only desk | **Yes** | **Yes** | **Yes** | Evidence desk + `GET /audit-evidence`; mutations denied `IADM03_AUDITOR_READONLY` / “Auditor access is read-only.” |
| **US-018** System Admin (R02) technical identities | **Yes** | **Yes** | **Yes** | Technical desk; SERVICE/TECHNICAL/SVC-* allowed by ceiling; INSTITUTION_ADMIN denied |

**US-040 / RBAC v3.0 Compliant badge:** **not flipped / not claimed.** Parked and later-integration stories unchanged.

---

## US-033 — what was built

- Migration `20261125100000_iadm03_audit_evidence_read.sql` — `audit.list_iadm03_evidence(limit)` SECURITY DEFINER, gated by `iam.audit.view` (D3 key already present).
- Java `AuditEvidenceService` + `GET /api/v1/iadm/03/audit-evidence`.
- Controller: all non-GET mutations call `assertNotAuditorMutation` → 403 `IADM03_AUDITOR_READONLY`.
- UI: `/platform/identity/evidence` (no grant/approve/revoke controls); BFF `/api/platform/identity/evidence`.
- Nav + returnTo + home redirect for Auditor include evidence desk.
- GrantorCeiling message aligned to “Auditor access is read-only.”

## US-018 — allow / deny cases listed and tested

| Action | Expected | Evidence |
|---|---|---|
| Assign `SERVICE` / `TECHNICAL` / `SVC-BATCH` | **Allow** (ceiling) | Unit `Us018TechnicalCeilingTest`; API ceiling evaluate |
| Assign `INSTITUTION_ADMIN` / `FACULTY` / `COLLEGE_ADMIN` | **Deny** | API + headed probe |
| Delegation decide / grant.assign / catalog approve keys | **No** on R02 matrix | Unit matrix |
| Headed technical desk | Visible for System Admin | `us018_technical_desk.png` |

`resolveUserRole` now returns `system_admin` for `plat.platform_admins.role_code = SYSTEM_ADMIN` (no longer collapses into Super Admin UI).

---

## Verification

| Check | Result |
|---|---|
| `mvn test` Us018 + Us033 + matrix + ceiling | **PASS** |
| `npx tsc --noEmit` (frontend) | **PASS** |
| `node scripts/demo/iadm03_prestep_p_headed.mjs` | **PASS** — `docs/iadm/iadm-03/evidence/prestep-p/PRESTEP_P_HEADED_RESULTS.json` |

### Evidence paths

- `docs/iadm/iadm-03/evidence/prestep-p/us033_evidence_desk.png`
- `docs/iadm/iadm-03/evidence/prestep-p/us033_mutation_denied.png`
- `docs/iadm/iadm-03/evidence/prestep-p/us018_technical_desk.png`
- `docs/iadm/iadm-03/evidence/prestep-p/us018_r03_denied.png`
- `docs/iadm/iadm-03/evidence/prestep-p/PRESTEP_P_HEADED_RESULTS.json`

---

## CSV status updates

| Story | New Implementation status |
|---|---|
| IADM-03-US-033 | **completed** |
| IADM-03-US-018 | **completed** |
| IADM-03-US-040 | unchanged (**partially completed** — badge still withheld) |
