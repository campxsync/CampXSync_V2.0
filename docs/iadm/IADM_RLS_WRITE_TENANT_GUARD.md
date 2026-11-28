# IADM RLS write tenant guard — DEV proof

**Migration:** `20261128150000_iadm_rls_write_tenant_guard.sql`  
**Applied via:** Supabase MCP `apply_migration` name `iadm_rls_write_tenant_guard` on project `azulbaxszsqxymqmlwfe` (DEV only).  
**Rollback kept:** `database/migrations/20261128150000_rollback__iadm_rls_write_tenant_guard.sql`

## Before (WITH CHECK summary)

Non-platform writers could satisfy `WITH CHECK` with permission alone — no `tenant_id = core.current_tenant_id()`:

| Table (plain label) | Policy | Before |
|---|---|---|
| `plat.usage_measurements` (usage meter) | `usage_write` | platform OR `iadm04.usage.record` |
| `plat.invoices` / `invoice_lines` | `*_write` | platform OR invoice/subscription manage |
| `plat.billing_notices` | `billing_notices_platform` | permission-only |
| Offline/online payment tables | insert/write policies | permission-only |
| `plat.plan_change_requests` | `plan_change_update` | decide permission only |
| `plat.renewal_actions` / `subscription_history` | insert | permission-only |
| `plat.institute_lifecycle_requests` | update | platform OR `adm01.manage` |
| `iam.governance_idempotency` | insert/update | actor user only |

Already guarded (no change): `core.colleges` insert/update (`tenant_id = current_tenant_id`); `gov.governance_requests` (`scope_tenant_id` vs JWT tenant).

## After

All listed policies now include `tenant_id IS NULL OR tenant_id = (SELECT core.current_tenant_id())` beside the prior permission clause, keeping `iam.is_platform_admin()` bypass. Captured `has_tenant_guard=true` on every touched policy via `pg_policy`.

## Database-level cross-institute insert refusal

Session: Institution Admin persona `50ea9b44-3b54-4ecc-b393-0865649ce0be`, JWT `tenant_id=d4000000-0000-4000-8000-000000000004` (IADM04_DEMO), `SET LOCAL ROLE authenticated`, `is_platform_admin=false`.

Attempted inserts for other institute `72ecbfe3-2077-41b5-8a20-b6d3e045311b` (MAC):

| Check | Result | Detail |
|---|---|---|
| Session | OK | tenant match; not platform |
| `plat.usage_measurements` cross-institute (IADM-04 usage) | **REFUSED** | RLS policy on `usage_measurements` |
| `gov.governance_requests` cross-institute (IADM-05 requests) | **REFUSED** | RLS policy on `governance_requests` |
| `core.colleges` cross-institute (IADM-01 colleges) | **REFUSED** | RLS policy on `colleges` |

Plain language: a non-platform role cannot write another institute’s usage, governance request, or college row — enforced by PostgreSQL RLS, not only Java.
