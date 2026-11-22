# Next Task

**STATUS: IADM implementation rolled back to RBAC Phase 4 state (2026-11-22). Docs retained. Do NOT start IADM or CADM implementation.**

## This Run Summary (2026-11-22) — IADM Rollback

| Plain name | Code / ref | Status |
|---|---|---|
| Owner-approved IADM rollback | Owner Niranjan V.G. 2026-11-21 | **completed** — `docs/iadm/ROLLBACK_REPORT.md` |
| Backup tag/branch | `backup-iadm-before-rollback` / `backup/iadm-before-rollback` | tip `dc4063f` (snapshot before rollback) |
| Code revert | `fac65de` | IADM Waves A–F, UI-1, Slice 1, demo catalog removed |
| Security keep (context leak) | `ec6057c` | K2 re-applied |
| DB rollback DEV | migrations `000200`…`120000` reverse; **skip K1 `000100`** | objects gone; K1 `auth.uid()` kept |
| RBAC Phases 0–4 | `0353f6e` and earlier | **unchanged / stay** |

## Spec for the next IADM attempt (when re-authorized)

Follow **`docs/user-stories/IADM_Rework_Desks_Journeys_and_Stories_v2_DRAFT.md`** — desks, demo data, stricter definition of done.  
**CADM waits.** Do not start any IADM or CADM implementation from this task.

## RBAC Phase 4 baseline (still closed)

Enterprise RBAC Matrix v3.0 Phases 0–4 remain the live DEV security baseline. Evidence: Phase reports under `docs/audits/rbac-implementation/`; post-rollback verify scripts PASS (see ROLLBACK_REPORT).

## Rules

No production/staging DB writes. No push to remote. No new IADM modules/ports until a new owner task authorizes the rework plan.
