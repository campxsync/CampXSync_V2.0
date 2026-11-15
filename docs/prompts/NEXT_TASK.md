# Next Task

**STATUS: R31 PILOT PARTIAL — storage / stack / frontend remain.** Path C live allow on Dev verified after write approval.

## Just completed (local)

- Owner: `APPROVED R31-PILOT-WRITE azulbaxszsqxymqmlwfe`
- Auth + IAM pilot Registrar rows on Dev (`azulbaxszsqxymqmlwfe`)
- Privileged `hasActiveRegistrarDelegation` join (RLS-safe Path C evaluator)
- `LivePostgresDelegationActiveCheckTest` ALLOW for STM-02..06.A + negatives

## Immediate gates (owner)

1. **`APPROVED EN-002-STORAGE azulbaxszsqxymqmlwfe`** — private `student-documents` bucket (still **0** buckets).
2. Free ≥3 GB RAM → start stack + set `CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081` → STM HTTP A/R smoke.
3. Frontend queue: contentBase64 upload (1b) → Registrar role mapping (1c) after pilot login verified in UI.

## Pickup queue (order)

0. **IAM Path C Phase 2** — **DONE** (`268c036` + RLS join fix).
0b. **R31 pilot activation** — **partially completed** (Auth/IAM/Path C live; stack smoke + storage pending).
1. **STM-06-EN-002** — Phase A done; **BLOCKED** Phase B storage apply.
1b. **Frontend:** `contentBase64` upload — **BLOCKED**.
1c. **Frontend:** Registrar role mapping — **BLOCKED** (pilot user exists; UI mapping next).
1d. **R31 Phase B seed** — superseded by 0b.
1e. **R31-SoD** — **DONE**.
2. **STM-CROSS-EN-010** — partially completed; e2e harness **BLOCKED**.
3–5. US-010 / US-007 / US-006 — **DONE**.
6. **STM-06-US-015** — partially completed; notification DDL **BLOCKED**.
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED** until scheduled.

## Rules

- No push unless owner authorizes.
- No production access.
- No unapproved SQL/Auth/Storage writes.
