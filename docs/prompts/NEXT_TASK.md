# Next Task

**STATUS: STM-07-EN-001 completed (2026-11-16). Coordinator stopped per owner.**

## Just completed

- Dev DDL: `stu.student_iam_links` + `stu.account_provisioning_requests` on `azulbaxszsqxymqmlwfe`
- Persistence wired in existing `student-service` (no new microservice)
- student-service suite **113/0**
- Evidence: `docs/reports/STM07_EN001_IAM_LINK_DDL_2026-11-16.json`
- STM-07-US-001 marked **partially completed** (durable link path; eligibility/portal follow-ons open)

## Remaining gaps / gates

- **STM-06-EN-003 live commercial AV** — BLOCKED
- **STM-CROSS-EN-007** — DEFERRED (Kafka)
- **FLAG-002 / FLAG-003 / FLAG-007** — awaiting owner
- **STM-08 / STM-09** — BLOCKED (new services/schema)
- **STM-07-US-002+** — yet to pickup after EN-001

## Pickup queue (when resumed)

1. STM-07-US-001 closure gaps (eligibility) then US-002.. in `student-service`
2. STM-10-EN-001 preferences (existing institute-admin path) where independent

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
