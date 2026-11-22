# Part G: API Clients & Machine Identity Governance (R27)

**Document:** `docs/audits/rbac-coverage/Part-G_api-clients.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Machine Identity Architecture (`iam.api_clients`)

**[CONFIRMED: Database inspection of schema `iam` on DEV `azulbaxszsqxymqmlwfe`]:**

Machine identities for external systems and microservice integration operate outside the normal human user profile structure:
- **Client Identity Table:** `iam.api_clients` (`Scripts_V3.0/20_tables.sql:872`)
- **Granular Grant Table:** `iam.api_client_grants` (`Scripts_V3.0/20_tables.sql:900`)
- **Live Active Client Count:** **0 clients** (Table is completely empty in DEV).

### Schema Governance Alignment against Matrix §21.7
| Governance Rule (§21.7) | Database Schema Attribute | Implementation Status | Evidence / Notes |
| :--- | :--- | :--- | :--- |
| **1. Named Human Owner** | `owner_user_id UUID NOT NULL` | **CONFIRMED (Schema)** | Foreign key enforces link to `iam.user_profiles(tenant_id, id)`. |
| **2. Maximum 90-Day Expiry** | `expires_at TIMESTAMPTZ` | **CONFIRMED (Schema)** | Column present; enforcement of 90-day ceiling is in repository logic. |
| **3. Scheduled Secret Rotation** | `updated_at`, `status` | **PARTIAL** | Status allows `ACTIVE`, `SUSPENDED`, `REVOKED`. No automated cron rotation. |
| **4. Explicit Scopes (No Wildcard)** | `iam.api_client_grants` | **CONFIRMED (Schema)** | Binds specific `permission_id`, `college_id`, `department_id`, and `resource_scope` JSONB. Wildcard `*` is not supported. |
| **5. Separate Create vs Issue Powers** | Java Service Repository | **NOT ALIGNED** | `PostgresApiClientRepository.java` handles unified CRUD. Matrix §21.7 separation (R01 creates, R03 rotates) is not yet decoupled. |
| **6. Audited Usage & Tracking** | `last_used_at`, `trg_audit` | **CONFIRMED (Schema)** | Audit trigger captures changes; `last_used_at` records invocation timestamp. |
| **7. Immediate Revocation** | `status = 'REVOKED'`, `deleted_at` | **CONFIRMED** | Soft-delete and status check fail closed immediately. |
| **8. Machine Identity Only** | Separate from `iam.user_profiles` | **CONFIRMED** | Authenticates as dedicated service account in `auth.users`. |
| **9. `adm03` Status** | `adm03.*` permission keys | **CONFIRMED DORMANT** | Marked dormant per Owner Decision IADM-D5; `adm03.manage` is on no role template. |

---

## 2. Endpoints & Code Implementation

**[CONFIRMED: Source code audit of `institute-admin-service`]:**

1. **Repository:** `PostgresApiClientRepository.java` in `services/administration-services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresApiClientRepository.java`.
   - Implements `createClient()`, `findById()`, `findAll()`, `update()`, and `revokeClient()`.
2. **Access Control on API Client Tables:**
   - Evaluated by PostgreSQL RLS in `60_rls.sql:1947-1951`:
     - `SELECT` requires `adm03.view`
     - `INSERT` / `UPDATE` requires `adm03.write`
     - `DELETE` requires `adm03.manage`
3. **The `adm03` Deadlock:**
   - `COLLEGE_ADMIN` holds `adm03.view` and `adm03.write`, but NOT `adm03.manage`.
   - `INSTITUTION_ADMIN` holds `adm03.view` and `adm03.write`, but NOT `adm03.manage`.
   - `adm03.manage` is assigned to **NO role template** in `iam.role_templates`.
   - Therefore, no standard administrator can delete or fully govern API clients without operating as `TENANT_ADMIN` (holding `*`) or bypassing RLS as `postgres`.
