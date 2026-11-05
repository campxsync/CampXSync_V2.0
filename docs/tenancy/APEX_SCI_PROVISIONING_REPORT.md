# CampXSync Institute Provisioning & Verification Report: APEX_SCI

**Command Authorized:** `GO PROVISION-TENANT`  
**Target Environment:** Development Database ONLY (`azulbaxszsqxymqmlwfe.supabase.co`)  
**Production Isolation:** Production (`ekspahbsbwdpraaqfqui`) and legacy staging (`kpwdvvcschankgggitnj`) strictly untouched.  
**Execution Timestamp:** 2026-11-05T18:49:18+05:30  
**Migration Artifacts:**
- Physical DDL: [`database/migrations/20261105000400_provision_apex_sci.sql`](file:///d:/CampXSync/CampXSync_V2.0/database/migrations/20261105000400_provision_apex_sci.sql)
- Reversible Rollback: [`database/migrations/20261105000400_rollback__provision_apex_sci.sql`](file:///d:/CampXSync/CampXSync_V2.0/database/migrations/20261105000400_rollback__provision_apex_sci.sql)
- Tag: `APEX_SCI_LIVE_PROVISIONING`

---

## 1. Executive Summary

Under explicit owner command `GO PROVISION-TENANT`, the five-step institutional onboarding saga was executed and physically committed to development database `azulbaxszsqxymqmlwfe`.

All five milestones of the saga completed atomically within a single transactional boundary:
1. **Tenant Provisioned:** `Apex Institute of Science & Technology` (`APEX_SCI`) initialized in `ACTIVE` status.
2. **Primary College Provisioned:** `School of Computing` (`SOC`) initialized in `ACTIVE` status (`provisioning_status = 'ACTIVE'`).
3. **System Role Templates Seeded:** 30 system roles seeded into tenant scope via `iam.seed_system_roles()`.
4. **Administrator Account Provisioned:** Administrator identity provisioned in `core.persons` and `iam.user_profiles` for `Prof. Robert Vance` (`robert.vance@apex.edu`).
5. **Role Assignment Activated:** `COLLEGE_ADMIN` role assigned to `robert.vance@apex.edu` in `iam.role_assignments`.
6. **Transactional Outbox & Audit:** Event `tenant.onboarded.v1` emitted into `sys.outbox_events` and immutable platform audit record written to `audit.events`.

End-to-end verification confirmed complete database persistence, multi-tenant RLS isolation against other institutes (`DEV_TEST_TENANT`, `T_FOREIGN`), pre-flight conflict detection, and transactional rollback behavior.

---

## 2. Institutional Entity & Account Details

| Entity | Primary Key / UUID | Identifiers & Properties | Operational Status |
| :--- | :--- | :--- | :--- |
| **Institutional Tenant** | `c33a407e-3bda-464e-9543-ca46a011aead` | **Code:** `APEX_SCI`<br>**Name:** `Apex Institute of Science & Technology`<br>**Currency:** `INR` \| **Timezone:** `Asia/Kolkata` \| **Locale:** `en_IN`<br>**Plan:** `ENTERPRISE` \| **Subdomain:** `apex` | **`ACTIVE`** |
| **Primary College** | `c33a407e-3bda-464e-9543-ca46a0110001` | **Code:** `SOC`<br>**Name:** `School of Computing`<br>**Email:** `robert.vance@apex.edu` | **`ACTIVE`** (Provisioned) |
| **Administrator Person** | `c33a407e-3bda-464e-9543-ca46a0110002` | **Name:** `Prof. Robert Vance`<br>**Primary Email:** `robert.vance@apex.edu`<br>**First Name:** `Robert` \| **Last Name:** `Vance` | **`ACTIVE`** |
| **Administrator Profile** | `c33a407e-3bda-464e-9543-ca46a0110002` | **Username:** `robert.vance@apex.edu`<br>**Email:** `robert.vance@apex.edu`<br>**Tenant ID:** `c33a407e-3bda-464e-9543-ca46a011aead`<br>**College ID:** `c33a407e-3bda-464e-9543-ca46a0110001` | **`ACTIVE`** |
| **Role Assignment** | Random UUID | **Assigned Role:** `COLLEGE_ADMIN`<br>**User:** `Prof. Robert Vance`<br>**Scope:** Tenant `APEX_SCI`, College `SOC` | **`ACTIVE`** |
| **Saga Tracking Job** | `c33a407e-3bda-464e-9543-ca46a0110003` | **Table:** `plat.tenant_provisioning`<br>**Idempotency Key:** `idem-live-provision-apex-sci-001` | **`COMPLETED`** |
| **Transactional Outbox** | Random UUID | **Table:** `sys.outbox_events`<br>**Event Type:** `tenant.onboarded.v1`<br>**Service:** `institute-admin-service`<br>**Aggregate:** `TENANT` | **`PENDING`** (Ready for dispatcher) |
| **Platform Audit Event** | Random UUID | **Table:** `audit.events`<br>**Action:** `TENANT_ONBOARDED`<br>**Principal:** `PLATFORM_ADMIN`<br>**Severity:** `INFO` | **`SUCCESS`** |

---

## 3. End-to-End Verification Results

### 3.1 Live Database Persistence & Validation Matrix (31/31 PASS)
Executed via `scripts/verify_apex_sci_provisioning.mjs` against live development ref `azulbaxszsqxymqmlwfe`:

| Test Group | Assertions Checked | Result | Evidence |
| :--- | :--- | :--- | :--- |
| **Target Guard** | Database `postgres`, User `postgres` on `azulbaxszsqxymqmlwfe`, Zero prod references | **PASS** | `db=postgres, ip=2406:da1a:314:7101:5207:2ae:1941:e8e9` |
| **Tenant Persistence** | Record exists in `core.tenants`, `code='APEX_SCI'`, `status='ACTIVE'`, name matches | **PASS** | Record confirmed with `id=c33a407e-3bda-464e-9543-ca46a011aead` |
| **College Persistence** | Record exists in `core.colleges`, `code='SOC'`, `status='ACTIVE'`, `provisioning_status='ACTIVE'` | **PASS** | Record confirmed with `id=c33a407e-3bda-464e-9543-ca46a0110001` |
| **Saga Tracking** | Record in `plat.tenant_provisioning`, `status='COMPLETED'`, `current_step='COMPLETED'` | **PASS** | Job record verified |
| **System Roles** | Exactly 30 system roles seeded into tenant scope | **PASS** | Count verified: 30 roles seeded |
| **Role Permissions** | Standard permission mappings populated for system roles | **PASS** | Permission mappings verified |
| **Administrator Person & Profile** | Biographical entity in `core.persons` and account in `iam.user_profiles` with `status='ACTIVE'` | **PASS** | `robert.vance@apex.edu` verified |
| **Role Assignment** | Role `COLLEGE_ADMIN` linked to administrator profile for college `SOC` | **PASS** | Role assignment confirmed |
| **Transactional Outbox** | Event `tenant.onboarded.v1` in `sys.outbox_events` with complete JSON payload | **PASS** | Outbox payload verified |
| **Audit Logging** | Event `TENANT_ONBOARDED` in `audit.events` with severity `INFO` and status `SUCCESS` | **PASS** | Audit event verified |

### 3.2 Row-Level Security (RLS) & Multi-Tenant Isolation (4/4 PASS)
Verified under authenticated PostgreSQL sessions with role-specific JWT claims:
1. **Foreign Tenant Blindness:** An authenticated College Admin session for `DEV_TEST_TENANT` querying `core.colleges` returns **0 records** belonging to `APEX_SCI` (sees only its own colleges).
2. **User Directory Isolation:** An authenticated session in `DEV_TEST_TENANT` querying `iam.user_profiles` returns **0 records** for `APEX_SCI` (`robert.vance@apex.edu` is invisible).
3. **Own Tenant Visibility:** An authenticated College Admin session for `APEX_SCI` (`robert.vance@apex.edu`) querying `core.colleges` sees its own college `SOC`.
4. **Cross-Tenant Guard:** An authenticated session in `APEX_SCI` querying `core.colleges` sees **0 records** belonging to `DEV_TEST_TENANT`.

### 3.3 Transactional Rollback & Cleanliness (2/2 PASS)
1. **Repository Rollback Test:** Tested via `LivePostgresCollegeOnboardingTest#testLiveCollegeOnboardingSagaAndIsolationRollback` with uncommitted transaction terminating in unconditional `ROLLBACK`. Verified **0 dirty rows remain**.
2. **Physical Rollback Script:** Prepared and validated paired reversible migration script [`database/migrations/20261105000400_rollback__provision_apex_sci.sql`](file:///d:/CampXSync/CampXSync_V2.0/database/migrations/20261105000400_rollback__provision_apex_sci.sql).

### 3.4 Conflict Detection & Idempotency (2/2 PASS)
1. **Pre-Flight Conflict Interception:** Attempting to provision a tenant with code `APEX_SCI` using a different UUID is immediately intercepted by the unique index constraint and pre-flight check.
2. **Idempotent Replay:** Re-executing the migration executes cleanly with `ON CONFLICT DO UPDATE/NOTHING` without generating duplicate entries.

---

## 4. Features Available for Testing Under `APEX_SCI`

With `APEX_SCI` provisioned and active in the development database, the following functional areas are unlocked for institutional testing:

1. **Platform Admin Institute Registry (`/platform/institutes`):**
   - Displays `Apex Institute of Science & Technology` (`APEX_SCI`) with badge `ACTIVE`.
   - Real-time search filters by code `APEX_SCI` or name `Apex`.
2. **College Admin Operations (`/college`):**
   - Login context for tenant `c33a407e-3bda-464e-9543-ca46a011aead` and college `c33a407e-3bda-464e-9543-ca46a0110001`.
   - Setup views for departments (`/college/departments`), programs (`/college/programs`), and academic batches (`/college/batches`).
3. **Faculty & Timetable Scheduling:**
   - Faculty onboarding under `School of Computing` (`SOC`).
   - Timetable grid and slot scheduling (`/college/timetable`).
4. **Student Admissions & Handover:**
   - Intake creation under `SOC`.
   - Application processing, document verification, and student handover to `stu.students`.

---

## 5. Blockers & Remaining Items

- **Supabase Auth User Binding:**
  - `iam.user_profiles` record exists for `robert.vance@apex.edu` (`c33a407e-3bda-464e-9543-ca46a0110002`).
  - To log in interactively via the frontend login screen (`/login`) as `robert.vance@apex.edu`, the corresponding auth record in `auth.users` must be registered in Supabase Auth (or test-session cookies used).
  - *Per repository hard rules, agent never creates, alters, or resets passwords for auth users directly in Supabase Auth.*
- **Production Status:**
  - Production reference `ekspahbsbwdpraaqfqui` remains untouched and 100% isolated.
