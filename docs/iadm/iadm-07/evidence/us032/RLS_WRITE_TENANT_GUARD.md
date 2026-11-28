# IADM-07 RLS write tenant guard — DEV proof

**Migration:** `20261127140000_iadm07_rls_write_tenant_guard.sql`  
**Applied via:** Supabase MCP `apply_migration` name `iadm07_rls_write_tenant_guard` on project `azulbaxszsqxymqmlwfe` (same method as prior IADM migrations).  
**Rollback kept:** `database/migrations/20261127140000_rollback__iadm07_rls_write_tenant_guard.sql`

## Before (WITH CHECK excerpt)

Non-platform writers could satisfy `WITH CHECK` with permission alone — no `institute_id = core.current_tenant_id()`:

| Table | Policy | Before `with_check` (summary) |
|---|---|---|
| `sec.findings` | `sec_findings_write` | platform admin OR `iadm07.register.write` |
| `sec.risks` | `sec_risks_write` | platform admin OR `iadm07.register.write` |
| `sec.alerts` | `sec_alerts_write` | platform admin OR `iadm07.register.write` |
| `sec.exceptions` | `sec_exceptions_write` | platform admin OR exception request/decide |
| `sec.signals` | `sec_signals_write` | platform admin OR register.write OR signal.intake |

## After (WITH CHECK excerpt)

Captured from `pg_policies` after apply — tenant guard present:

| Table | After `with_check` (summary) |
|---|---|
| `sec.findings` | platform admin OR (`iadm07.register.write` AND (`institute_id` NULL OR equals `core.current_tenant_id()` OR `assigned_institute_id` equals tenant)) |
| `sec.risks` | platform admin OR (`iadm07.register.write` AND (`institute_id` NULL OR equals tenant)) |
| `sec.alerts` | platform admin OR (`iadm07.register.write` AND (`institute_id` NULL OR equals tenant)) |
| `sec.control_assessments` | platform admin OR (`iadm07.register.write` AND `institute_id` equals tenant) |
| `sec.evidence_refs` | platform admin OR (`iadm07.register.write` AND (`institute_id` NULL OR equals tenant)) |
| `sec.exceptions` | platform admin OR decide OR (request AND (`institute_id` NULL OR equals tenant)) |
| `sec.signals` | platform admin OR signal.intake OR (register.write AND (`institute_id` NULL OR equals tenant)) |
| `sec.enforcement_status` | platform admin OR signal.intake OR (register.write AND (`institute_id` NULL OR equals tenant)) |

## Database-level cross-institute insert refusal

Session: Institution Admin (R03) persona `institution.iadm04.demo@campx.local` (`50ea9b44-3b54-4ecc-b393-0865649ce0be`), JWT claims `tenant_id=d4000000-0000-4000-8000-000000000004` (IADM04_DEMO), `SET LOCAL ROLE authenticated`.

Attempted inserts for other institute `72ecbfe3-2077-41b5-8a20-b6d3e045311b` (MAC):

| Check | Result | Detail |
|---|---|---|
| Session | OK | `tenant=d4000000-…-0004`, `has_write=true`, `is_platform=false` |
| `sec.findings` cross-tenant | **REFUSED** | `new row violates row-level security policy for table "findings"` |
| `sec.risks` cross-tenant | **REFUSED** | `new row violates row-level security policy for table "risks"` |
| `sec.alerts` cross-tenant | **REFUSED** | `new row violates row-level security policy for table "alerts"` |

Plain language: a non-platform role with write permission still cannot insert a finding, risk, or alert for another institute — enforced by PostgreSQL RLS, not only Java.
