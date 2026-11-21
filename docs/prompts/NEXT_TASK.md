# Next Task

**STATUS: CADM implementation — reconcile CADM stories, then Waves A–D (needs IADM contracts).**  
Owner: Niranjan V.G. Prior IADM Waves A–D approval was 2026-11-21 and is now closed for this branch (local only; no push).

## Completed this run (2026-11-21) — IADM

| Plain name | Code | Status / Evidence |
|---|---|---|
| Step 0 Preflight | Step 0 | **completed** — `docs/iadm/STEP0_REPORT.md`, `docs/user-stories/IADM_User_Stories.md` |
| Appointment fn hardening (`auth.uid()`) | IADM-01-US-002 / 0.1 | **completed** — `20261121000100_iadm_assign_institution_admin_auth_uid.sql` |
| Wave A: Institute lifecycle + Platform config | IADM-01 / IADM-02 | **completed** — ports 8099/8100; `WAVE_A_REPORT.md` |
| Wave B: Identity & Access + Audit/Ops | IADM-03 / IADM-08 | **completed** — ports 8101/8106; `WAVE_B_REPORT.md` |
| Wave C: Data Governance + Security | IADM-05 / IADM-07 | **completed** — ports 8103/8105; `WAVE_C_REPORT.md` |
| Wave D: Billing/Entitlements + Analytics | IADM-04 / IADM-06 | **completed** — ports 8102/8104; `WAVE_D_REPORT.md` |
| Inventory | — | `docs/iadm/FILE_LIST.md`, `docs/iadm/FINDINGS.md` |

## IADM contracts CADM may call

1. First College Admin + handover (IADM-01).
2. Effective configuration resolve platform > institute > college (IADM-02).
3. Role ceiling lookup (IADM-03).
4. Shared audit read (IADM-08).
5. Entitlement check `plat.check_entitlement` / `GET /api/v1/iadm/04/entitlements/check` (IADM-04) — fail closed [CONFIRMED].

## This next task (CADM)

1. Reconcile CADM draft/legacy stories into a final backlog (same pattern as IADM).
2. Implement CADM Waves A–D in DEV only, consuming the IADM contracts above.
3. No production; no push; local commits per story group on `feature/java-backend-integration`.

## Rules

No production/staging DB writes. No push to remote. Do not invent PLATFORM_ADMIN/SYSTEM_ADMIN role_template codes (FINDING-IADM-RBAC-01).
