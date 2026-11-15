# Next Task

**STATUS: OWNER WRITE GATE (2026-11-15).** R31 pilot activation package ready. No DB/Auth writes until approved phrase.

## Immediate gate

**Approve:** `APPROVED R31-PILOT-WRITE azulbaxszsqxymqmlwfe`  
**Package:** `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md`  
**Verified target:** CampXSync-Architecture-Dev / `azulbaxszsqxymqmlwfe` only.

After approval: create Auth user → apply §3.B SQL → set `CAMPX_IAM_DELEGATION_BASE_URL` → live Path C verification across STM-02..06.

Separate: `APPROVED EN-002-STORAGE azulbaxszsqxymqmlwfe` for private storage bucket (0 buckets today).

## Pickup queue (order)

0. **IAM Path C Phase 2** — **DONE** (`268c036`).
0b. **R31 pilot activation** — **BLOCKED** at write gate (package ready).
1. **STM-06-EN-002** — Phase A done; **BLOCKED** Phase B storage apply.
1b. **Frontend:** `contentBase64` upload — **BLOCKED**.
1c. **Frontend:** Registrar role mapping — **BLOCKED** (after R31 pilot user exists).
1d. **R31 Phase B seed** — superseded by 0b activation package.
1e. **R31-SoD** — **DONE**.
2. **STM-CROSS-EN-010** — partially completed; e2e harness **BLOCKED**.
3–5. US-010 / US-007 / US-006 — **DONE**.
6. **STM-06-US-015** — partially completed; notification DDL **BLOCKED**.
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED** until scheduled.

## Rules

- No push unless owner authorizes.
- No production access.
- No unapproved SQL/Auth/Storage writes.
