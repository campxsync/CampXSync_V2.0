# DEMO EVIDENCE REPORT: STM-03-US-003

**Story Title:** Validate enrolment eligibility
**Execution Timestamp:** 2026-11-14T10:24:56.978Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>Academic administrator authenticates and opens the Course registration desk | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded**<br>College enrolment desk renders with term and handover sections | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/02_verify_enrolment_desk_loaded.png ) |
| 3 | **Select Academic Term**<br>Registrar selects the 2026 ODD term so the registration window context is bound | **PASS** | [`screenshots/03_select_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/03_select_academic_term.png ) |
| 4 | **Enter Programme Identifier**<br>Staff supplies the programme id for the eligibility validation enrolment | **PASS** | [`screenshots/04_enter_programme_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/04_enter_programme_identifier.png ) |
| 5 | **Enter Application Identifier**<br>Staff supplies a dedicated application id for this eligibility story | **PASS** | [`screenshots/05_enter_application_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/05_enter_application_identifier.png ) |
| 6 | **Enter Student Identifier**<br>Staff supplies a dedicated student id to avoid term duplicate collisions | **PASS** | [`screenshots/06_enter_student_identifier.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/06_enter_student_identifier.png ) |
| 7 | **Open Enrolment From Handover**<br>Open a PENDING enrolment that will be validated for eligibility | **PASS** | [`screenshots/07_open_enrolment_from_handover.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/07_open_enrolment_from_handover.png ) |
| 8 | **Verify Handover Success Banner**<br>UI confirms the PENDING enrolment was persisted before eligibility validation | **PASS** | [`screenshots/08_verify_handover_success_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/08_verify_handover_success_banner.png ) |
| 9 | **Enrolment Appears In List**<br>PENDING enrolment for the demo student is listed on the registrar desk | **PASS** | [`screenshots/09_enrolment_appears_in_list.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/09_enrolment_appears_in_list.png ) |
| 10 | **Validate Enrolment Eligibility**<br>Academic administrator runs eligibility validation on the new PENDING enrolment | **PASS** | [`screenshots/10_validate_enrolment_eligibility.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/10_validate_enrolment_eligibility.png ) |
| 11 | **Eligibility Result Visible**<br>UI shows eligibility outcome panel after validation | **PASS** | [`screenshots/11_eligibility_result_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/11_eligibility_result_visible.png ) |
| 12 | **Confirm Eligible Outcome**<br>Result reports Eligible when the registration window is open | **PASS** | [`screenshots/12_confirm_eligible_outcome.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/12_confirm_eligible_outcome.png ) |
| 13 | **PostgreSQL Eligibility Status Read-Back**<br>Read-only query verifies the enrolment advanced to ELIGIBLE under the pilot tenant | **PASS** | [`screenshots/13_postgresql_eligibility_status_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/13_postgresql_eligibility_status_read_back.png ) |
| 14 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated POST to enrolment eligibility with HTTP 401 | **PASS** | [`screenshots/14_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots/14_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-US-003-879930`

---

## 4. Performance & Timings

- **College Admin Authentication:** 1594 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-003/screenshots)
