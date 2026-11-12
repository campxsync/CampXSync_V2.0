# DEMO EVIDENCE REPORT: STM-04-US-011

**Story Title:** Student Reinstatement Request & Return to Active Academic Standing
**Execution Timestamp:** 2026-11-12T12:23:03.985Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>Student on approved leave authenticates to CampXSync with authorized student session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Student Lifecycle Desk**<br>Verify student lifecycle workspace loads with verified ON_LEAVE academic standing badge | **PASS** | [`screenshots/02_navigate_to_student_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/02_navigate_to_student_lifecycle_desk.png ) |
| 3 | **Select Reinstatement Request Type**<br>Student selects 'Reinstatement (REINSTATE)' from request type dropdown to resume active studies | **PASS** | [`screenshots/03_select_reinstatement_request_type.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/03_select_reinstatement_request_type.png ) |
| 4 | **Validation Error Guardrail**<br>Attempting submission with empty rationale triggers immediate validation alert banner | **PASS** | [`screenshots/04_validation_error_guardrail.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/04_validation_error_guardrail.png ) |
| 5 | **Verify Validation Alert Banner**<br>System displays validation warning enforcing minimum 5-character clearance justification | **PASS** | [`screenshots/05_verify_validation_alert_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/05_verify_validation_alert_banner.png ) |
| 6 | **Provide Valid Medical Clearance Rationale**<br>Student enters detailed medical clearance rationale and return-to-campus readiness petition | **PASS** | [`screenshots/06_provide_valid_medical_clearance_rationale.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/06_provide_valid_medical_clearance_rationale.png ) |
| 7 | **Submit Reinstatement Request**<br>Click 'Submit Request' button, dispatching POST /api/v1/lifecycle/requests through API Gateway to lifecycle-service | **PASS** | [`screenshots/07_submit_reinstatement_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/07_submit_reinstatement_request.png ) |
| 8 | **Verify Submission Confirmation**<br>System displays immediate success banner confirming reinstatement petition is recorded and awaiting registrar review | **PASS** | [`screenshots/08_verify_submission_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/08_verify_submission_confirmation.png ) |
| 9 | **Switch to My Requests Tab**<br>Navigate to 'My Requests' tab to review submitted lifecycle petitions | **PASS** | [`screenshots/09_switch_to_my_requests_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/09_switch_to_my_requests_tab.png ) |
| 10 | **Verify Reinstatement Request Row and Status Badge**<br>Reinstatement petition is displayed live in requests table with PENDING status badge | **PASS** | [`screenshots/10_verify_reinstatement_request_row_and_status_badge.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/10_verify_reinstatement_request_row_and_status_badge.png ) |
| 11 | **PostgreSQL Database Committed Check**<br>Read-only query verifies stu.lifecycle_requests has committed the request with type REINSTATE and status PENDING | **PASS** | [`screenshots/11_postgresql_database_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/11_postgresql_database_committed_check.png ) |
| 12 | **Negative RBAC Enforcement Check**<br>Unauthenticated API request to gateway lifecycle endpoints must fail closed with HTTP 401 Unauthorized | **PASS** | [`screenshots/12_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots/12_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-011-170891`

---

## 4. Performance & Timings

- **Portal Authentication:** 696 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-011/screenshots)
