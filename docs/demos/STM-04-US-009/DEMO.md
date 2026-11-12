# DEMO EVIDENCE REPORT: STM-04-US-009

**Story Title:** Registrar Review, Approval & Denial of Leave Requests
**Execution Timestamp:** 2026-11-12T12:03:06.758Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>College Administrator / Registrar authenticates to CampXSync with authorized R31 staff session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Lifecycle Review Desk**<br>Verify College Lifecycle desk loads with institutional review authority | **PASS** | [`screenshots/02_navigate_to_lifecycle_review_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/02_navigate_to_lifecycle_review_desk.png ) |
| 3 | **Inspect Pending Leave Requests Queue**<br>Desk displays active queue of pending lifecycle and leave requests submitted by students | **PASS** | [`screenshots/03_inspect_pending_leave_requests_queue.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/03_inspect_pending_leave_requests_queue.png ) |
| 4 | **Verify Leave Request Type and Pending Status**<br>Verify request type badge indicates LEAVE and status badge confirms PENDING state | **PASS** | [`screenshots/04_verify_leave_request_type_and_pending_status.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/04_verify_leave_request_type_and_pending_status.png ) |
| 5 | **Open Rejection Remarks Modal**<br>Registrar initiates rejection workflow, exposing mandatory regulatory justification dialog | **PASS** | [`screenshots/05_open_rejection_remarks_modal.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/05_open_rejection_remarks_modal.png ) |
| 6 | **Verify Rejection Guardrail Validation**<br>Attempting rejection without justification triggers client-side validation guardrail | **PASS** | [`screenshots/06_verify_rejection_guardrail_validation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/06_verify_rejection_guardrail_validation.png ) |
| 7 | **Assert Validation Guardrail Message**<br>System enforces minimum 5-character regulatory rejection justification | **PASS** | [`screenshots/07_assert_validation_guardrail_message.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/07_assert_validation_guardrail_message.png ) |
| 8 | **Dismiss Rejection Modal**<br>Registrar dismisses rejection dialog to proceed with standard approval decision | **PASS** | [`screenshots/08_dismiss_rejection_modal.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/08_dismiss_rejection_modal.png ) |
| 9 | **Approve Student Leave Request**<br>Registrar executes formal approval, dispatching POST /api/v1/lifecycle/requests/{id}/decide through API Gateway | **PASS** | [`screenshots/09_approve_student_leave_request.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/09_approve_student_leave_request.png ) |
| 10 | **Verify Approval Success Confirmation**<br>System displays green success notification confirming request approval and student status transition | **PASS** | [`screenshots/10_verify_approval_success_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/10_verify_approval_success_confirmation.png ) |
| 11 | **PostgreSQL Lifecycle Request Committed Check**<br>Read-only query verifies stu.lifecycle_requests has committed status APPROVED with registrar decision timestamp | **PASS** | [`screenshots/11_postgresql_lifecycle_request_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/11_postgresql_lifecycle_request_committed_check.png ) |
| 12 | **PostgreSQL Student Status Transition Committed Check**<br>Read-only query verifies stu.lifecycle_status transitioned student standing from ACTIVE to ON_LEAVE | **PASS** | [`screenshots/12_postgresql_student_status_transition_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/12_postgresql_student_status_transition_committed_check.png ) |
| 13 | **Negative RBAC Enforcement Check**<br>API Gateway and Lifecycle Service reject unauthorized decision attempts by non-registrar students with HTTP 403 | **PASS** | [`screenshots/13_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots/13_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-009-971451`

---

## 4. Performance & Timings

- **Portal Authentication:** 1237 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-009/screenshots)
