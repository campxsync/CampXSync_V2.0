# DEMO EVIDENCE REPORT: STM-03-US-004

**Story Title:** Update enrolment status
**Execution Timestamp:** 2026-11-14T10:29:15.296Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>Registrar authenticates and opens the Course registration desk | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded**<br>College enrolment desk renders | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/02_verify_enrolment_desk_loaded.png ) |
| 3 | **Select Academic Term**<br>Registrar selects the 2026 ODD term | **PASS** | [`screenshots/03_select_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/03_select_academic_term.png ) |
| 4 | **Enter Programme Identifier**<br>Staff supplies the programme id | **PASS** | [`screenshots/04_enter_programme_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/04_enter_programme_identifier.png ) |
| 5 | **Enter Application Identifier**<br>Dedicated application id for status-update story | **PASS** | [`screenshots/05_enter_application_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/05_enter_application_identifier.png ) |
| 6 | **Enter Student Identifier**<br>Dedicated student id to avoid term duplicate collisions | **PASS** | [`screenshots/06_enter_student_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/06_enter_student_identifier.png ) |
| 7 | **Open Enrolment From Handover**<br>Open a PENDING enrolment that will receive a status update | **PASS** | [`screenshots/07_open_enrolment_from_handover.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/07_open_enrolment_from_handover.png ) |
| 8 | **Verify Handover Success Banner**<br>UI confirms the PENDING enrolment was persisted | **PASS** | [`screenshots/08_verify_handover_success_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/08_verify_handover_success_banner.png ) |
| 9 | **Enrolment Row Visible**<br>Demo student enrolment appears in the registrar list | **PASS** | [`screenshots/09_enrolment_row_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/09_enrolment_row_visible.png ) |
| 10 | **Suspend Enrolment**<br>Registrar updates enrolment status to SUSPENDED for a traceable change | **PASS** | [`screenshots/10_suspend_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/10_suspend_enrolment.png ) |
| 11 | **Status Update Success**<br>UI confirms status was updated | **PASS** | [`screenshots/11_status_update_success.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/11_status_update_success.png ) |
| 12 | **PostgreSQL Status Read-Back**<br>Read-only query verifies enrolment is SUSPENDED under the pilot tenant | **PASS** | [`screenshots/12_postgresql_status_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/12_postgresql_status_read_back.png ) |
| 13 | **PostgreSQL History Trace Read-Back**<br>Read-only query verifies append-only status history row with action=status and SUSPENDED | **PASS** | [`screenshots/13_postgresql_history_trace_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/13_postgresql_history_trace_read_back.png ) |
| 14 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated POST to enrolment status with HTTP 401 | **PASS** | [`screenshots/14_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots/14_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-US-004-136250`

---

## 4. Performance & Timings

- **College Admin Authentication:** 2695 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-004/screenshots)
