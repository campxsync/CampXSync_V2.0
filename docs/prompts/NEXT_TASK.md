# Next Task

**STATUS: STM-07 batch 2 COMPLETED 2026-11-16. Batch stop.**

## Just completed (this batch)

| ID | Status | Commit | Notes |
|---|---|---|---|
| STM-07-US-008 | DONE | `2675dea` | Assign R25 via institute-admin IAM + student-service port |
| STM-07-US-009 | DONE | `2675dea` | Revoke R25 via IAM port; lifecycle best-effort revoke |
| STM-07-EN-002 | DONE | `57dc0ff` | No duplicate auth/credential/role store; fail-closed IAM port |
| STM-07-EN-004 | DONE | `bf8cf6d` | Idempotent REQUESTED reuse; FAILED retry; COMPLETED/FAILED markers |
| Browser demo | DONE | (separate commit) | `docs/reports/playwright-stm07-batch2/` |

student-service suite after batch: **153/0**

## BLOCKED (do not implement)

| ID | Reason |
|---|---|
| STM-07-US-001 | **partially completed** — verificationStatus=VERIFIED eligibility gate still UNCOVERED |
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
2. Close US-001 verificationStatus gate if owner confirms it is required
3. STM-10-EN-001 preferences if independent
4. Unblock STM-08/09 only after owner approves new services

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
