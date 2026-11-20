# CampXSync RBAC Open Findings Register

**Status:** ACTIVE  
**Last Updated:** 2026-11-20  
**Context:** Captured during Phases 0–3 of Enterprise RBAC (IADM & CADM) implementation in DEV.

---

## 1. Open Findings

### FINDING-RBAC-01: Ambiguous Test User Role in Dev Database
- **Impacted Components:** `api-gateway` (`LiveDatabaseIdentityResolutionTest.java`) vs `college-admin-service` (`TestEnvironmentGuard.java`).
- **Description:** Test user `ba77b8f7-ab32-46bd-85d2-5aade3526880` was seeded as `COLLEGE_ADMIN` in tenant `a0000000-0000-0000-0000-000000000001` (`wave0_dev_test_seed.sql`), but was also inserted as `SUPER_ADMIN` into `plat.platform_admins` by `TestEnvironmentGuard.java` for downstream test setups. Because `JdbcIdentityResolver` prioritizes `plat.platform_admins`, this caused gateway identity tests expecting `COLLEGE_ADMIN` to resolve `SUPER_ADMIN`.
- **Status:** Mitigated in code; permanent cleanup recommended: separate platform admin test UUID from tenant college admin test UUID.

### FINDING-RBAC-02: Hardcoded Route Destination Scheme in GatewayConfig
- **Impacted Components:** `api-gateway` (`GatewayConfig.java`).
- **Description:** While most services map to `http://localhost:<PORT>`, ports 8096 (Lifecycle) and 8098 (Documents) were hardcoded to `http://127.0.0.1:<PORT>`. Tests checking exact URLs required adjustment.
- **Status:** Mitigated in tests; recommendation is to normalize all routes to use configurable host property (e.g., `${DOWNSTREAM_HOST:localhost}`).

### FINDING-RBAC-03: Hardcoded Non-Existent Tenant in Legacy Integration Test
- **Impacted Components:** `services/administration-services/institute-admin-service/src/test/.../PostgresCollegeRepositoryIntegrationTest.java`.
- **Description:** Test had hardcoded tenant UUID `0c914bb2-f63b-472c-a99a-39977112935d` which did not exist in dev database.
- **Status:** Resolved by querying dynamic staging tenant via `TestEnvironmentGuard.verifySafeStagingTenant(...)`.

### FINDING-RBAC-04: IADM-04 Billing Endpoints Lack Authorization Annotations
- **Impacted Components:** `services/administration-services/institute-admin-service` (Billing endpoints).
- **Description:** Identified in Phase 0.5: billing endpoints currently lack permission-key enforcement (`adm03`/`adm04`).
- **Status:** Open for subsequent wave; logged per Matrix §22.1.
