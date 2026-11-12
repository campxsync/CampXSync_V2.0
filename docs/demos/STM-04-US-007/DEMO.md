# DEMO EVIDENCE REPORT: STM-04-US-007

**Story Title:** Program Transfer Request & Academic Standing
**Execution Timestamp:** 2026-11-12T10:32:52.543Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>Student authenticates to CampXSync with authorized student session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Student Lifecycle Desk**<br>Verify student lifecycle workspace loads with verified ACTIVE academic standing badge | **PASS** | [`screenshots/02_navigate_to_student_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/02_navigate_to_student_lifecycle_desk.png ) |
| 3 | **Select Program Transfer Request**<br>Student selects 'Program Transfer (TRANSFER)' from request type dropdown | **PASS** | [`screenshots/03_select_program_transfer_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/03_select_program_transfer_request.png ) |
| 4 | **Provide Transfer Rationale**<br>Student inputs formal justification and program transfer details into rationale field | **PASS** | [`screenshots/04_provide_transfer_rationale.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/04_provide_transfer_rationale.png ) |
| 5 | **Submit Transfer Request**<br>Click 'Submit Request' button, dispatching POST /api/v1/lifecycle/requests through API Gateway to lifecycle-service | **PASS** | [`screenshots/05_submit_transfer_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/05_submit_transfer_request.png ) |
| 6 | **Verify Submission Confirmation**<br>System displays immediate success banner confirming request is recorded and awaiting registrar review | **PASS** | [`screenshots/06_verify_submission_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/06_verify_submission_confirmation.png ) |
| 7 | **Switch to My Requests Tab**<br>Navigate to 'My Requests' tab to review submitted lifecycle requests | **PASS** | [`screenshots/07_switch_to_my_requests_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/07_switch_to_my_requests_tab.png ) |
| 8 | **Verify Request Row and Status Badge**<br>Transferred request is displayed live in requests table with PENDING status badge | **PASS** | [`screenshots/08_verify_request_row_and_status_badge.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/08_verify_request_row_and_status_badge.png ) |
| 9 | **PostgreSQL Database committed check**<br>Read-only query verifies stu.lifecycle_requests has committed the request with type TRANSFER and status PENDING | **PASS** | [`screenshots/09_postgresql_database_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/09_postgresql_database_committed_check.png ) |
| 10 | **Negative RBAC Enforcement Check**<br>API Gateway and Lifecycle Service reject unauthorized cross-student request submissions with HTTP 403 | **PASS** | [`screenshots/10_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots/10_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-007-559910`

---

## 4. Performance & Timings

- **Portal Authentication:** 2390 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-007/screenshots)
