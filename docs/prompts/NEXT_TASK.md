# Next Task

**STATUS: Frontend 1c Registrar role mapping DONE (2026-11-16).** R31 is a distinct UI bucket with STM desk allowlist; College Admin / Academic Admin surfaces remain denied.

## Just completed

- `UserRole` + `resolve-role.ts`: map `REGISTRAR` / `R31` → `registrar` (never folded into `college_admin`)
- `role-policy.ts`: campus STM allowlist; operational vs admin-only BFF helpers; gateway hint `REGISTRAR`
- Middleware + nav + home/auth resolve + test-session persona (`REGISTRAR_PILOT_*`)
- BFF: lifecycle / enrollment / verifications / access-grants / guardians allow registrar; types/requirements/org stay admin-only
- Verification:
  - `npx tsc --noEmit` → 0
  - `verify_registrar_role_policy.mjs` → 7/7
  - `verify_role_routing.mjs` → **50/50** (registrar allow/deny; student/faculty/admin unchanged)
  - Evidence: `docs/reports/STM06_FRONTEND_1C_REGISTRAR_MAPPING_2026-11-16.json`

## Remaining gaps (EN-002 still partially completed)

- Orphan object delete API not implemented
- Live malware AV still open gap (D11)

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE**.
1. **STM-06-EN-002** — **partially completed** (live Storage + 1b + 1c DONE; orphan API + malware remain).
1b. **Frontend:** `contentBase64` upload — **DONE**.
1c. **Frontend:** Registrar role mapping — **DONE** (2026-11-16).
2. **STM-CROSS-EN-010** e2e — **BLOCKED** (harness approval).
6. **STM-06-US-015** — **BLOCKED** (notification DDL).
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED**.

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Next eligible without product decision: none in this queue. Await owner pickup.
