# DEMO EVIDENCE REPORT: STM-03-EN-005

**Story Title:** Frontend integration, enrolment UI and browser E2E testing
**Execution Timestamp:** 2026-11-14T11:17:26.590Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Course Registration Desk (frontend → BFF → gateway → STM-03) | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/01_college_admin_authentication.png ) |
| 2 | **College Enrolment Desk Visible**<br>College desk heading and term section render with data-testid contracts | **PASS** | [`screenshots/02_college_enrolment_desk_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/02_college_enrolment_desk_visible.png ) |
| 3 | **College Catalogue Section Visible**<br>Catalogue/capacity UI is present for registrar workflow | **PASS** | [`screenshots/03_college_catalogue_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/03_college_catalogue_section_visible.png ) |
| 4 | **Select Academic Term**<br>Registrar selects live term to exercise frontend→gateway integration | **PASS** | [`screenshots/04_select_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/04_select_academic_term.png ) |
| 5 | **College Success Feedback**<br>UI success banner confirms BFF round-trip | **PASS** | [`screenshots/05_college_success_feedback.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/05_college_success_feedback.png ) |
| 6 | **Student Authentication**<br>Student opens Course Registration self-service desk | **PASS** | [`screenshots/06_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/06_student_authentication.png ) |
| 7 | **Student Enrolment Desk Visible**<br>Student desk heading and list contracts render | **PASS** | [`screenshots/07_student_enrolment_desk_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/07_student_enrolment_desk_visible.png ) |
| 8 | **Student Enrolment List Visible**<br>Owned enrolments list is present for browser E2E coverage | **PASS** | [`screenshots/08_student_enrolment_list_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/08_student_enrolment_list_visible.png ) |
| 9 | **Select Owned Enrolment**<br>Student selects owned enrolment to open details panel | **PASS** | [`screenshots/09_select_owned_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/09_select_owned_enrolment.png ) |
| 10 | **Student Details Panel Visible**<br>Details panel proves frontend integration for view-enrolment path | **PASS** | [`screenshots/10_student_details_panel_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/10_student_details_panel_visible.png ) |
| 11 | **PostgreSQL Enrolment Read-Back**<br>DB read-back confirms UI is backed by real enrolment persistence | **PASS** | [`screenshots/11_postgresql_enrolment_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/11_postgresql_enrolment_read_back.png ) |
| 12 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>Unauthenticated GET on enrolment contract returns HTTP 401 | **PASS** | [`screenshots/12_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots/12_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-005-029984`

---

## 4. Performance & Timings

- **College Admin Authentication:** 957 ms (Target <= 1500 ms: **PASS**)
- **Student Authentication:** 1360 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-005/screenshots)
