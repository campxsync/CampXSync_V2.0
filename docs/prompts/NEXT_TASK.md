# Next Task

**STATUS: OWNER QUEUE (2026-11-15).** Do not start an item until the owner says.

## Pickup queue (order)

0. **IAM Path C Phase 2** — **DONE** (check API + gateway + five STM Http clients). Set `CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081` to activate; else FailClosed. Staging smoke still needs R31 seed (1d).
1. **STM-06-EN-002** — Phase A implemented/tested; **BLOCKED** (Phase B awaits owner approval: staging bucket apply + live check).
1b. **Frontend:** send file content on document upload (`file` input → `contentBase64`). **BLOCKED** (frontend).
1c. **Frontend:** Registrar role mapping (`resolve-role.ts`, `UserRole`, BFFs) and staging Registrar user. **BLOCKED** (needs R31 IAM seed Phase B).
1d. **R31 Phase B:** apply `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md`. **BLOCKED** (awaits owner). Required before live A/R with real delegations.
1e. **R31-SoD** — **DONE** (`dfc6788`).
2. **STM-CROSS-EN-010** — **partially completed** (`7a41325`); remaining unified multi-service e2e / penetration harness **BLOCKED** (needs owner approval — new harness / stack-wide e2e).
3. **STM-06-US-010** — **DONE** (`0b1f73d`).
4. **STM-05-US-007** — **DONE** (`4b2154b`).
5. **STM-06-US-006** — **DONE** (`24473ff`).
6. **STM-06-US-015** — **partially completed** (`7a41325`); DB-persisted notifications **BLOCKED** (no notification table; DDL needs owner approval). In-app process-local list remains.
7. **STM-CROSS-EN-014** with **STM-03-EN-007** — docs/ops readiness pack. **BLOCKED** until owner schedules.

## Deferred (do not start)

- STM-06-US-017 (External Verification Agency — D4 fail-closed)
- STM-04-EN-004, STM-05-EN-004 (need STM-07/08/10)
- STM-CROSS-EN-007 (Kafka)
- STM-07..STM-10

## Owner policy items still open (do not implement)

- STM-RBAC-FLAG-001 remaining open labels (Registrar code R31 decided in D10)
- STM-RBAC-FLAG-002, -003, -006, -007

## Rules

- No push unless owner authorizes.
- No Supabase Storage apply until Phase B approval.
- No Path B cross-schema STM reads of `iam.delegations`.
