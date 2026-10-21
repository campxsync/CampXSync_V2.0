# CampXSync — Wave 4, Stage A: G1 Database Security Remediation Proposal
## Comprehensive Technical Design, Least-Privilege Architecture, and Reviewable SQL Scripts

**Document Identifier:** `docs/reports/wave-4/G1_SECURITY_REMEDIATION_PROPOSAL.md`  
**Date:** 2026-10-21  
**Auditor:** Antigravity Autonomous Agent  
**Branch:** `feature/java-backend-integration`  
**Governing Documents:** root `AGENTS.md`, `.agent/rules/campxsync-wave-control.md`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, `docs/project-control/PROGRESS.md`, `prompts/NEXT_ANTIGRAVITY_TASK.md`, `docs/reports/wave-4/STUDENT_DATABASE_RLS_AUDIT.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`)  
**Status:** PROPOSAL ONLY — Zero Database Writes, Zero Role/Policy Changes Executed, Zero Java Application Code Modified.

---

## 1. Executive Summary & G1 Baseline Findings

In accordance with Gate G1 of the CampXSync Wave Implementation Plan:
> **G1 — Least-privilege DB role (before Wave 4 persistence):**  
> *Business DB access must use a dedicated application login role without `BYPASSRLS`, with transaction-scoped tenant/user context. A privileged role must not be used to claim tenant isolation.*

The preflight audit ([STUDENT_DATABASE_RLS_AUDIT.md](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/STUDENT_DATABASE_RLS_AUDIT.md)) conclusively established that **Gate G1 is currently FAILING / NOT VERIFIED (OPEN)**:

1. **Superuser Connection Default:** All backend connections in Next.js (`frontend/src/lib/db.ts`) and Java (`DatabaseConfig.java`) connect using `SUPABASE_USER = postgres.azulbaxszsqxymqmlwfe`. In PostgreSQL, the `postgres` role owns all application tables and possesses `BYPASSRLS`.
2. **The Superuser / `BYPASSRLS` Security Trap:** PostgreSQL official documentation establishes that *superusers and roles with the `BYPASSRLS` attribute always bypass row-level security, even when `FORCE ROW LEVEL SECURITY` is applied to a table*. Therefore, applying `FORCE RLS` while continuing to connect as `postgres` is mathematically incapable of enforcing tenant isolation. Gate G1 can only be satisfied by introducing an unprivileged connection role.
3. **Missing `FORCE ROW LEVEL SECURITY` on Base Tables:** While RLS is enabled (`relrowsecurity = true`) across catalog tables, `FORCE ROW LEVEL SECURITY` (`relforcerowsecurity = false`) is disabled on every table. Under PostgreSQL kernel semantics, table owners bypass RLS when `FORCE RLS` is not set.
4. **No Dedicated Application Role:** No unprivileged application login role without `BYPASSRLS` currently exists in the migration history or catalog.
5. **Tenant Isolation Overclaiming:** Tenant separation currently rests 100% on application-level query predicates (`WHERE tenant_id = $1`). If an application query omits this predicate, the database engine will not intervene to block cross-tenant leakage.

This proposal provides an evidence-based, staging-only remediation design and reviewable SQL script package (`docs/reports/wave-4/g1-proposed-sql/`) to close Gate G1 safely without breaking existing services.

---

## 2. Deliverable A1 — G1 Security Findings and Design

### A. Role Architecture & Application Database Role Design

#### 1. Evaluation: Shared Application Role vs. Per-Service Roles

| Architecture Option | Structural & Operational Characteristics | Security & Isolation Boundary | Connection Pooling Impact (Supabase PgBouncer) | Recommendation |
|---|---|---|---|:---:|
| **Option 1: Per-Service Roles**<br>(e.g. `campx_college_admin`, `campx_student_service`, `campx_course_mgmt`) | • Each microservice connects with unique database credentials.<br>• Schema and table DML grants restricted strictly to each service's domain.<br>• 12+ separate database credentials to provision, vault, and rotate. | • High defense-in-depth against service-level compromise.<br>• However, all services share cross-cutting tables (`audit.events`, `sys.outbox_events`, `core.persons`), requiring cross-schema grants anyway. | • **High Risk of Connection Exhaustion:** Supabase pooler on development tiers enforces strict connection limits (25–50 pooler slots). 4–5 microservices each configuring 10 pool connections would saturate the pooler and cause connection drops. | **NOT Recommended** at current architecture stage. |
| **Option 2: Single Dedicated Application Role**<br>(`campx_app_server`) | • Single unprivileged login role for all trusted Java microservices.<br>• One vaulted credential set injected via environment variable.<br>• Zero table ownership; `NOBYPASSRLS` strictly enforced. | • Service-level authorization is enforced by the API Gateway and application code.<br>• **Multi-tenant row-level isolation is enforced unconditionally at the PostgreSQL kernel level** via `SET LOCAL ROLE authenticated` and `request.jwt.claims`. | • **Pooler Safe:** Allows a consolidated HikariCP connection allocation fitting within the 25-connection pooler budget on development instances. | **RECOMMENDED** |

