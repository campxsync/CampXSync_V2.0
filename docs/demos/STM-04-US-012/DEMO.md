# DEMO EVIDENCE REPORT: STM-04-US-012

**Story Title:** Registrar Review and Graduation Eligibility Certification & Degree Clearance
**Execution Timestamp:** 2026-11-12T12:30:31.607Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>College Administrator / Registrar authenticates to CampXSync with authorized R31 staff session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Lifecycle Review Desk**<br>Verify College Lifecycle workspace loads with institutional review authority | **PASS** | [`screenshots/02_navigate_to_lifecycle_review_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/02_navigate_to_lifecycle_review_desk.png ) |
| 3 | **Input Student Record ID**<br>Registrar inputs candidate student record identifier for degree audit | **PASS** | [`screenshots/03_input_student_record_id.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/03_input_student_record_id.png ) |
| 4 | **Input Programme ID**<br>Registrar inputs candidate degree programme identifier (B.Tech CSE) | **PASS** | [`screenshots/04_input_programme_id.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/04_input_programme_id.png ) |
| 5 | **Input Official Graduation Date**<br>Registrar sets formal convocation and degree conferral date | **PASS** | [`screenshots/05_input_official_graduation_date.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/05_input_official_graduation_date.png ) |
| 6 | **Execute Eligibility Pre-Validation**<br>Dispatch POST /api/v1/lifecycle/graduation/validate through Gateway to check standing and pending petitions | **PASS** | [`screenshots/06_execute_eligibility_pre_validation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/06_execute_eligibility_pre_validation.png ) |
| 7 | **Assert Eligibility Success Confirmation**<br>System displays green confirmation confirming student meets all graduation standing criteria | **PASS** | [`screenshots/07_assert_eligibility_success_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/07_assert_eligibility_success_confirmation.png ) |
| 8 | **Record Irreversible Graduation Certification**<br>Registrar executes formal degree clearance, dispatching POST /api/v1/lifecycle/graduation/record | **PASS** | [`screenshots/08_record_irreversible_graduation_certification.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/08_record_irreversible_graduation_certification.png ) |
| 9 | **Verify Graduation Certification Notification**<br>System displays green success notification confirming degree conferral and status transition to GRADUATED | **PASS** | [`screenshots/09_verify_graduation_certification_notification.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/09_verify_graduation_certification_notification.png ) |
| 10 | **PostgreSQL Graduation Records Committed Check**<br>Read-only query verifies stu.graduation_records contains committed degree certification record | **PASS** | [`screenshots/10_postgresql_graduation_records_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/10_postgresql_graduation_records_committed_check.png ) |
| 11 | **PostgreSQL Student Status Transition Committed Check**<br>Read-only query verifies stu.lifecycle_status transitioned student standing from ACTIVE to GRADUATED | **PASS** | [`screenshots/11_postgresql_student_status_transition_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/11_postgresql_student_status_transition_committed_check.png ) |
| 12 | **PostgreSQL Lifecycle Audit History Committed Check**<br>Read-only query verifies stu.lifecycle_history appended graduation transition entry | **PASS** | [`screenshots/12_postgresql_lifecycle_audit_history_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/12_postgresql_lifecycle_audit_history_committed_check.png ) |
| 13 | **Negative RBAC Enforcement Check**<br>Unauthenticated POST request to gateway graduation endpoints must fail closed with HTTP 401/403 | **PASS** | [`screenshots/13_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots/13_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-012-615745`

---

## 4. Performance & Timings

- **Portal Authentication:** 1127 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-012/screenshots)
