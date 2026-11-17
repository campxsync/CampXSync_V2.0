# STM-07 batch 4 — owner decisions follow-up (2026-11-17)

**Branch:** `feature/java-backend-integration`  
**No push.**

## Decision 1 — Pilot Registrar Auth metadata + role assignment (APPROVED with limits)

- **Project confirmed:** `azulbaxszsqxymqmlwfe` before writes.
- Auth metadata evidence: `docs/reports/STM07_REGISTRAR_PILOT_APP_METADATA_BEFORE_AFTER_2026-11-17.md`
- Role-assignment evidence: `docs/reports/STM07_REGISTRAR_ROLE_ASSIGNMENT_BEFORE_AFTER_2026-11-17.md`
- Runbook restore of assignment `…000032`: **row already present** (`ON CONFLICT DO NOTHING` no-op).
- **Root cause of `GATEWAY_NO_ROLE_ASSIGNED`:** not a missing assignment row; API Gateway `JdbcIdentityResolver` does not treat `REGISTRAR` as a recognized business role. Live test cleanups do not delete the pilot row. Tests not changed.
- **Demo re-run:** still **FAIL** with same gateway code until Registrar is recognized (question for owner).

## Decision 2 — Deny demo / College Admin path

College Admin does **not** share the STM-07.ACCOUNT.A delegation check for staff link-status (`assertCanViewStaffAccountLink`). Browser deny case not run; service test `stm07Us003_collegeAdminWithoutBeingOperatorDenied` covers it. Pilot M1 not revoked.

## Decision 3 — Platform Admin (R01) college selector

**No selector.** Staff desks are for Institution Admin (R03) and delegated Registrar.  
**R02 / R03 test accounts:** none in `.env.audit`; none found as active holders on Dev. Demo guide notes Platform Admin cannot use staff desks until a separate story is approved.

## Decision 4 — Announcements publishers (CONFIRMED)

Publishers: **College Admin** and **Communication Manager** (not Registrar).  
Under `services/` only `communication-services/notification-service/.gitkeep` exists → **STM-10-US-023 BLOCKED** (depends on a service that does not exist). Build nothing. CSV update prepared in `.tmpwrite` while the open CSV file was locked by the editor.

## Known-failures list addition

- **student-service 1 error appears only with `CAMPX_PERSISTENCE_MODE=postgres`** (live RLS on `core.persons` / conflict→409 path). Clean env without that mode: full suite 184/0.