#### 2. Three-Tier Role Identity Model

To understand how security is enforced, the database architecture separates three distinct role concepts:

```
[Physical Connection Pool]      --> Login Role: 'campx_app_server'
                                     • Attributes: LOGIN, NOSUPERUSER, NOBYPASSRLS, CONNECTION LIMIT 25
                                     • Owns 0 database objects. Cannot bypass RLS.
                                     • Member of 'authenticated' (GRANT authenticated TO campx_app_server).

[Transaction Lifecycle]         --> Effective Role: 'authenticated'
                                     • Activated via: SET LOCAL ROLE authenticated;
                                     • Attributes: NOLOGIN, NOSUPERUSER, NOBYPASSRLS.
                                     • Evaluates RLS policies defined 'TO authenticated'.
                                     • Resets automatically to 'campx_app_server' upon COMMIT/ROLLBACK.

[Database Schema Ownership]     --> Table Owner: 'postgres' (Deployment / Migration Only)
                                     • Attributes: SUPERUSER, BYPASSRLS.
                                     • NEVER used by runtime application services.
```

#### 3. Mandatory Role-Membership Invariant
In PostgreSQL, a session can execute `SET ROLE <name>` or `SET LOCAL ROLE <name>` only if the session user is an explicit member of `<name>` or a superuser.
Therefore, `GRANT authenticated TO campx_app_server;` is a **mandatory invariant**. Without this grant, any transaction calling `SET LOCAL ROLE authenticated` fails immediately with PostgreSQL error:
`ERROR: 42501: must be member of role "authenticated"`.

#### 4. Specification of Proposed Role: `campx_app_server`
- **Login Capability:** `LOGIN` enabled (required for JDBC pooler authentication).
- **RLS Bypass:** `NOBYPASSRLS` strictly enforced.
- **Superuser Privilege:** `NOSUPERUSER` strictly enforced.
- **Administration Privileges:** `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`.
- **Table Ownership:** Zero table or schema ownership. `campx_app_server` owns NO database objects.
- **Connection Bound:** `CONNECTION LIMIT 25` to protect pooler capacity on development instances.
- **Session Constraints:**
  * `statement_timeout = '15s'` (prevents denial-of-service from unindexed runaway queries).
  * `idle_in_transaction_session_timeout = '30s'` (prevents leaked locks from hanging connections).
  * `search_path = core, stu, acd, iam, doc, audit, sys, public`.

---

### B. Trusted Tenant/User Context Propagation & Fail-Closed Behavior

#### 1. End-to-End Ingress to Database Trace

