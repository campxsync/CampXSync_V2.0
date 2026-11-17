# Next Task

**STATUS: Owner answers applied (partial) — STOP. No push.**

## Done

1. Confirmed Dev `azulbaxszsqxymqmlwfe`. Runbook restore of pilot Registrar role-assignment `…000032` — row **already present** (INSERT no-op). Evidence: `docs/reports/STM07_REGISTRAR_ROLE_ASSIGNMENT_BEFORE_AFTER_2026-11-17.md`.
2. Root cause of `GATEWAY_NO_ROLE_ASSIGNED`: gateway does not recognize `REGISTRAR` in `JdbcIdentityResolver` (not a missing row / not test cleanup of the pilot). Tests not changed.
3. College Admin is **not** on the STM-07.ACCOUNT.A path for link-status — browser deny kept off; service tests cover denial. Pilot M1 not revoked.
4. Platform Admin: no college selector; R02/R03 have **no** existing `.env.audit` test accounts on Dev.
5. Announcements (STM-10-US-023): publishers confirmed College Admin + Communication Manager; communication service **missing** under `services/` → BLOCKED (CSV patch ready in `.tmpwrite` if the open CSV file lock is released).

## Owner question to unblock Registrar success demo

May we add Registrar (R31) / `REGISTRAR` to the API Gateway’s recognized business roles in `JdbcIdentityResolver` (and the in-memory twin) so delegated Registrar JWT identity resolves?

## Rules

No push unless authorized.
