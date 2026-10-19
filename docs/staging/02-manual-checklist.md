# Track A — Supabase Staging Dashboard Manual Checklist

**Document**: `docs/staging/02-manual-checklist.md`  
**Date**: 2026-10-13  
**Target Environment**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**Scope**: Dashboard configuration, Auth hooks, pooler setup, and test tenant provisioning that cannot be performed programmatically via local code.

---

## 1. Project Reference Validation (Non-Secret)

### Action & Click-Path:
1. Log in to [Supabase Management Dashboard](https://supabase.com/dashboard).
2. Open the project designated for CampXSync Staging.
3. Inspect the browser address bar.

### How to Confirm:
- The URL **MUST** strictly begin with:
  ```text
  https://supabase.com/dashboard/project/kpwdvvcschankgggitnj
  ```
- If the project ref in the URL matches `ekspahbsbwdpraaqfqui` (PRODUCTION), **STOP IMMEDIATELY**.
- Under Rule 1 and Rule 2, never trust a friendly name containing "STAGING"; verify the ref string `kpwdvvcschankgggitnj`.

---

## 2. Authentication Custom Access Token Hook Setup

The CampXSync security model relies on the JWT containing the user's active `tenant_id` in `app_metadata`. This is stamped dynamically by the PostgreSQL hook function `iam.custom_access_token_hook(event jsonb)` created in `85_auth_hook.sql`.

### Action & Click-Path:
1. In the Supabase Dashboard, navigate to:
   `Authentication` (key icon) → `Hooks` (tab in left sub-menu).
2. Locate the row titled **"Custom Access Token"** (MFA / Claims).
3. If not enabled, click **"Enable Hook"** (or edit existing).
4. Select Hook Type: **"Postgres Function"**.
5. Select Schema: **`iam`**.
6. Select Function: **`custom_access_token_hook(event jsonb)`**.
7. Click **Save**.

### How to Confirm:
- Status badge shows **"Enabled"** (green checkmark).
- Function reference displays: `pg-functions://postgres/iam/custom_access_token_hook`.
- Subtitle confirms the function executes on every token issuance and refresh.

---

## 3. Staging Test Users & Tenant Provisioning

### Action & Click-Path:
1. **Auth Users**:
   - Navigate to `Authentication` → `Users`.
   - Ensure at least one active test administrator user exists.
   - Note the user's `UUID` (non-secret).
2. **Database App Tables**:
   - Navigate to `SQL Editor` (or `Table Editor`).
   - Run:
     ```sql
     SELECT id, name, slug, status FROM core.tenants WHERE deleted_at IS NULL;
     ```
   - Ensure a test tenant exists (e.g. `TENANT_CAMPX_STAGING` or a designated UUID).
   - Ensure the test user has a corresponding active row in `iam.user_profiles`:
     ```sql
     SELECT id, tenant_id, full_name, email, status FROM iam.user_profiles WHERE id = '<test-user-uuid>'::uuid;
     ```
   - Ensure the profile has `status = 'ACTIVE'` and `deleted_at IS NULL`, and points to the valid `tenant_id`.

### How to Confirm:
- Query in `verify-readonly.sql` (Query 15) returns at least 1 active tenant in `core.tenants` and 1 active profile in `iam.user_profiles`.

---

## 4. JDBC Connection & Pooler Architectural Setup

To enable safe, automated integration tests without firewall or DNS issues, the staging database connection string must be configured correctly.

### Direct Connection vs Supabase Pooler:
| Characteristic | Direct Database Connection | Supabase Connection Pooler (Supavisor) |
| :--- | :--- | :--- |
| **Hostname** | `db.kpwdvvcschankgggitnj.supabase.co` | `aws-0-ap-south-1.pooler.supabase.com` (or regional cluster) |
| **IP Version** | **IPv6 ONLY** in most AWS/Supabase regions | **Dual-Stack (IPv4 and IPv6)** |
| **Workstation Compatibility** | Fails on standard developer machines or CI runners without native IPv6 routing | **Works reliably on all standard networks (IPv4)** |
| **Max Connections** | Limited by database compute tier (e.g. 60–100) | Scales to thousands of client connections |

### Session Mode vs Transaction Mode:
Supabase pooler exposes two distinct ports:
1. **Port 6543 (Transaction Mode)**:
   - Connections are returned to the pool after every transaction.
   - **Critical Java JDBC Incompatibility**: Does NOT support server-side prepared statements (`PreparedStatement`). If Java JDBC driver attempts to prepare statements across pooled sessions, it throws `PSQLException: prepared statement "S_1" does not exist`. Requires setting `prepareThreshold=0` on the JDBC URL.
2. **Port 5432 (Session Mode)**:
   - Client is assigned a dedicated backend session for the duration of the TCP connection.
   - **Full JDBC Compatibility**: Fully supports server-side prepared statements, session variables, temporary tables, and standard transaction isolation.

### Recommended Configuration for Java Integration Tests:
Use the **Supabase Connection Pooler in Session Mode (Port 5432)**.

### Connection Parameters:
- **Host**: `aws-0-ap-south-1.pooler.supabase.com` (verify your region in dashboard)
- **Port**: `5432`
- **Database**: `postgres`
- **Username Format**: `postgres.kpwdvvcschankgggitnj`  
  *(The user must be in the form `<role>.<project-ref>`, which matches `TestEnvironmentGuard` pooler requirements)*
- **SSL Mode**: `sslmode=require`
- **Constructed JDBC URL**:
  ```text
  jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require
  ```

### Dashboard Retrieval Click-Path:
1. Navigate to: `Project Settings` (gear icon) → `Database`.
2. Scroll to section **"Connection string"**.
3. Select tab **"URI"** or **"JDBC"**, and select mode **"Session"** (Port 5432).
4. Verify hostname ends with `pooler.supabase.com` and user is `postgres.kpwdvvcschankgggitnj`.
5. Set these in your private shell environment (never in git files or chat):
   - `export CAMPX_TEST_ENV=staging`
   - `export SUPABASE_JDBC_URL="jdbc:postgresql://..."`
   - `export SUPABASE_USER="postgres.kpwdvvcschankgggitnj"`
   - `export SUPABASE_PASSWORD="..."`
   - `export CAMPX_TEST_TENANT_ID="<tenant-uuid>"`
   - `export CAMPX_TEST_USER_ID="<user-uuid>"`
