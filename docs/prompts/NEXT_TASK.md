# Next Task

**STATUS: STM-07 batch 3 COMPLETED 2026-11-17. Batch stop. No push.**

## Completed this batch

| ID | Plain name | Status | Commit |
|---|---|---|---|
| STM-07-US-003 | Staff view of account link status | **completed** | `aa5f990` |
| STM-07-US-005 | Retry failed login setup | **completed** | `d6bf89b` |
| STM-07-US-006 | See past login requests | **completed** | `dcb9a3b` |
| STM-07-US-012 | Audit who changed accounts and roles | **completed** | `64327a8` |

## Batch-end verify (2026-11-17)

| Command | Result |
|---|---|
| Playwright STM-07 batch3 | **4/4 PASS** (`docs/reports/playwright-stm07-batch3/`) |
| `run_cross_tenant_pen_test.mjs` | **34/34 PASS; vulns=0** |
| `run_stm_cross_en_010_e2e.mjs` | **32/32 PASS** |
| `cd frontend; npx tsc --noEmit` | **0 errors** |

## Pickup queue (next batch when authorized)

1. STM-07-EN-005 / EN-006 if still fitting and unblocked
2. STM-10-EN-001 preferences if independent
3. Unblock STM-08/09 only after owner approves new services

## BLOCKED (unchanged)

STM-07-US-007/011/014, EN-003, STM-08/09, FLAG-002/003/007, CROSS-EN-007, live AV.

## Rules

- No push unless owner authorizes.
- Known leftovers: `classpath.txt`, `_csv_status_*.txt`, `_verify_*.txt`, `_wt_compare_*.txt`, `docs/reports/**/video/*.webm`.
