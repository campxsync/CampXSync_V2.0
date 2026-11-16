# Next Task

**STATUS: STM-07 batch 2 IN PROGRESS (2026-11-16). Owner decisions STM-07-B2-D1..D3 recorded in PROGRESS.md.**

## Preflight (this batch)

- Branch: `feature/java-backend-integration`
- Untracked leftovers only: `classpath.txt`, `_csv_status_*.txt` (known; no STOP)
- Dev project confirmed: `azulbaxszsqxymqmlwfe`
- student-service suite: green on first full run; intermittent live `LiveStudentRuntimeSecurityTest` PgBouncer "prepared statement already exists" (pre-existing flaky)
- institute-admin-service: **499 run, 0 fail, 11 errors, 32 skipped** — pre-existing Postgres college/provisioning integration (`InstituteNotFound` / platform-admin privilege); documented in COMPLETION_REPORT Path C entries

## Batch 2 selection (max 4)

| # | ID | Plan |
|---|---|---|
| 1–2 | STM-07-US-008 + US-009 | Pair (D3): IAM R25 assign/revoke on institute-admin + student-service port |
| 3 | STM-07-EN-002 | Existing IAM integration hardening (no duplicate auth/role system) |
| 4 | STM-07-EN-004 | Idempotent provisioning / retries / reconciliation (P0 next) |

## BLOCKED (do not implement)

| ID | Reason |
|---|---|
| STM-07-US-007 | Account activation notifications need event bus (CROSS-EN-007 / Kafka hold) |
| STM-07-US-011 | Tenant role mappings depend on FLAG-002 |
| STM-07-US-014 | Student self-request: "if enabled" setting not defined — ask owner exact setting key/default |
| STM-07-EN-003 | FLAG-002 / owner infrastructure decision pending |
| STM-06-EN-003 live AV | owner infrastructure decision pending |
| STM-CROSS-EN-007 | DEFERRED (Kafka) |
| FLAG-002 / FLAG-003 / FLAG-007 | owner infrastructure decision pending |
| STM-08-* / STM-09-* | New service gate (design notes only) |

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- One commit per story; browser demo commit separate at batch end.
