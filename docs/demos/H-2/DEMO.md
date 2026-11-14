# DEMO EVIDENCE REPORT: H-2

**Story Title:** ACD-10 Platform Dashboard on Real Postgres Tenants/Users
**Execution Timestamp:** 2026-11-14T07:01:40.625Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Platform Admin Auth**<br>Platform admin opens operator dashboard via test session (real plat.platform_admins user) | **PASS** | [`screenshots/01_platform_admin_auth.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/01_platform_admin_auth.png ) |
| 2 | **Wait Dashboard Heading**<br>Platform Operator Dashboard heading is visible | **PASS** | [`screenshots/02_wait_dashboard_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/02_wait_dashboard_heading.png ) |
| 3 | **KPI Cards Visible**<br>Real KPI strip rendered from ACD-10 Postgres aggregates (not hardcoded 3/3/30) | **PASS** | [`screenshots/03_kpi_cards_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/03_kpi_cards_visible.png ) |
| 4 | **Tenant Count KPI**<br>Onboarded institutes KPI shows live tenantCount from core.tenants | **PASS** | [`screenshots/04_tenant_count_kpi.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/04_tenant_count_kpi.png ) |
| 5 | **Institutes Table Loaded**<br>Institutes table lists real tenants (not empty mock list) | **PASS** | [`screenshots/05_institutes_table_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/05_institutes_table_loaded.png ) |
| 6 | **Dev Test Tenant Row**<br>DEV_TEST_TENANT row present from core.tenants | **PASS** | [`screenshots/06_dev_test_tenant_row.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/06_dev_test_tenant_row.png ) |
| 7 | **Gateway Platform Dashboard API**<br>Gateway returns live platform dashboard payload for PLATFORM_ADMIN | **PASS** | [`screenshots/07_gateway_platform_dashboard_api.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/07_gateway_platform_dashboard_api.png ) |
| 8 | **DB Read-Back Tenants**<br>Live DB confirms core.tenants rows exist as dashboard source of truth | **PASS** | [`screenshots/08_db_read_back_tenants.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/08_db_read_back_tenants.png ) |
| 9 | **DB Read-Back Role Templates**<br>Live DB confirms iam.role_templates exist (not hardcoded 30) | **PASS** | [`screenshots/09_db_read_back_role_templates.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots/09_db_read_back_role_templates.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-H-2-685150`

---

## 4. Performance & Timings

- **Platform Admin Auth:** 7246 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-2/screenshots)
