# Next Task

**STATUS: IADM-01 (Institute & Tenant Lifecycle) S0–S4 authorized for DEV (2026-11-22). Owner: Niranjan V.G. IADM-02…08 and CADM are out of scope.**

## This run (in progress)

| Plain name | Code / ref | Status |
|---|---|---|
| Owner authorization | IADM-01 slices S0–S4 DEV only | **active** |
| Phase 0 report | `docs/iadm/IADM01_PHASE0_REPORT.md` | **completed** (IADM01-TECH-001) |
| Foundation migration | `20261122010000_iadm01_lifecycle_foundation` (+ MCP apply) | **applied on DEV** |
| IADM-01 service | port **8099**, `/api/v1/iadm/01` | **scaffolded + compiling**; RBAC unit tests 7/7 |
| Gateway route | `GatewayConfig` `/api/v1/iadm/01` → `:8099` | **registered** |
| Stack scripts | start/check/restart know `iadm-01` | **updated** |
| S1 wizard / R03 home | Platform onboard + `/institution` | **in progress** — demo report not yet written |
| Owner checkpoint | After `SLICE_S1_DEMO_REPORT.md` | **await APPROVED S1** (not reached yet) |

## Spec

- Work list: `docs/user-stories/IADM-01_User_Stories.csv`
- Design: external `CampXSync_IADM-01_Detailed_Blueprint_V2.1.md` (see Phase 0 DOC finding)
- Fixed decisions in owner task (tenant=institute; no college in Platform wizard; Auth-first R03; etc.)

## Rules

No production/staging DB writes. No push. No IADM-02…08 or CADM implementation in this authorization.
