# Next Task

**STATUS: IADM Waves A–D in progress (owner approval 2026-11-21). Wave A complete locally. No push.**

## This Run Summary (2026-11-21)

| Plain name | Code | Status / Evidence |
|---|---|---|
| Step 0 Preflight | Step 0 | **completed** — `docs/iadm/STEP0_REPORT.md`, `docs/user-stories/IADM_User_Stories.md` |
| Appointment fn hardening (`auth.uid()`) | IADM-01-US-002 / 0.1 | **completed** — `20261121000100_iadm_assign_institution_admin_auth_uid.sql` |
| Wave A: IADM-01 + IADM-02 | Wave A | **completed** — `docs/iadm/WAVE_A_REPORT.md` (ports 8099/8100) |
| Wave B: IADM-03 + IADM-08 | Wave B | **in progress** |
| Wave C: IADM-07 + IADM-05 | Wave C | yet to pickup |
| Wave D: IADM-04 + IADM-06 | Wave D | yet to pickup |

## After Wave D

Set next task to: **CADM implementation: reconcile CADM stories, then Waves A–D (needs IADM contracts)**.

## Rules

No production/staging DB writes. No push to remote. Local commits on `feature/java-backend-integration` per story group.
