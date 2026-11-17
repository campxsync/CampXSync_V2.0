# Next Task

**STATUS: STM-07 batch 3 in progress — Staff view of account link status (STM-07-US-003) COMPLETED.**

## Just completed

| ID | Plain name | Status | Notes |
|---|---|---|---|
| STM-07-US-003 | Staff view of account link status | **completed** | B3-D5 staff auth; college desk + BFF; `Stm07Us003StaffLinkStatusTest` |

## Remaining this batch (authorized)

1. Retry failed login setup (STM-07-US-005)
2. See past login requests (STM-07-US-006)
3. Audit who changed accounts and roles (STM-07-US-012)
4. Batch-end suites + headed Playwright batch3 + STOP (no push)

## BLOCKED (do not implement)

| ID | Reason |
|---|---|
| STM-07-US-007 | Notifications need event bus (CROSS-EN-007 / Kafka hold) |
| STM-07-US-011 | Depends on FLAG-002 |
| STM-07-US-014 | Self-request "if enabled" setting not defined |
| STM-07-EN-003 | FLAG-002 / owner infrastructure decision pending |
| STM-07-EN-005 / EN-006 | Parked for batch 4 if capacity |
| STM-06-EN-003 live AV | owner infrastructure decision pending |
| STM-CROSS-EN-007 | DEFERRED (Kafka) |
| FLAG-002 / FLAG-003 / FLAG-007 | owner infrastructure decision pending |
| STM-08-* / STM-09-* | New service gate |

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- One commit per story; known leftovers: `classpath.txt`, `_csv_status_*.txt`, `_verify_*.txt`, `_wt_compare_*.txt`, `docs/reports/**/video/*.webm`.
