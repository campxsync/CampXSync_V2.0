# Next Task

**STATUS: STM-07 stack fix + US-001 VERIFIED gate COMPLETED 2026-11-17. Batch stop.**

## Just completed (this verify pass)

| ID | Status | Commit | Notes |
|---|---|---|---|
| Stack script | DONE | `a480396` | institute-admin :8081 + student-service :8093 in start/check_stack |
| STM-07-US-001 | **completed** | `912a6f9` (+ `117f4fa` suite adapt) | `VERIFICATION_NOT_VERIFIED` eligibility gate |
| Role endpoint security tests | DONE | `8412134` | R01/R02/R03 only; deny paths; gateway missing-auth |
| STM-07-US-008 | completed | `2675dea` | Re-verified: Playwright batch2 5/5 |
| STM-07-US-009 | completed | `2675dea` | Re-verified: Playwright batch2 5/5 |
| STM-07-EN-002 | completed | `57dc0ff` | Re-verified: Playwright batch2 5/5 |
| STM-07-EN-004 | completed | `bf8cf6d` | Re-verified: Playwright batch2 5/5 |

## Verify counts (2026-11-17)

| Command | Result |
|---|---|
| `node scripts/demo/run_stm_cross_en_010_e2e.mjs` | **32/32 PASS** (after starting :8095/:8097) |
| `node scripts/demo/run_cross_tenant_pen_test.mjs` | **34/34 PASS; vulns=0** |
| `node docs/reports/playwright-stm07-batch2/run_batch2_demo.mjs` | **5/5 PASS** |

## Pre-existing suite compare @ `8f486ab` worktree

| Module | HEAD | `8f486ab` | Verdict |
|---|---|---|---|
| institute-admin | 493 / 0f / **12e** | 477 / 0f / **12e** | **Identical** 12 Postgres/live errors |
| api-gateway | 155 / **6f** / **7e** | 153 / **6f** / **7e** | **Identical** 6f+7e (HEAD +2 tests from role auth) |

## OWNER decision still open

Hosting of student role assign/revoke on **institute-admin-service :8081** — confirm **"approved"** or **"move"**.

## BLOCKED (do not implement)

| ID | Reason |
|---|---|
| STM-07-US-007 | Notifications need event bus (CROSS-EN-007 / Kafka hold) |
| STM-07-US-011 | Depends on FLAG-002 |
| STM-07-US-014 | Self-request "if enabled" setting not defined |
| STM-07-EN-003 | FLAG-002 / owner infrastructure decision pending |
| STM-06-EN-003 live AV | owner infrastructure decision pending |
| STM-CROSS-EN-007 | DEFERRED (Kafka) |
| FLAG-002 / FLAG-003 / FLAG-007 | owner infrastructure decision pending |
| STM-08-* / STM-09-* | New service gate |

## Pickup queue (next batch when authorized)

1. Remaining unblocked STM-07 P1 (US-003/005/006/012) inside student-service
2. STM-10-EN-001 preferences if independent
3. Unblock STM-08/09 only after owner approves new services

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
