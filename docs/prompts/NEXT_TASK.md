# Next Task

**STATUS: STM-06-US-015 completed (2026-11-16).** Notification DDL applied on Dev; persistence wired; document-service 139/0.

## Just completed

- Applied `doc.document_expiry_notifications` migration to `azulbaxszsqxymqmlwfe` (FORCE RLS, 3 policies)
- Replaced process-local notification list with Postgres + InMemory persistence
- Duplicate (tenant,user,document,type) handling; registrar-only; tenant/user scope on mark-read
- Evidence: `docs/reports/STM06_US015_NOTIFICATION_DDL_2026-11-16.json`

## Remaining gaps / gates

- **STM-06-EN-003 live commercial AV** — BLOCKED (no ClamAV/Docker/paid AV)
- **STM-CROSS-EN-007** — DEFERRED (Kafka)

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE**.
1. **STM-06-EN-002** — **completed** (including live orphan cleanup on Dev).
2. **STM-06-EN-003** — **partially completed** (controlled scanner DONE; live AV gated).
3. **STM-06-US-015** — **completed** (DDL + persistence).
4. **STM-CROSS-EN-010** e2e harness — **NEXT** (owner-authorized; existing gateway/services only).
5. **STM-CROSS-EN-014** / **STM-03-EN-007** — readiness/docs (non-infra portions).
6. Cross-tenant penetration suite + RBAC FLAG-002/003/004/005/007 reconcile (D8/D9).
7. Eligible STM-07..STM-10 stories (batch ≤4, same-service).

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Do not install live AV / Kafka without explicit owner approval.