```
[Browser / Mobile Client]
       │
       │ HTTPS Request with Authorization: Bearer <Supabase-JWT>
       ▼
[CampXSync API Gateway (port 8080)]
       │
       ├─► 1. Anti-Spoofing: Strips untrusted incoming headers ('X-User-*', 'X-Tenant-*', 'X-Role-*')
       │      via GatewayHmacProtocol.STRIPPED_INBOUND_HEADERS.
       ├─► 2. Asymmetric JWT Validation: SupabaseJwtValidator verifies ES256 signature via JWKS.
       ├─► 3. Identity Resolution: IdentityResolver looks up authoritative business role & tenant in DB.
       ├─► 4. Gateway HMAC Signing: Gateway signs request with HMAC-SHA256, binding:
       │        - X-User-Id, X-Tenant-Id, X-User-Role
       │        - X-Gateway-Timestamp (enforcing 60s replay window)
       │        - X-Gateway-Signature
       ▼
[Java Domain Service Ingress (e.g., college-admin-service)]
       │
       ├─► 1. Cryptographic HMAC Verification: GatewayHmacVerifier validates signature & timestamp.
       │      Rejects missing HMAC, expired timestamps, or tampered headers fail-closed (HTTP 401/403).
       ├─► 2. UUID Syntax Validation: UserSecurityContext.fromHeaders parses X-User-Id and X-Tenant-Id
       │      via UUID.fromString(). Rejects non-UUID strings fail-closed.
       ▼
[Transaction Lifecycle Wrapper (UserSecurityContext.executeInTransaction)]
       │
       ├─► 1. Acquires JDBC Connection from HikariCP pool (authenticated as campx_app_server).
       ├─► 2. conn.setAutoCommit(false);
       ├─► 3. UserSecurityContext.applyClaimsAndRole(conn):
       │        a) SET LOCAL ROLE authenticated;
       │        b) SELECT set_config('request.jwt.claims', claimsJson, true);
       ▼
[Domain Repository Layer (JDBC PreparedStatement)]
       │
       ├─► 1. Defense-in-Depth Predicate: Explicit SQL clause WHERE s.tenant_id = ?
       ├─► 2. Submits SQL to PostgreSQL engine under active transaction context.
       ▼
[PostgreSQL Kernel Enforcement]
       │
       ├─► 1. Checks relforcerowsecurity -> Enforces RLS unconditionally.
       ├─► 2. Evaluates table policy (e.g., stu.students: students_select).
       ├─► 3. Evaluates core.current_tenant_id() -> Reads request.jwt.claims.
       ├─► 4. If tenant matches and user has permission -> Permitted.
       └─► 5. If tenant mismatched or claims unset -> Evaluates to FALSE / Fail-Closed.
       ▼
[Shared Services Persistence & Commit]
       │
       ├─► 1. Atomically inserts audit log to audit.events (enforced by events_insert policy).
       ├─► 2. Atomically inserts outbox event to sys.outbox_events.
       ├─► 3. conn.commit();
       │        (PostgreSQL automatically discards transaction-scoped claims and role).
       └─► 4. Emits IntelligenceContextEvent via ControlledIntelligenceAdapter.
```

#### 2. Fail-Closed Mechanics & Mathematical Proof

| Scenario | Database & Application Mechanism | Evaluation Result | Security Outcome |
|---|---|---|---|
| **Clean Session / Missing Context** | Query executed without setting `request.jwt.claims`. | `current_setting('request.jwt.claims', true)` returns `''`. `nullif('', '')` returns `NULL`. `core.current_tenant_id()` returns `NULL`. | Policy predicate `tenant_id = NULL` evaluates to SQL `UNKNOWN` (treated as `FALSE` in RLS). **0 rows returned; all mutations rejected.** |
| **Malformed JSON Claims** | Non-JSON text injected into `request.jwt.claims`. | In `core.current_tenant_id()`, `current_setting(...)::jsonb` cast fails with PostgreSQL syntax error `22P02`. | **Transaction immediately aborts and rolls back fail-closed.** |
| **Spoofed HTTP Header** | Untrusted client sends `X-Tenant-Id: <Victim-Tenant-Id>`. | Gateway's `STRIPPED_INBOUND_HEADERS` unconditionally deletes header before JWT validation. | **Spoofed header never reaches downstream services.** |
| **Connection Pool Leakage** | Next transaction on same pooled connection doesn't set context. | Parameters set with `is_local = true` and `SET LOCAL ROLE` are strictly bound to the active transaction and automatically reset upon `COMMIT` or `ROLLBACK`. | **Subsequent transaction executes with clean, empty context (fails closed).** |

#### 3. Evidence Classification: Inspection vs. Test-Proven vs. Unverified

| Component / Claim | Evidence Source | Status | Exact Proof / Gap |
|---|---|:---:|---|
| **Gateway Strips Headers** | `ReverseProxyHandler.java` (lines 306–313), `GatewayHmacProtocol.STRIPPED_INBOUND_HEADERS` | **PROVEN (Code & Tests)** | Verified in `GatewaySecurityTest` and `GatewayCorsSecurityTest`. |
| **Gateway ES256 Signature Validation** | `SupabaseJwtValidator.java`, `JwksClient.java` | **PROVEN (Code & Tests)** | Verified in `SupabaseJwtClaimParsingRegressionTest`. |
| **Java HMAC Verification & Replay Window** | `GatewayHmacVerifier.java`, `UserPreferenceControllerHttpTest.java` | **PROVEN (Code & Tests)** | Verified in `UserProfileControllerHttpTest` with 60s window. |
| **Java Transaction Context Application** | `UserSecurityContext.java` (`applyClaimsAndRole`) | **PROVEN (Code Inspection)** | Code inspection verifies `SET LOCAL ROLE` and `set_config`. |
| **PostgreSQL Function Fail-Closed** | `40_functions.sql` (`core.current_tenant_id`) | **PROVEN (Code Inspection)** | Verified SQL definition uses `nullif(..., '')::jsonb`. |
| **Live Multi-Tenant RLS Enforcement under `campx_app_server`** | PostgreSQL Live Catalog | **UNVERIFIED (G1 OPEN)** | **Cannot be claimed until staging execution under `campx_app_server` (`NOBYPASSRLS`).** |

