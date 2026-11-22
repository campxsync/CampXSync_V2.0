# Next Task

**STATUS: IADM-01 Slice S1 — CHANGES addressed (Type/Plan dropdowns + Primary contact). STOP again for `APPROVED S1` or further `CHANGES S1`. IADM-02…08 and CADM out of scope.**

## This run

| Plain name | Code / ref | Status |
|---|---|---|
| Owner authorization | IADM-01 slices S0–S4 DEV only | **active** |
| Phase 0 report | `docs/iadm/IADM01_PHASE0_REPORT.md` | **completed** |
| S1 demo report | `docs/iadm/SLICE_S1_DEMO_REPORT.md` | **written — STOP** |
| Register → fail invite → retry → ACTIVE | institute `S1F175540` | **PASS (API/BFF)** |
| Durable audit | `audit.events` via `audit.log_event` | **PASS (SQL)** |
| R03 invite login landing | `/institution` home | **PARTIAL — owner confirm** |
| Next after approval | Slice S2 colleges/handover | **blocked on APPROVED S1** |

## Spec

- Work list: `docs/user-stories/IADM-01_User_Stories.csv`
- Design: external `CampXSync_IADM-01_Detailed_Blueprint_V2.1.md` (see Phase 0 DOC finding)
- Fixed decisions in owner task (tenant=institute; no college in Platform wizard; Auth-first R03; etc.)

## Rules

No production/staging DB writes. No push. No IADM-02…08 or CADM implementation in this authorization.
