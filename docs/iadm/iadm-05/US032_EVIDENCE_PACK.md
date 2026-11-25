# IADM-05-US-032 — Evidence pack and close-out

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Service:** IADM-05 Data Governance & Privacy (`:8103` / `/api/v1/iadm/05`)

Full S4 narrative: `docs/iadm/IADM-05_S4_Report.md`.

## Commits (local only — not pushed)

| Hash | One-line label |
|---|---|
| `d3c2e74` | S0 scaffold — service, keys, gov FORCE RLS, shared audit |
| `854d8f1` | S1 policy lifecycle, DEMO seed, governance desks |
| `2b566df` | S2/S3 request queues, DEMO simulator seeds, privacy gate, evidence pack |
| `5bad1b0` | docs — S2/S3 commit hash and progress/handover |
| `6d31085` | S4 desks ACL, failure suite, headed US-032, G-15 harden — **APPROVED S4 COMMIT** |

## Automated tests

| Suite (plain meaning) | Result |
|---|---|
| `GovernancePermissionMatrixTest` — offline role×key denials (US-023) | PASS |
| `GovernanceAuditFailClosedTest` — audit unavailable refuses action (US-025) | PASS |
| `GovernanceFailureTableTest` — failure-table + R05–R39 sample + log-leak (US-031) | PASS |
| `npx tsc --noEmit` — Frontend TypeScript typecheck | PASS (0 errors) |
| `iadm05_s4_api_smoke.mjs` — live R03/R04/R28/privacy/faculty | 10/10 PASS |

## Headed persona runs

Runner: `node scripts/demo/iadm05_headed.mjs`  
Evidence dir: `docs/iadm/iadm-05/evidence/us032/`  
**Result: 21/21 PASS** (2026-11-25T10:14:16Z)

| Persona code | Plain role | Desk | Status |
|---|---|---|---|
| R01 | Super Admin | `/platform/governance` | PASS — approve, publish, export decide, erasure close attempt |
| R02 | System Admin | `/platform/governance` | PASS — propose, submit, export; approve 403 FORBIDDEN |
| R03 | Institution Admin | `/institution/governance` | PASS — adherence seeded; isolation; write 403 |
| R04 | College Admin | `/college/governance` | PASS — view; write 403 |
| R28 | Auditor | `/platform/governance/audit` | PASS — evidence view; write/approve 403 |

## Blueprint §15.2 checklist

See ticked table in `docs/iadm/IADM-05_S4_Report.md` §2 (all items evidenced).

## Gaps G-01…G-18

See `docs/iadm/IADM-05_S4_Report.md` §3 and `docs/iadm/iadm-05/BUILD_DEFAULTS.md`.

## Parked

- Real domain-service wiring (G-08)
- Legal durations / taxonomy / opening privacy gate
- Remote push (owner authorization required)