---

### C. RLS Scope Consistency & Table-by-Table Evaluation

#### 1. Scope Reconciliation: Tier 1 (Core Scope) vs. Tier 2 (Extended Scope)

To eliminate the scope discrepancy between the 4 tables in the original approval scope and the 9 tables in the proposed scripts, the tables are strictly partitioned into two governance tiers:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ TIER 1: CORE APPROVAL SCOPE (4 TABLES) — Required for Wave 4 minimal Student Roster (DEC-G1-02A) │
├──────────────────────────┬──────────────────────────┬──────────────────────────┬────────────────┤
│ stu.students             │ core.persons             │ stu.guardianships        │ acd.batch_students
└──────────────────────────┴──────────────────────────┴──────────────────────────┴────────────────┘
                                           │
                                           ▼ (Dependent child entities)
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ TIER 2: EXTENDED DEMOGRAPHIC & SECTION SCOPE (5 TABLES) — Separate Decision (DEC-G1-02B)       │
├──────────────────────────┬──────────────────────────┬──────────────────────────┬────────────────┤
│ core.addresses           │ core.person_contacts     │ core.person_emergency_   │ core.person_   │
│                          │                          │ contacts                 │ identifiers    │
├──────────────────────────┴──────────────────────────┴──────────────────────────┴────────────────┤
│ acd.section_enrollments                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 2. Detailed Table Inventory & RLS Policy Analysis

| Table Name | Schema | Scope Tier | Current RLS | Proposed FORCE RLS | Foreign Key Dependencies | Existing Policies (`60_rls.sql`) | Operational Rationale |
|---|---|:---:|:---:|:---:|---|---|---|
| **`stu.students`** | `stu` | **Tier 1 (Core)** | `ENABLED` | **`FORCE` (DEC-G1-02A)** | `core.persons(id)`, `core.colleges(id)`, `acd.programs(id)` | `students_select`, `students_insert`, `students_update`, `students_delete`. Scoped by `tenant_id` and `stu01.*` permissions. | **Core Student Record.** Anchor for student academic lifecycle. FORCED RLS prevents cross-tenant roster access even if queries run as table owner. |
| **`core.persons`** | `core` | **Tier 1 (Core)** | `ENABLED` | **`FORCE` (DEC-G1-02A)** | Supertype anchor referenced by `stu.students`, `stu.guardianships` | `persons_select`, `persons_insert`, `persons_update`, `persons_delete`. Scoped by `tenant_id` and `core.pii.read` or self-access. | **Demographic Identity.** Holds student/parent PII. FORCED RLS guarantees strict data minimization and statutory DPDP compliance. |
| **`stu.guardianships`** | `stu` | **Tier 1 (Core)** | `ENABLED` | **`FORCE` (DEC-G1-02A)** | `stu.students(id)`, `core.persons(id)` | `guardianships_select`, `guardianships_insert`, `guardianships_update`, `guardianships_delete`. Scoped by `tenant_id`. | **Parental & Fee Linkage.** Controls parental access to grades and financial fee payments (`can_pay_fees`). Must be strictly isolated. |
| **`acd.batch_students`** | `acd` | **Tier 1 (Core)** | `ENABLED` | **`FORCE` (DEC-G1-02A)** | `acd.batches(id)`, `stu.students(id)` | `batch_students_select`, `batch_students_insert`, `batch_students_update`, `batch_students_delete`. Scoped by `tenant_id`. | **Cohort Membership.** Connects students to cohort batches and institutional roll numbers. Essential for student roster operations. |
| **`core.addresses`** | `core` | **Tier 2 (Ext)** | `ENABLED` | **`FORCE` (DEC-G1-02B)** | `core.tenants(id)`; referenced by `core.persons` | `addresses_select`, `addresses_insert`, `addresses_update`, `addresses_delete`. Scoped by `tenant_id` and `core.pii.read`. | **Postal Addresses.** Permanent and current residential addresses. Separate approval allows deferral if demographic persistence is phased. |
| **`core.person_contacts`** | `core` | **Tier 2 (Ext)** | `ENABLED` | **`FORCE` (DEC-G1-02B)** | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `person_contacts_select`, `person_contacts_insert`, `person_contacts_update`, `person_contacts_delete`. Scoped by `tenant_id`. | **Communication Channels.** Phone numbers, emails, WhatsApp. Child entity of `core.persons`. |
| **`core.person_emergency_contacts`** | `core` | **Tier 2 (Ext)** | `ENABLED` | **`FORCE` (DEC-G1-02B)** | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `person_emergency_contacts_select`, `person_emergency_contacts_insert`, etc. Scoped by `tenant_id`. | **Emergency Contacts.** Medical and family emergency contact information. |
| **`core.person_identifiers`** | `core` | **Tier 2 (Ext)** | `ENABLED` | **`FORCE` (DEC-G1-02B)** | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `person_identifiers_select`, `person_identifiers_insert`, `person_identifiers_update`, `person_identifiers_delete`. Scoped by `tenant_id`. | **Statutory Identifiers.** Aadhaar, PAN, APAAR, Passport hashes. High-risk statutory PII. |
| **`acd.section_enrollments`** | `acd` | **Tier 2 (Ext)** | `ENABLED` | **`FORCE` (DEC-G1-02B)** | `acd.course_sections(id)`, `stu.students(id)` | `section_enrollments_select`, `section_enrollments_insert`, `section_enrollments_update`, `section_enrollments_delete`. Scoped by `tenant_id`. | **Section Enrollments.** Timetable and course section assignments. Required when student course scheduling is integrated. |

