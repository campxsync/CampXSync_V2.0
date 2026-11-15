# Next Task

**STATUS: OWNER QUEUE (2026-11-15).** Do not start an item until the owner says.  
**Preflight STOP (2026-11-15):** unexpected dirty files beyond known leftovers — see PROGRESS / final report. Queue not started until cleared.

## Pickup queue (order)

1. **STM-06-EN-002** — Phase A implemented/tested; **BLOCKED** (Phase B awaits owner approval: staging bucket apply + live check).
1b. **Frontend:** send file content on document upload (`file` input → `contentBase64`), required before any storage-backed pilot use. **BLOCKED** (frontend; outside STM Java same-service batch until owner schedules).
1c. **Frontend:** Registrar role mapping (`resolve-role.ts`, `UserRole`, BFFs) and staging Registrar user. **BLOCKED** (needs R31 IAM seed Phase B).
1d. **R31 Phase B:** apply `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md` (IAM seed + pilot assignment). **BLOCKED** (awaits owner apply authorization). Live delegation adapter separately **BLOCKED** pending approval of `docs/design/IAM_DELEGATION_ADAPTER_DESIGN_NOTE.md`.
1e. **R31-SoD:** extend same-actor verify-to-approve denial to enrollment, lifecycle, guardian and admissions services; tests; full suites.
2. **STM-CROSS-EN-010** — scoped to STM-03..STM-06 (tenant-isolation, RBAC negative, integration tests).
3. **STM-06-US-010** — Request document re-verification.
4. **STM-05-US-007** — Designate primary and secondary emergency contacts.
5. **STM-06-US-006** — Update document metadata (needs Registrar role per D1/D9; service recognition done; IAM seed still Phase B — treat as **BLOCKED** until pilot Registrar user exists or owner waives).
6. **STM-06-US-015** — Document-expiry notifications (**in-app only**).
7. **STM-CROSS-EN-014** with **STM-03-EN-007** — docs/ops readiness pack.

## Deferred (do not start)

- STM-06-US-017 (External Verification Agency — D4 fail-closed)
- STM-04-EN-004, STM-05-EN-004 (need STM-07/08/10)
- STM-CROSS-EN-007 (Kafka)
- STM-07..STM-10

## Owner policy items still open (do not implement)

- STM-RBAC-FLAG-001 remaining open labels (Registrar code R31 decided in D10)
- STM-RBAC-FLAG-002, -003, -006, -007
- Live IAM delegation adapter — **BLOCKED**; design note: `docs/design/IAM_DELEGATION_ADAPTER_DESIGN_NOTE.md` (D12a)

## Rules

- No commit/push unless owner authorizes.
- No Supabase Storage apply until Phase B approval.
- No live delegation adapter until owner approves the design note (Path B or C + mapping M1/M2/M3).
