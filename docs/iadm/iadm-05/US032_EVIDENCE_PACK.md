# IADM-05-US-032 — Evidence pack and close-out

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Service:** IADM-05 Data Governance & Privacy (`:8103` / `/api/v1/iadm/05`)

## Commits (local only — not pushed)

| Hash | One-line label |
|---|---|
| `d3c2e74` | S0 scaffold — service, keys, gov FORCE RLS, shared audit |
| `854d8f1` | S1 policy lifecycle, DEMO seed, governance desks |

*(S2/S3/S4 commit hashes appended when committed.)*

## Automated tests

| Suite | Meaning | Result |
|---|---|---|
| `GovernancePermissionMatrixTest` | Offline role×key denials (US-023) | PASS |
| `GovernanceAuditFailClosedTest` | Audit unavailable refuses action (US-025) | PASS |
| `GovernanceFailureTableTest` | Failure-table offline behaviours (US-031) | PASS |
| `npx tsc --noEmit` | Frontend TypeScript check | PASS (0 errors) |

## Headed persona runs

Runner: `node scripts/demo/iadm05_headed.mjs`  
Evidence dir: `docs/iadm/iadm-05/evidence/us032/`

| Persona | Plain role | Desk | Status |
|---|---|---|---|
| R01 | Super Admin | `/platform/governance` | Run required — see DEMO.md after headed |
| R02 | System Admin | `/platform/governance` | Run required |
| R03 | Institution Admin | `/institution/governance` | Run required |
| R04 | College Admin | `/college/governance` | Run required |
| R28 | Auditor | `/platform/governance/audit` | Run required |

## Blueprint §15.2 checklist

| Item | Evidence |
|---|---|
| R01 platform scope ≠ R03 institute scope | Institute policies DEMO-RET-INST-A/B + adherence API scopes by tenant |
| R02 write ≠ policy approval | Matrix + BFF deny approve/publish for system_admin |
| R03 cannot view/govern other institute | RLS + adherence filter on `scope_tenant_id` |
| R03 adherence view-only (G-16) | Desk `writeControls=false`; no POST BFF for institution |
| R04 cannot override IADM policy | College desk `canEditPolicy=false`; no write routes |
| R04 entry points informational only (G-02) | Entry list `triggerable=false` |
| R28 read-only | Auditor desk; no mutate controls |
| No R05–R39 inferred access | Matrix empty for R05/R10/R25/R31 |
| Cross-tenant sharing default deny | Wildcard provider/consumer rejected; deny seed row |
| V ≠ E/X/D/approve | Matrix tests |
| Export approval ≠ execution | Decide then dispatch/simulator |
| Erasure by domain owners not IADM-05 | Simulator / contract only; no domain DB access |
| Partial ≠ global success | Aggregate status logic + seeded partial rows |
| CADM-08 remains import owner | Not built / not claimed |
| IADM-07 remains security owner | Not built / not claimed |
| Shared audit used; no second store | `DurableAuditWriter` → `audit.log_event` |
| Shared identity/authz/logging reused | Gateway HMAC + `iam.has_permission` + CampXLogger |
| Missing tenant/policy fail closed | `IADM05_NO_APPLICABLE_POLICY`, tenant checks |
| No sensitive payloads in logs | Audit payload refs only; controller avoids body dumps |
| No Kafka | None introduced |
| Schema/API/keys treated as build proposal | Documented in S0/S1 reports (G-09/G-01 closed by build default) |
| Queues seeded (not empty mocks) | S2/S3 DEMO seeds applied on DEV |
| Retention/consent/sharing are IADM-05 records | Schema `gov`; not IADM-02 `cfg.*` |

## Gaps G-01…G-18 — what this build assumed

| Gap | Assumed in this build |
|---|---|
| G-01 | Keys = `iadm05.*` catalogue |
| G-02 | R04 informational entry points only |
| G-03/G-04 | Privacy publish hard-blocked; no open path |
| G-05 | Technical closure only |
| G-06 | DEMO ONLY classification labels |
| G-07 | Destination notes TBC |
| G-08 | DEMO simulator; contracts TBC per domain |
| G-09 | Schema `gov` as build proposal |
| G-10 | One published version or conflict/none fail closed |
| G-11 | Reuse `audit.log_event` fields |
| G-12 | State machine as implemented |
| G-13 | Holds = TBC text fields |
| G-14 | Port 8103 / prefix `/api/v1/iadm/05` |
| G-15 | Approver = R01 only; SoD |
| G-16 | R03 view-only |
| G-17 | Data-subject access/correction/grievance **not** in IADM-05 this build |
| G-18 | Invoice retention / college archive D1 placeholders only |

## Parked

- Full headed film PASS for all five personas (run `iadm05_headed.mjs` when UI session accounts confirmed)
- Real domain-service wiring (explicitly out of this build)
- Legal durations / taxonomy / DPDP interpretation
