# DEMO EVIDENCE REPORT: STM-03-US-005

**Story Title:** View enrolment history
**Execution Timestamp:** 2026-11-14T10:34:03.344Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student authenticates and opens Course registration | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/01_student_authentication.png ) |
| 2 | **Verify Student Enrolment Desk**<br>Student enrolment desk loads | **PASS** | [`screenshots/02_verify_student_enrolment_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/02_verify_student_enrolment_desk.png ) |
| 3 | **Enrolment List Visible**<br>Student sees owned enrolments | **PASS** | [`screenshots/03_enrolment_list_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/03_enrolment_list_visible.png ) |
| 4 | **Select Owned Enrolment**<br>Student selects enrolment that has status history | **PASS** | [`screenshots/04_select_owned_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/04_select_owned_enrolment.png ) |
| 5 | **Enrolment History Panel Visible**<br>History section is shown for the selected enrolment | **PASS** | [`screenshots/05_enrolment_history_panel_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/05_enrolment_history_panel_visible.png ) |
| 6 | **History Row Visible**<br>At least one history row with status/action is visible | **PASS** | [`screenshots/06_history_row_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/06_history_row_visible.png ) |
| 7 | **History Status Field Visible**<br>History status value is rendered for the first row | **PASS** | [`screenshots/07_history_status_field_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/07_history_status_field_visible.png ) |
| 8 | **PostgreSQL History Read-Back**<br>Read-only query verifies append-only history exists for the viewed enrolment | **PASS** | [`screenshots/08_postgresql_history_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/08_postgresql_history_read_back.png ) |
| 9 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated GET to enrolment history with HTTP 401 | **PASS** | [`screenshots/09_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots/09_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-US-005-427998`

---

## 4. Performance & Timings

- **Student Authentication:** 3495 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-005/screenshots)
