# Next Task

**STATUS: STM-CROSS-EN-010 completed (2026-11-16).** Cross-service E2E harness 31/31 PASS.

## Just completed

- `scripts/demo/run_stm_cross_en_010_e2e.mjs` + `run_cross_en_010_stack.ps1`
- Bearer JWT → Gateway → STM-03..06; auth/role/tenant negatives; Path C IAM
- Evidence: `docs/reports/STM_CROSS_EN_010_E2E_2026-11-16.json` (31/31)

## Remaining gaps / gates

- **STM-06-EN-003 live commercial AV** — BLOCKED
- **STM-CROSS-EN-007** — DEFERRED (Kafka)

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE**.
1. **STM-06-EN-002** — **completed**.
2. **STM-06-EN-003** — **partially completed** (live AV gated).
3. **STM-06-US-015** — **completed**.
4. **STM-CROSS-EN-010** — **completed**.
5. **STM-CROSS-EN-014** / **STM-03-EN-007** — **NEXT** (non-infra readiness/docs).
6. Cross-tenant penetration suite + RBAC FLAG-002/003/004/005/007 reconcile (D8/D9).
7. Eligible STM-07..STM-10 stories (batch ≤4, same-service).

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Do not install live AV / Kafka without explicit owner approval.
