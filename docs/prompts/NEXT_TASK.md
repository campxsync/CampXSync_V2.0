# Next Task

**STATUS: EN-002 Phase B PARTIAL.** Private bucket applied on Dev; service-role live path blocked on missing key.

## Just completed

- Owner: `APPROVED EN-002-STORAGE azulbaxszsqxymqmlwfe`
- Created private `student-documents` bucket (10MB, MIME allowlist) on `azulbaxszsqxymqmlwfe`
- Restrictive deny policies for `anon` + `authenticated`
- Deny verification **8/8 PASS** (`docs/reports/STM06_EN002_STORAGE_APPLY_VERIFY_2026-11-15.json`)
- document-service suite **127/0/0/0**
- Non-secret keys in `.env.audit`; **service-role key not set in agent env**

## Immediate owner actions

1. Set `CAMPX_SUPABASE_SERVICE_ROLE_KEY` in gitignored `.env.audit` from Dev Dashboard → Settings → API (**do not paste into chat**).
2. Reply `CONTINUE EN-002-STORAGE-LIVE` after the key is set — agent will restart document-service and run put/get + tenant-prefix smoke (Dev only).
3. Frontend queue: contentBase64 upload (1b) → Registrar role mapping (1c).

## Pickup queue (order)

0. **IAM Path C Phase 2** — **DONE**.
0b. **R31 pilot activation** — **partially completed** (Auth/IAM/Path C + stack smoke done).
1. **STM-06-EN-002** — **partially completed** (Phase B bucket/policies done; live service-role path **BLOCKED** on missing key).
1b. **Frontend:** `contentBase64` upload — **BLOCKED** (wait EN-002 live path / product decision).
1c. **Frontend:** Registrar role mapping — **BLOCKED**.
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
- Never print service-role keys.
