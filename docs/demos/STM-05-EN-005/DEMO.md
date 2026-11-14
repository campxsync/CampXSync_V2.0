# DEMO EVIDENCE REPORT: STM-05-EN-005

**Story Title:** Student/guardian frontend and accessible consent management
**Execution Timestamp:** 2026-11-14T12:02:18.250Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student opens Guardians & Consent self-service desk | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/01_student_authentication.png ) |
| 2 | **Student Guardians Desk Loaded**<br>Student guardians heading proves self-service consent UI path | **PASS** | [`screenshots/02_student_guardians_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/02_student_guardians_desk_loaded.png ) |
| 3 | **Guardian List Section Visible**<br>Linked guardians list/empty state section is present for accessible consent management | **PASS** | [`screenshots/03_guardian_list_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/03_guardian_list_section_visible.png ) |
| 4 | **Consent Controls Visible**<br>Consent period and revoke controls are available to the authenticated student | **PASS** | [`screenshots/04_consent_controls_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/04_consent_controls_visible.png ) |
| 5 | **Revoke Control Visible**<br>Revoke access button exposes student-owned consent revocation path | **PASS** | [`screenshots/05_revoke_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/05_revoke_control_visible.png ) |
| 6 | **College Admin Authentication**<br>College Administrator opens institutional Guardians desk | **PASS** | [`screenshots/06_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/06_college_admin_authentication.png ) |
| 7 | **College Guardians Desk Visible**<br>College guardians heading completes dual-persona frontend coverage | **PASS** | [`screenshots/07_college_guardians_desk_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/07_college_guardians_desk_visible.png ) |
| 8 | **College Consent Grant Control Visible**<br>Staff grant-access control remains available alongside student self-service | **PASS** | [`screenshots/08_college_consent_grant_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/08_college_consent_grant_control_visible.png ) |
| 9 | **PostgreSQL Guardianship Read-Back**<br>Confirm guardianship rows exist for pilot tenant | **PASS** | [`screenshots/09_postgresql_guardianship_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/09_postgresql_guardianship_read_back.png ) |
| 10 | **Negative RBAC — Unauthenticated Student Guardians Blocked**<br>Unauthenticated GET on guardians student contract returns HTTP 401 | **PASS** | [`screenshots/10_negative_rbac___unauthenticated_student_guardians_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots/10_negative_rbac___unauthenticated_student_guardians_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-05-EN-005-722528`

---

## 4. Performance & Timings

- **Student Authentication:** 5689 ms (Target <= 1500 ms: **WARN**)
- **College Admin Authentication:** 1539 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-005/screenshots)
