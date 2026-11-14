# DEMO EVIDENCE REPORT: STM-10-EN-007

**Story Title:** Portal performance, operational readiness and authorized Intelligence Services dashboard analytics
**Execution Timestamp:** 2026-11-14T12:20:37.510Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Portal Authentication**<br>Student opens portal for operational readiness and analytics surface | **PASS** | [`screenshots/01_student_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/01_student_portal_authentication.png ) |
| 2 | **Dashboard Operational**<br>Student dashboard root confirms portal operational readiness | **PASS** | [`screenshots/02_dashboard_operational.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/02_dashboard_operational.png ) |
| 3 | **KPI Strip Visible**<br>Dashboard KPI strip provides authorized portal analytics widgets | **PASS** | [`screenshots/03_kpi_strip_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/03_kpi_strip_visible.png ) |
| 4 | **Portal Summaries Visible**<br>Document and notification summary widgets remain available | **PASS** | [`screenshots/04_portal_summaries_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/04_portal_summaries_visible.png ) |
| 5 | **Notifications Empty State**<br>In-service notifications empty state is explicit (no invented feed) | **PASS** | [`screenshots/05_notifications_empty_state.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/05_notifications_empty_state.png ) |
| 6 | **PostgreSQL Outbox Schema Present**<br>Confirm sys.outbox_events table exists for intelligence adapter emissions (liveEndpoint may remain false) | **PASS** | [`screenshots/06_postgresql_outbox_schema_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/06_postgresql_outbox_schema_present.png ) |
| 7 | **Gateway Students Contract Auth Gate**<br>Unauthenticated students/me remains blocked proving gateway ops posture | **PASS** | [`screenshots/07_gateway_students_contract_auth_gate.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots/07_gateway_students_contract_auth_gate.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-10-EN-007-829395`

---

## 4. Performance & Timings

- **Student Portal Authentication:** 1591 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-007/screenshots)
