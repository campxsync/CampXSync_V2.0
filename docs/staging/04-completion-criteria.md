# Track A — Staging Completion Criteria & Readiness Matrix

**Document**: `docs/staging/04-completion-criteria.md`  
**Date**: 2026-10-13  
**Target Environment**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**Phase**: Phase 3 — Staging Completion  
**Scope**: Concrete pass/fail criteria defining full staging readiness for CampXSync V2.0.

---

## 1. Concrete Pass/Fail Verification Checklist

Every criterion below is an absolute gate before Track A can be marked COMPLETE.

| ID | Staging Completion Criterion | Verification Method / Evidence Source | Target Expected Result | Current Classification |
| :---: | :--- | :--- | :--- | :---: |
| **CRIT-01** | **Guard Blocks Production Ref** | Unit test execution (`TestEnvironmentGuardTest`) in ADM-01, ADM-02, ACD-01. | Strict `IllegalStateException` thrown on `ekspahbsbwdpraaqfqui`. | **VERIFIED COMPLETE**<br>(74/74 unit tests passed) |
| **CRIT-02** | **Staging Ref Assertion Test** | Unit test execution verifying `kpwdvvcschankgggitnj` direct host and pooler user format. | Accepts valid staging host and `<role>.kpwdvvcschankgggitnj`. | **VERIFIED COMPLETE**<br>(Verified in guard unit tests) |
| **CRIT-03** | **20 ERP Schemas Present** | Supabase MCP `execute_sql`: Query 2 in `verify-readonly.sql`. | All 20 schemas (`acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`) return `PRESENT`. | **VERIFIED COMPLETE**<br>(Introspected live: 20/20 present) |
| **CRIT-04** | **458 Application Tables Present** | Supabase MCP `execute_sql`: Query 3 in `verify-readonly.sql`. | Table count matches catalog total (~458 tables across 20 schemas). | **VERIFIED COMPLETE**<br>(Introspected live: exactly 458 tables) |
| **CRIT-05** | **Primary Keys & Constraints Intact** | Supabase MCP `execute_sql`: Query 4 & 5 in `verify-readonly.sql`. | PK = 458, Unique = 403, Foreign Keys = 1239 (matching constraint catalog). | **VERIFIED COMPLETE**<br>(Introspected live: 458 PK, 403 UQ, 1239 FK) |
| **CRIT-06** | **Partitioned Tables & Partitions Active** | Supabase MCP `execute_sql`: Query A / Query 7 in `verify-readonly.sql`. | 6 partition bases attached with monthly children through Jan 2027. Monthly children must have RLS. | **PARTIALLY COMPLETE**<br>(Partitions attached, but 24 monthly children lack RLS; Finding 1) |
| **CRIT-07** | **Functions & Scope Functions Compiled** | Supabase MCP `execute_sql`: Query 8 & 9 in `verify-readonly.sql`. | `core.current_tenant_id()`, `sys.ensure_partitions()`, 106 `iam.sc_*`, 39 `core.own_*`/`vis_*` functions compiled. | **VERIFIED COMPLETE**<br>(Introspected live: 106 sc_*, 39 own_/vis_*) |
| **CRIT-08** | **Auth Hook Function & Signature Intact** | Supabase MCP `execute_sql`: Query 8 & 14 in `verify-readonly.sql`. | `iam.custom_access_token_hook(event jsonb)` is `SECURITY DEFINER`, execute granted strictly to `supabase_auth_admin`. | **VERIFIED COMPLETE**<br>(Introspected live: execute granted only to `supabase_auth_admin`) |
| **CRIT-09** | **Auth Hook Enabled in Dashboard** | Supabase Dashboard: `02-manual-checklist.md` Item 2. | Custom Access Token hook enabled and pointing to `iam.custom_access_token_hook`. | **BLOCKED**<br>(Requires user dashboard confirmation) |
| **CRIT-10** | **RLS Enabled & Forced Across Tables** | Supabase MCP `execute_sql`: Query 10 in `verify-readonly.sql`. | `unshielded_tables_count = 0` across all 20 schemas. | **PARTIALLY COMPLETE**<br>(434 tables have RLS; 24 monthly partitions unshielded per Finding 1) |
| **CRIT-11** | **RLS Denies Unauthenticated / Anon Access** | Supabase MCP `execute_sql`: Query 13 in `verify-readonly.sql`. | `anon` role has 0 table permissions across all application schemas. | **VERIFIED COMPLETE**<br>(Introspected live: 0 table permissions for anon) |
| **CRIT-12** | **Zero Duplicate Seed Roles** | Supabase MCP `execute_sql`: Query 16 in `verify-readonly.sql`. | `SELECT code, count(*) FROM iam.roles ... HAVING count(*) > 1` returns 0 rows. | **VERIFIED COMPLETE**<br>(Introspected live: 0 rows) |
| **CRIT-13** | **Zero Duplicate Permissions** | Supabase MCP `execute_sql`: Query 17 in `verify-readonly.sql`. | `SELECT code, count(*) FROM iam.permissions ... HAVING count(*) > 1` returns 0 rows. 547 active permissions. | **VERIFIED COMPLETE**<br>(Introspected live: 0 duplicates; 547 permissions) |
| **CRIT-14** | **Staging Test Tenant & Users Active** | Supabase MCP `execute_sql`: Query 15 in `verify-readonly.sql`. | At least 1 active tenant in `core.tenants` and 1 active profile in `iam.user_profiles`. | **NOT STARTED**<br>(Tables empty; awaiting write approval to provision test tenant) |
| **CRIT-15** | **JDBC Pooler Configuration (Session 5432)** | Environment config: `02-manual-checklist.md` Item 4. | Dual-stack pooler host, port 5432, username `postgres.kpwdvvcschankgggitnj`. | **VERIFIED COMPLETE**<br>(Pooler host & user format verified in ambient shell) |
| **CRIT-16** | **Local Rule 11 Boolean Cleanliness** | PowerShell process check (`scratch/check_env.ps1`). | `ContainsProdRef = False` for all variables; `ContainsStagingRef = True` for JDBC and user; `CAMPX_TEST_ENV = staging`. | **VERIFIED COMPLETE**<br>(Clean check: 0 prod refs, staging refs confirmed) |
| **CRIT-17** | **Auth Hook Claims Stamped in Issued JWT** | Live integration test with issued token. | Issued token claims contain `app_metadata.tenant_id = <valid-uuid>`. | **NOT STARTED**<br>(Gated on CRIT-09, 14) |
| **CRIT-18** | **Tenant A Cannot Read/Write Tenant B** | Live RLS test (`PostgresCourseRestrictedIdentityRlsIntegrationTest`). | Cross-tenant access denied with zero rows or RLS exception (42501) under unprivileged `authenticated` role. | **PARTIALLY COMPLETE LOCALLY**<br>(Test code implemented with SET LOCAL ROLE; live run pending CRIT-14) |
| **CRIT-19** | **Service-Level Read/Write Inside Tenant** | Live repository tests (ADM-01, ADM-02, ACD-01). | JDBC CRUD operations succeed without exceptions. | **PARTIALLY COMPLETE LOCALLY**<br>(Repositories implemented; live run pending CRIT-14) |
| **CRIT-20** | **Application Starts & Boots Staging Persistence** | Live boot test with `DatabaseConfig.load()`. | No `PersistenceBootstrapException` or connection timeout. | **NOT STARTED**<br>(Gated on CRIT-14 test tenant provisioning) |

