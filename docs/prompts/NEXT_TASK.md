# Next Task

**STATUS: STM-07 batch 4 complete — STOP. Awaiting owner. No push.**

## Batch 4 results

| Plain name | ID | Status | Commit |
|---|---|---|---|
| Connect account setup to student, admission, lifecycle and portal | STM-07-EN-005 | **partially completed** | `591f2c0` |
| Security tests, logs and go-live readiness for account setup | STM-07-EN-006 | **completed** | `895016b` |
| Batch 4 headed demo | — | done | `435e28a` |
| Student portal buildable-now map | — | done | `c021678` |

## Owner questions still open

1. Should Dev Auth for the pilot Registrar get `app_metadata.tenant_id` (and role) so the gateway accepts its JWT?
2. How should Platform Admin (R01) supply tenant context on the staff account-provisioning desk (BFF currently gets 403 Missing X-Tenant-Id)?
3. Which roles may publish portal announcements (STM-10-US-023)?

## Remaining open STM-07 (not this batch)

- Set which roles apply per college (STM-07-US-011) — role-configuration decision
- Ask for portal access myself (STM-07-US-014) — self-request setting
- College-scoped role rules and database security (STM-07-EN-003) — FLAG-002 family
- Connect account setup … (STM-07-EN-005) remaining gaps: admission auto-login-setup, event bus

## Rules

No push unless authorized. Known leftovers unchanged (`classpath.txt`, `_csv_status_*.txt`, `_verify_*.txt`, `_wt_compare_*.txt`, `docs/reports/**/video/*.webm`).