#### 3. Policy Command Coverage Audit (`USING` vs. `WITH CHECK`)
All existing policies defined in [60_rls.sql](file:///D:/CampXSync/Documentation/DatabaseDesign/Supabase/Scripts_V3.0/60_rls.sql) for these tables were verified:
- **`SELECT`:** Scoped via `USING (tenant_id = (SELECT core.current_tenant_id()) AND ...)` -> Prevents reading foreign tenant rows.
- **`INSERT`:** Scoped via `WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND ...)` -> Prevents inserting rows belonging to a foreign tenant.
- **`UPDATE`:** Scoped via **both** `USING` and `WITH CHECK` with identical tenant predicates -> Prevents modifying foreign tenant rows AND prevents moving an existing row into another tenant.
- **`DELETE`:** Scoped via `USING (tenant_id = (SELECT core.current_tenant_id()) AND ...)` -> Prevents deleting foreign tenant rows.

#### 4. Operational & View Security Analysis
- **Database Migrations:** Schema migrations run by deployment tools connect as `postgres` superuser (which has `BYPASSRLS`). Migrations remain unhindered.
- **View Security Invoker Behavior:** In PostgreSQL, views execute under the view owner's privileges by default (which bypasses RLS unless `security_invoker = true` is set on the view). For the Student domain, Java repository queries execute directly against base tables (`stu.students`, `core.persons`, `acd.batch_students`), preventing view-level RLS bypasses.
- **Search Path Hijacking Protection:** All helper functions in `40_functions.sql` (`core.current_tenant_id`, `iam.has_permission`, etc.) are compiled with `SECURITY DEFINER SET search_path = ''` and use fully-qualified table identifiers. This completely neutralizes search_path injection attacks.

---

### D. Privileges and Object Security

#### 1. Three-Tier Least-Privilege Grants Matrix (Question 1 Resolution)

Under the mandatory three-tier security architecture:
1. **`campx_app_server` (Session Login Role):** Connects to the database and maintains connection pool sessions. Granted `CONNECT` on database, `USAGE` on application schemas (`core, stu, acd, iam, doc, audit, sys`), and membership in `authenticated`. It holds **ZERO direct DML privileges on domain or audit tables**. Any query executed outside of `UserSecurityContext.applyClaimsAndRole()` fails immediately with `SQLSTATE 42501 (permission denied)`.
2. **`authenticated` (Transactional Execution Role):** Activated inside transactions via `SET LOCAL ROLE authenticated`. Holds the required table-level DML privileges subject to RLS policies.
3. **`postgres` (Schema Owner & Migrations):** Sole owner of tables, functions, and views with `BYPASSRLS`.

| Role | Target Schemas / Objects | Operations Permitted | Operations Revoked / Denied | Security & Execution Rationale |
|---|---|---|---|---|
| **`campx_app_server`** | Schemas `core, stu, acd, iam, doc, audit, sys` | `USAGE`, role membership in `authenticated` | `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP` | Login role only; withholding direct table DML guarantees fail-closed isolation if claims are omitted. |
| **`authenticated`** | Core Roster (`stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`) | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | `TRUNCATE`, `ALTER`, `DROP` | Active transactional user; row visibility and mutation restricted 100% by RLS tenant policies. |
| **`authenticated`** | Demographic Child Tables (Tier 2: `core.addresses`, `core.person_*`, `acd.section_*`) | `SELECT`, `INSERT`, `UPDATE`, `DELETE` (Subject to DEC-G1-02B) | `TRUNCATE`, `ALTER`, `DROP` | Demographic child records; protected by tenant foreign-key cascade and RLS. |
| **`authenticated`** | Reference Tables (`core.tenants`, `core.colleges`, `acd.programs`, etc.) | `SELECT` | `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | Read-only reference structure for operational validation. |
| **`authenticated`** | Shared Services (`audit.events`, `audit.change_log`) | `SELECT`, `INSERT` (Append-Only) | `UPDATE`, `DELETE`, `TRUNCATE` | Append-only tamper-evident audit ledger. Tampering strictly denied. |
| **`authenticated`** | Outbox (`sys.outbox_events`, `sys.dead_letter_events`) | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | `TRUNCATE` | Transactional outbox pattern for event publishing. |

#### 2. Critical Shared Services Audit Gap & Resolution (Question 2 Resolution)
In the existing baseline migration (`75_grants.sql` line 109), `INSERT` on `audit.events` was revoked from `authenticated` under the assumption that audit rows would only be generated by database triggers.
However, in Wave 1 and Wave 2 Java services, the repositories (`PostgresSystemAuditLogRepository`, `PostgresDepartmentRepository`, `PostgresProgramRepository`) execute explicit atomic inserts into `audit.events` within the business transaction.
When Java executes `SET LOCAL ROLE authenticated`, direct inserts into `audit.events` fail unless:
1. `GRANT SELECT, INSERT ON TABLE audit.events, audit.change_log TO authenticated;` is provided, AND
2. A dedicated RLS policy `audit.events_insert` is created:
   ```sql
   CREATE POLICY events_insert ON audit.events FOR INSERT TO authenticated
   WITH CHECK (
       tenant_id = (SELECT core.current_tenant_id())
       OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
   );
   ```
   * **Tenant Isolation:** Scopes row insertion strictly to `core.current_tenant_id()`. An authenticated tenant user cannot insert audit rows on behalf of another tenant.
   * **Fail-Closed:** If claims are unset, `core.current_tenant_id()` returns `NULL`, causing `tenant_id = NULL` to evaluate to `UNKNOWN` (`FALSE`), rejecting the insert.
   * **Append-Only Invariant:** `UPDATE`, `DELETE`, and `TRUNCATE` remain strictly revoked from both `authenticated` and `campx_app_server`.

---

## 3. Deliverable A2 — Proposed SQL Scripts Inventory

All SQL scripts reside in 📁 [docs/reports/wave-4/g1-proposed-sql/](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/):

| Script File | Purpose & Operational Scope | Type | Changes in this Correction |
|---|---|:---:|---|
| [01_preflight_readonly.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/01_preflight_readonly.sql) | Introspects database connection identity, role attributes, table ownership, RLS flags, policy definitions, and existing grants. | `READ-ONLY` | Added audit policy checks and view `security_invoker` introspection. |
| [02_application_role_proposal.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/02_application_role_proposal.sql) | Idempotently defines `campx_app_server` with `LOGIN`, `NOBYPASSRLS`, `NOSUPERUSER`, connection limit 25, and grants membership in `authenticated`. | `PROPOSAL (DATED)` | Clarified mandatory `SET LOCAL ROLE` membership invariant and security boundaries. |
| [03_minimum_grants_proposal.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/03_minimum_grants_proposal.sql) | Grants schema USAGE, table DML on student entities, read-only on academic references, append-only on audit tables, and creates `events_insert` policy. | `PROPOSAL (DATED)` | Separated Tier 1 Core grants from Tier 2 Extended grants; added `events_insert` policy. |
| [04_rls_hardening_proposal.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/04_rls_hardening_proposal.sql) | Applies `FORCE ROW LEVEL SECURITY`. | `PROPOSAL (DATED)` | **Decoupled into Block 1 (Tier 1 Core Scope: 4 tables) and Block 2 (Tier 2 Extended Scope: 5 tables).** |
| [05_postchange_verification_readonly.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/05_postchange_verification_readonly.sql) | Asserts role invariants, checks 100% FORCE RLS coverage by tier, audits grants, and simulates transaction context fail-closed resolution. | `READ-ONLY` | Added role-membership verification and audit policy coverage check. |
| [06_rollback_proposal.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/wave-4/g1-proposed-sql/06_rollback_proposal.sql) | Reverts FORCE RLS to ENABLE RLS, revokes privileges, drops custom audit policy, terminates active sessions, and drops `campx_app_server`. | `PROPOSAL (ROLLBACK)` | **Modularized rollback (reverts Tier 2 and Tier 1 independently); adds session termination before role drop.** |

> [!CAUTION]
> **NONE of the mutation scripts (`02`, `03`, `04`, `06`) have been executed.** They are proposals requiring explicit owner review and separate written approval tokens before any execution.

---

## 4. Deliverable A3 — Security Verification Plan (Staging-Only)

This plan defines the exact automated checks required to close Gate G1 once approved:

1. **Connection Target Guard:** Validate that JDBC connects to development ref `azulbaxszsqxymqmlwfe` on `aws-0-ap-south-1.pooler.supabase.com:6543`. Immediate fail-closed if host or username contains production ref `ekspahbsbwdpraaqfqui` or legacy staging ref `kpwdvvcschankgggitnj`.
2. **Role Attribute Assertion:** Verify `pg_roles` for `campx_app_server` returns `rolbypassrls == false` and `rolsuper == false`.
3. **Role Membership Assertion:** Verify `campx_app_server` is a member of `authenticated`.
4. **Context Propagation & Transaction Boundary Tests:**
   - *Test 4.1 (Positive Path):* Set `request.jwt.claims` for Tenant A (`11111111-...`). Query `stu.students`. Must return only Tenant A students.
   - *Test 4.2 (Missing Context Fail-Closed):* Do not set `request.jwt.claims`. Query `stu.students`. Must return exactly **0 rows**.
   - *Test 4.3 (Cross-Tenant Read Denial):* Set `request.jwt.claims` for Tenant A. Execute `SELECT * FROM stu.students WHERE id = '<Tenant-B-Student-UUID>'`. Must return **0 rows**.
   - *Test 4.4 (Cross-Tenant Insert Denial):* Set `request.jwt.claims` for Tenant A. Attempt to execute `INSERT INTO stu.students (tenant_id, ...) VALUES ('22222222-2222-2222-2222-222222222222', ...)`. Must be rejected by PostgreSQL kernel with `new row violates row-level security policy for table "students"`.
   - *Test 4.5 (Cross-Tenant Update Denial):* Set `request.jwt.claims` for Tenant A. Attempt to execute `UPDATE stu.students SET enrollment_no = 'HACK' WHERE id = '<Tenant-B-Student-UUID>'`. Must update **0 rows**.
   - *Test 4.6 (Connection Pool Leakage Test):* Acquire pooled connection. Execute transaction with Tenant A context. Commit. Acquire same connection in second request without context. Query `stu.students`. Must return **0 rows**, proving transaction-scoped configuration did not survive `COMMIT`.

---

## 5. Concise Decision Register

| Decision ID | Required Decision | Recommended Option | Security & Operational Rationale | Objects / Roles / Functions Affected | Approval Needed |
|:---:|---|---|---|---|:---:|
| **DEC-G1-01** | Create dedicated application login role. | Create `campx_app_server` with `LOGIN`, `NOSUPERUSER`, `NOBYPASSRLS`, connection limit 25, and grant membership in `authenticated`. | Eliminates the superuser `postgres` vulnerability. Membership in `authenticated` enables `SET LOCAL ROLE authenticated` inside transactions. | Role `campx_app_server`, role `authenticated`. | **YES**<br>`APPROVE-G1-ROLE-CREATION` |
| **DEC-G1-02A** | Apply `FORCE ROW LEVEL SECURITY` to Core Scope (Tier 1). | Force RLS on 4 core tables: `stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`. | Mandatory for minimal Wave 4 student roster. Prevents table owner RLS bypass. Reconciles the original approval scope. | Tables: `stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`. | **YES**<br>`APPROVE-G1-FORCE-RLS-TIER1` |
| **DEC-G1-02B** | Apply `FORCE ROW LEVEL SECURITY` to Extended Scope (Tier 2). | Force RLS on 5 child tables: `core.addresses`, `core.person_contacts`, `core.person_emergency_contacts`, `core.person_identifiers`, `acd.section_enrollments`. | Extends FORCED RLS to demographic child entities and section enrollments. Kept as a separate decision so core roster approval is not blocked. | Tables: `core.addresses`, `core.person_contacts`, `core.person_emergency_contacts`, `core.person_identifiers`, `acd.section_enrollments`. | **YES**<br>`APPROVE-G1-FORCE-RLS-TIER2` |
| **DEC-G1-03** | Minimum Grants & Shared Services Audit Policy. | Grant schema `USAGE` and helper functions to `campx_app_server`, ensure `authenticated` holds required table DML, grant missing `INSERT` on `audit.events` to `authenticated`, and create `events_insert` policy. | Enforces strict three-tier execution: `campx_app_server` holds no direct domain table DML (fails closed), while `authenticated` executes tenant-scoped DML and atomic audit logging. | Schemas `core, stu, acd, audit, sys`, Table `audit.events`, Policy `audit.events_insert`. | **YES**<br>`APPROVE-G1-GRANTS` |
| **DEC-G1-04** | Credential Provisioning & Vaulting. | Provision `campx_app_server` password externally in Supabase Dashboard / Vault; inject into Java runtime via `CAMPX_DB_PASSWORD`. | Passwords must never be stored in Git or reports. | Secrets / Vault / Env configuration. | **YES**<br>Owner Vault Action |
| **DEC-G1-05** | Pre-approve Rollback Strategy. | Adopt `06_rollback_proposal.sql` with modular Tier 1/Tier 2 reversal and session termination. | Guarantees safe, automated recovery if staging verification encounters anomalies. | Script `06_rollback_proposal.sql`. | **YES**<br>`APPROVE-G1-ROLLBACK` |

---

## 6. Unverified Claims & Missing Evidence

The following assertions cannot be verified until explicit owner approval and controlled staging execution take place:

1. **Live PostgreSQL RLS Enforcement under `campx_app_server`:** While code inspection proves the policies and context functions are correctly constructed, live rejection of cross-tenant queries under `campx_app_server` (`NOBYPASSRLS`) has **NOT** been observed on the live database catalog.
2. **Supabase Pooler Port 6543 Parameter Reset under High Concurrency:** In theory, PgBouncer resets transaction-scoped variables upon `COMMIT`. This must be validated through live concurrency tests on `aws-0-ap-south-1.pooler.supabase.com:6543`.
3. **Absence of Conflicting Default Privileges in Catalog:** Information schema grants were inspected statically; live preflight execution (`01_preflight_readonly.sql`) on dev ref `azulbaxszsqxymqmlwfe` is required to confirm no undocumented global grants exist.

---

## 7. Readiness Status & Next Safe Steps

- **G1 Gate Status:** **FAILING / NOT VERIFIED (OPEN)**. Remains open until this proposal is reviewed, approved, and executed in staging.
- **Java Student Domain Implementation:** **BLOCKED** from shared database persistence until Gate G1 is formally closed.
- **Proposal Review Status:** **READY FOR OWNER APPROVAL REVIEW**. All scripts are modular, documented, and safe.
- **Immediate Next Safe Step:**
  1. Owner to review the Decision Register (`DEC-G1-01` through `DEC-G1-05`).
  2. If approved, owner provides explicit approval tokens:
     * `APPROVE-G1-ROLE-CREATION` (for DEC-G1-01)
     * `APPROVE-G1-FORCE-RLS-TIER1` (for DEC-G1-02A)
     * `APPROVE-G1-FORCE-RLS-TIER2` (for DEC-G1-02B, or defer)
     * `APPROVE-G1-GRANTS` (for DEC-G1-03)
     * `APPROVE-G1-ROLLBACK` (for DEC-G1-05)
  3. Execute `01_preflight_readonly.sql` on dev ref `azulbaxszsqxymqmlwfe` as the first staging step once authorized.
