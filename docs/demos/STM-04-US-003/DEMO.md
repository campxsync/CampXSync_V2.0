# DEMO EVIDENCE REPORT: STM-04-US-003

**Story Title:** Head of Department (HOD) Endorsement & Departmental Review of Student Leave Petitions
**Execution Timestamp:** 2026-11-12T14:11:41.930Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Faculty Advisor Portal Authentication**<br>Faculty Advisor / HOD (Dr. Rajesh Sharma) authenticates to CampXSync with authorized R32/R10 staff session | **PASS** | [`screenshots/01_faculty_advisor_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/01_faculty_advisor_portal_authentication.png ) |
| 2 | **Navigate to Faculty Advisory & Advisees Desk**<br>Verify Faculty Advisory workspace loads with assigned departmental advisee monitoring authority | **PASS** | [`screenshots/02_navigate_to_faculty_advisory___advisees_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/02_navigate_to_faculty_advisory___advisees_desk.png ) |
| 3 | **Inspect Assigned Student Advisee Roster**<br>System displays active advisees assigned to this faculty member by institutional registry | **PASS** | [`screenshots/03_inspect_assigned_student_advisee_roster.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/03_inspect_assigned_student_advisee_roster.png ) |
| 4 | **Inspect Pending Advisee Petitions Queue**<br>System renders pending leave and transfer petitions submitted by advisees awaiting departmental clearance | **PASS** | [`screenshots/04_inspect_pending_advisee_petitions_queue.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/04_inspect_pending_advisee_petitions_queue.png ) |
| 5 | **Locate Advisee Medical Leave Petition**<br>Faculty advisor identifies pending orthopedic recovery leave petition submitted by advisee | **PASS** | [`screenshots/05_locate_advisee_medical_leave_petition.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/05_locate_advisee_medical_leave_petition.png ) |
| 6 | **Trigger Departmental Endorsement Action**<br>Faculty advisor opens departmental review and recommendation modal for the petition | **PASS** | [`screenshots/06_trigger_departmental_endorsement_action.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/06_trigger_departmental_endorsement_action.png ) |
| 7 | **Verify Endorsement Modal Displayed**<br>Departmental Endorsement modal opens with recommendation options and mandatory remarks input | **PASS** | [`screenshots/07_verify_endorsement_modal_displayed.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/07_verify_endorsement_modal_displayed.png ) |
| 8 | **Select Recommendation Level**<br>Faculty selects RECOMMENDED endorsement based on fulfilled departmental criteria | **PASS** | [`screenshots/08_select_recommendation_level.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/08_select_recommendation_level.png ) |
| 9 | **Enter Departmental Review Remarks**<br>Faculty advisor inputs detailed justification (> 5 chars) confirming academic progress clearance | **PASS** | [`screenshots/09_enter_departmental_review_remarks.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/09_enter_departmental_review_remarks.png ) |
| 10 | **Submit Departmental Endorsement**<br>Faculty submits formal endorsement to Java Lifecycle Service via API Gateway, appending immutable history entry | **PASS** | [`screenshots/10_submit_departmental_endorsement.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/10_submit_departmental_endorsement.png ) |
| 11 | **Verify Endorsement Confirmation**<br>System displays success banner confirming petition was endorsed and forwarded to Registrar Lifecycle Review Desk | **PASS** | [`screenshots/11_verify_endorsement_confirmation.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/11_verify_endorsement_confirmation.png ) |
| 12 | **Verify Departmental Endorsement Badge on Faculty Desk**<br>Pending advisee petitions table renders updated endorsement status badge | **PASS** | [`screenshots/12_verify_departmental_endorsement_badge_on_faculty_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/12_verify_departmental_endorsement_badge_on_faculty_desk.png ) |
| 13 | **Registrar Desk Verification — College Admin Sign-In**<br>College Administrator / Registrar authenticates to review endorsed petition on institutional desk | **PASS** | [`screenshots/13_registrar_desk_verification___college_admin_sign_in.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/13_registrar_desk_verification___college_admin_sign_in.png ) |
| 14 | **Verify HOD Endorsement Surfaced to Registrar**<br>Registrar Pending Requests queue renders HOD Endorsement badge and remarks on the petition row | **PASS** | [`screenshots/14_verify_hod_endorsement_surfaced_to_registrar.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/14_verify_hod_endorsement_surfaced_to_registrar.png ) |
| 15 | **PostgreSQL Immutable Endorsement History Check**<br>Read-only query verifies stu.lifecycle_history contains committed hod.endorsement row in Supabase PostgreSQL | **PASS** | [`screenshots/15_postgresql_immutable_endorsement_history_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/15_postgresql_immutable_endorsement_history_check.png ) |
| 16 | **Negative RBAC Check — Gateway Direct Unauthenticated Access Blocked**<br>Gateway strictly blocks unauthenticated requests to /api/v1/lifecycle/advisor/requests with HTTP 401 Unauthorized | **PASS** | [`screenshots/16_negative_rbac_check___gateway_direct_unauthenticated_access_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots/16_negative_rbac_check___gateway_direct_unauthenticated_access_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-US-003-669135`

---

## 4. Performance & Timings

- **Faculty Advisor Portal Authentication:** 2808 ms (Target <= 1500 ms: **WARN**)
- **Registrar Desk Verification — College Admin Sign-In:** 2451 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/screenshots)
