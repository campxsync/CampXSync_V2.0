# DEMO EVIDENCE REPORT: STM-03-US-001

**Story Title:** Convert approved admission into enrolment
**Execution Timestamp:** 2026-11-14T09:51:18.079Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator authenticates via development test session and lands on the Course Registration Desk | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded**<br>College enrolment desk renders with term, catalogue, and handover sections | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/02_verify_enrolment_desk_loaded.png ) |
| 3 | **Select Existing Academic Term**<br>Registrar selects the existing 2026 ODD academic term for the admission handover | **PASS** | [`screenshots/03_select_existing_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/03_select_existing_academic_term.png ) |
| 4 | **Confirm Term Selected**<br>UI confirms the academic term id is bound for handover | **PASS** | [`screenshots/04_confirm_term_selected.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/04_confirm_term_selected.png ) |
| 5 | **Enter Programme Identifier**<br>Staff supplies the programme id for the approved admission conversion | **PASS** | [`screenshots/05_enter_programme_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/05_enter_programme_identifier.png ) |
| 6 | **Enter Application Identifier**<br>Staff supplies the approved admission application id that will become the enrolment source | **PASS** | [`screenshots/06_enter_application_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/06_enter_application_identifier.png ) |
| 7 | **Enter Student Identifier**<br>Staff supplies a dedicated demo student id for this admission conversion (avoids term duplicate collisions) | **PASS** | [`screenshots/07_enter_student_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/07_enter_student_identifier.png ) |
| 8 | **Open Enrolment From Handover**<br>POST /api/v1/enrollment/handovers via BFF converts the approved admission into a PENDING enrolment | **PASS** | [`screenshots/08_open_enrolment_from_handover.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/08_open_enrolment_from_handover.png ) |
| 9 | **Verify Handover Success Banner**<br>UI shows handover saved confirmation after the Java enrolment service persists the PENDING record | **PASS** | [`screenshots/09_verify_handover_success_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/09_verify_handover_success_banner.png ) |
| 10 | **PostgreSQL Enrolment Read-Back**<br>Read-only query verifies stu.enrolments contains PENDING row for the demo application id under the pilot tenant | **PASS** | [`screenshots/10_postgresql_enrolment_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/10_postgresql_enrolment_read_back.png ) |
| 11 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated POST to /api/v1/enrollment/handovers with HTTP 401 | **PASS** | [`screenshots/11_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots/11_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-US-001-860048`

---

## 4. Performance & Timings

- **College Admin Authentication:** 4101 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-001/screenshots)
