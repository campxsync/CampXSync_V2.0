# DEMO EVIDENCE REPORT: STM-04-EN-001

**Story Title:** Supabase lifecycle persistence and CRUD integration
**Execution Timestamp:** 2026-11-14T11:25:42.179Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Lifecycle Desk backed by PostgresLifecyclePersistence | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Lifecycle Desk Loaded**<br>College lifecycle heading proves UI → gateway → lifecycle-service path | **PASS** | [`screenshots/02_lifecycle_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/02_lifecycle_desk_loaded.png ) |
| 3 | **Summary Section Visible**<br>Standing summary section reads aggregated lifecycle state | **PASS** | [`screenshots/03_summary_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/03_summary_section_visible.png ) |
| 4 | **Total Standing Stat Visible**<br>Total student standing count is rendered from live service data | **PASS** | [`screenshots/04_total_standing_stat_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/04_total_standing_stat_visible.png ) |
| 5 | **PostgreSQL Lifecycle Status Read-Back**<br>Confirm stu.lifecycle_status rows exist for pilot tenant under RLS-forced schema | **PASS** | [`screenshots/05_postgresql_lifecycle_status_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/05_postgresql_lifecycle_status_read_back.png ) |
| 6 | **Schema Ownership / RLS Audit**<br>Verify lifecycle tables have RLS enabled and FORCED | **PASS** | [`screenshots/06_schema_ownership___rls_audit.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/06_schema_ownership___rls_audit.png ) |
| 7 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>Unauthenticated GET on lifecycle status contract returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-EN-001-530922`

---

## 4. Performance & Timings

- **College Admin Authentication:** 4586 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-001/screenshots)
