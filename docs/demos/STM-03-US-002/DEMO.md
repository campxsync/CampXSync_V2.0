# DEMO EVIDENCE REPORT: STM-03-US-002

**Story Title:** View enrolment details
**Execution Timestamp:** 2026-11-14T10:08:55.202Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student authenticates via development test session and opens Course registration | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/01_student_authentication.png ) |
| 2 | **Verify Student Enrolment Desk**<br>Student enrolment desk loads with heading and enrolment list | **PASS** | [`screenshots/02_verify_student_enrolment_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/02_verify_student_enrolment_desk.png ) |
| 3 | **Enrolment List Visible**<br>Student sees at least one owned enrolment in the list | **PASS** | [`screenshots/03_enrolment_list_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/03_enrolment_list_visible.png ) |
| 4 | **Select Owned Enrolment**<br>Student selects their ENROLLED enrolment to view programme and term details | **PASS** | [`screenshots/04_select_owned_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/04_select_owned_enrolment.png ) |
| 5 | **Verify Enrolment Details Panel**<br>Details panel shows status, programme, academic year and term for the selected enrolment | **PASS** | [`screenshots/05_verify_enrolment_details_panel.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/05_verify_enrolment_details_panel.png ) |
| 6 | **Verify Programme Field**<br>Programme id is visible so the student can verify their programme | **PASS** | [`screenshots/06_verify_programme_field.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/06_verify_programme_field.png ) |
| 7 | **Verify Academic Year Field**<br>Academic year code is visible on the enrolment detail view | **PASS** | [`screenshots/07_verify_academic_year_field.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/07_verify_academic_year_field.png ) |
| 8 | **Verify Term Field**<br>Term name/code is visible on the enrolment detail view | **PASS** | [`screenshots/08_verify_term_field.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/08_verify_term_field.png ) |
| 9 | **PostgreSQL Enrolment Ownership Read-Back**<br>Read-only query verifies the viewed enrolment belongs to student1 under the pilot tenant | **PASS** | [`screenshots/09_postgresql_enrolment_ownership_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/09_postgresql_enrolment_ownership_read_back.png ) |
| 10 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated GET to enrolment detail with HTTP 401 | **PASS** | [`screenshots/10_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots/10_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-US-002-918192`

---

## 4. Performance & Timings

- **Student Authentication:** 5543 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-US-002/screenshots)
