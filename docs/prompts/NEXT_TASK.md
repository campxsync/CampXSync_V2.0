# Next Task

**STATUS: IADM Rework Step 0 complete → Slice 1 in progress. Stop after Slice 1 for owner pattern confirmation.**

**Owner:** Niranjan V.G. — Rework approved 2026-11-21 (DEV only). Spec: `docs/user-stories/IADM_Rework_Desks_Journeys_and_Stories_v2_DRAFT.md`.

## Just completed

| ID | Status |
|---|---|
| Wave UI-1 desks | Snapshot commit `ef3b93d` |
| Rework Step 0 | D1–D7 fixes, screen inventory, demo seed migration `20261121120000`, reports |

## Now

**Slice 1 — Platform Operator Desk:** IADM-PD-01…PD-06 + Journey J1 (onboard institute → appoint Institution Admin R03 with two-person approval → admin can log in). Then write `SLICE1_DEMO_REPORT.md`, commit, **STOP**.

## Still deferred

| ID | Meaning |
|---|---|
| IADM-05-US-007 | Under-18 guardian consent (legal) |
| R27 / adm03 / Kafka | Unchanged |

## Rules

DEV only. UI → BFF → Gateway → Java → Supabase. Local commits; no push unless asked.
