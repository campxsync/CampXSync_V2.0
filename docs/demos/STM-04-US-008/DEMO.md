# DEMO EVIDENCE REPORT: STM-04-US-008

**Story Title:** Student Leave of Absence Request & Date Span Enforcement
**Execution Timestamp:** 2026-11-12T11:18:02.196Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>Student authenticates to CampXSync with authorized student session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Student Lifecycle Desk**<br>Verify student lifecycle workspace loads with verified ACTIVE academic standing badge | **PASS** | [`screenshots/02_navigate_to_student_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/02_navigate_to_student_lifecycle_desk.png ) |
| 3 | **Select Leave of Absence Request Type**<br>Student selects 'Leave of Absence (LEAVE)' from request type dropdown | **PASS** | [`screenshots/03_select_leave_of_absence_request_type.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/03_select_leave_of_absence_request_type.png ) |
| 4 | **Verify Date Span Fields Displayed**<br>Form dynamically reveals mandatory Effective Start Date and optional Expected Return Date fields | **PASS** | [`screenshots/04_verify_date_span_fields_displayed.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/04_verify_date_span_fields_displayed.png ) |
| 5 | **Validation Error Guardrail**<br>Attempting submission with empty rationale triggers immediate validation alert banner | **PASS** | [`screenshots/05_validation_error_guardrail.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/05_validation_error_guardrail.png ) |
| 6 | **Verify Validation Alert Banner**<br>System displays validation warning enforcing minimum 5-character rationale | **PASS** | [`screenshots/06_verify_validation_alert_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/06_verify_validation_alert_banner.png ) |
| 7 | **Provide Valid Leave Dates and Rationale**<br>Student enters valid effective date span and detailed medical rationale | **PASS** | [`screenshots/07_provide_valid_leave_dates_and_rationale.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/07_provide_valid_leave_dates_and_rationale.png ) |
| 8 | **Submit Leave Request**<br>Click 'Submit Request' button, dispatching POST /api/v1/lifecycle/requests through API Gateway to lifecycle-service | **PASS** | [`screenshots/08_submit_leave_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/08_submit_leave_request.png ) |
| 9 | **Verify Submission Confirmation**<br>System displays immediate success banner confirming leave request is recorded and awaiting registrar review | **PASS** | [`screenshots/09_verify_submission_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/09_verify_submission_confirmation.png ) |
| 10 | **Switch to My Requests Tab**<br>Navigate to 'My Requests' tab to review submitted lifecycle requests | **PASS** | [`screenshots/10_switch_to_my_requests_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/10_switch_to_my_requests_tab.png ) |
| 11 | **Verify Leave Request Row and Status Badge**<br>Leave request is displayed live in requests table with PENDING status badge | **PASS** | [`screenshots/11_verify_leave_request_row_and_status_badge.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/11_verify_leave_request_row_and_status_badge.png ) |
| 12 | **PostgreSQL Database Committed Check**<br>Read-only query verifies stu.lifecycle_requests has committed the request with type LEAVE and status PENDING | **PASS** | [`screenshots/12_postgresql_database_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/12_postgresql_database_committed_check.png ) |
| 13 | **Negative RBAC Enforcement Check**<br>API Gateway and Lifecycle Service reject unauthorized cross-student leave requests with HTTP 403 | **PASS** | [`screenshots/13_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots/13_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-008-267637`

---

## 4. Performance & Timings

- **Portal Authentication:** 1705 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-008/screenshots)
