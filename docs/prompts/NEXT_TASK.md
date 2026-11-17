# Next Task

**STATUS: STM-07 batch 4 in progress — EN-005 partially completed; next EN-006.**

## Just completed

| Plain name | ID | Status | Notes |
|---|---|---|---|
| Connect account setup to student, admission, lifecycle and portal | STM-07-EN-005 | **partially completed** | student/portal/lifecycle notify; admission auto-provision + event bus uncovered |
| Batch 3 negative demo | — | committed | `1d38263` / report `a62950e` |

## Owner decision needed (Registrar JWT)

Pilot Registrar Auth user signs in but JWT `app_metadata` has **no `tenant_id`**, so the gateway returns malformed-token before Java STM-07.ACCOUNT.A runs. Should we set `app_metadata.tenant_id` (and role) on that existing Auth user in Dev project `azulbaxszsqxymqmlwfe` without inventing a new account?

## Remaining this batch

1. Security tests, logs and go-live readiness for account setup (STM-07-EN-006)
2. Playwright batch4 demo + STM10_BUILDABLE_NOW.md
3. Final report; STOP; no push

## Rules

- No push unless owner authorizes.
- Known leftovers: `classpath.txt`, `_csv_status_*.txt`, `_verify_*.txt`, `_wt_compare_*.txt`, `docs/reports/**/video/*.webm`.
