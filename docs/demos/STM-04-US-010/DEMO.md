# DEMO EVIDENCE REPORT: STM-04-US-010

**Story Title:** Student Voluntary Withdrawal Petition & Institutional Exit Request
**Execution Timestamp:** 2026-11-12T12:13:05.795Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>Student authenticates to CampXSync with authorized student session and institutional tenant context | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Student Lifecycle Desk**<br>Verify student lifecycle workspace loads with verified ACTIVE academic standing badge | **PASS** | [`screenshots/02_navigate_to_student_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/02_navigate_to_student_lifecycle_desk.png ) |
| 3 | **Select Voluntary Withdrawal Request Type**<br>Student selects 'Voluntary Withdrawal (WITHDRAWAL)' from request type dropdown | **PASS** | [`screenshots/03_select_voluntary_withdrawal_request_type.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/03_select_voluntary_withdrawal_request_type.png ) |
| 4 | **Verify Effective Date Field Displayed**<br>Form dynamically exposes mandatory Effective Exit Date input for regulatory archiving | **PASS** | [`screenshots/04_verify_effective_date_field_displayed.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/04_verify_effective_date_field_displayed.png ) |
| 5 | **Set Effective Withdrawal Date**<br>Student enters proposed effective institutional exit date | **PASS** | [`screenshots/05_set_effective_withdrawal_date.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/05_set_effective_withdrawal_date.png ) |
| 6 | **Validation Error Guardrail**<br>Attempting submission with empty rationale triggers immediate validation alert banner | **PASS** | [`screenshots/06_validation_error_guardrail.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/06_validation_error_guardrail.png ) |
| 7 | **Verify Validation Alert Banner**<br>System displays validation warning enforcing minimum 5-character exit justification | **PASS** | [`screenshots/07_verify_validation_alert_banner.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/07_verify_validation_alert_banner.png ) |
| 8 | **Provide Valid Withdrawal Rationale**<br>Student enters detailed formal voluntary withdrawal petition with career and relocation details | **PASS** | [`screenshots/08_provide_valid_withdrawal_rationale.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/08_provide_valid_withdrawal_rationale.png ) |
| 9 | **Submit Withdrawal Request**<br>Click 'Submit Request' button, dispatching POST /api/v1/lifecycle/requests through API Gateway to lifecycle-service | **PASS** | [`screenshots/09_submit_withdrawal_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/09_submit_withdrawal_request.png ) |
| 10 | **Verify Submission Confirmation**<br>System displays immediate success banner confirming withdrawal petition is recorded and awaiting registrar review | **PASS** | [`screenshots/10_verify_submission_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/10_verify_submission_confirmation.png ) |
| 11 | **Switch to My Requests Tab**<br>Navigate to 'My Requests' tab to review submitted lifecycle petitions | **PASS** | [`screenshots/11_switch_to_my_requests_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/11_switch_to_my_requests_tab.png ) |
| 12 | **Verify Withdrawal Request Row and Status Badge**<br>Withdrawal petition is displayed live in requests table with PENDING status badge | **PASS** | [`screenshots/12_verify_withdrawal_request_row_and_status_badge.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/12_verify_withdrawal_request_row_and_status_badge.png ) |
| 13 | **PostgreSQL Database Committed Check**<br>Read-only query verifies stu.lifecycle_requests has committed the request with type WITHDRAWAL and status PENDING | **PASS** | [`screenshots/13_postgresql_database_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/13_postgresql_database_committed_check.png ) |
| 14 | **Negative RBAC Enforcement Check**<br>Unauthenticated API request to gateway lifecycle endpoints must fail closed with HTTP 401 Unauthorized | **PASS** | [`screenshots/14_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots/14_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-010-571259`

---

## 4. Performance & Timings

- **Portal Authentication:** 763 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-010/screenshots)