---

## 2. Overall Readiness Summary

- **VERIFIED COMPLETE**: 11 items (CRIT-01, CRIT-02, CRIT-03, CRIT-04, CRIT-05, CRIT-07, CRIT-08, CRIT-11, CRIT-12, CRIT-13, CRIT-15, CRIT-16).
- **PARTIALLY COMPLETE**: 4 items (CRIT-06 Partition security; CRIT-10 Table RLS coverage; CRIT-18 Cross-tenant RLS test code; CRIT-19 Persistence repositories code).
- **BLOCKED**: 1 item (CRIT-09 Dashboard hook toggle confirmation).
- **NOT STARTED**: 4 items (CRIT-14 Provision test tenant; CRIT-17 Live JWT stamp test; CRIT-20 Live app startup).

**Track A Verdict**: **SUBSTANTIAL ADVANCEMENT | INCOMPLETE PENDING 4 GATED ACTIONS**:
1. Fix Finding 1 by enabling RLS and revoking direct table grants on 24 monthly partition children (`GO PARTITION-RLS-FIX`).
2. Provision test tenant in `core.tenants` to trigger system role seeding (`GO PROVISION-TEST-TENANT`).
3. User confirms Custom Access Token hook toggle in Supabase Dashboard (`02-manual-checklist.md` Item 2).
4. Run live integration tests and startup validation against provisioned test tenant.
