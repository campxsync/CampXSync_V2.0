# DEMO EVIDENCE REPORT: H-1

**Story Title:** ACD-03/ACD-06 Real Postgres Persistence (mark, restart, multi-persona read)
**Execution Timestamp:** 2026-11-13T13:47:20.218Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Faculty Auth + Attendance Desk**<br>Faculty opens attendance capture with seeded timetable slot and section (Postgres-backed ACD-06) | **PASS** | [`screenshots/01_faculty_auth___attendance_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/01_faculty_auth___attendance_desk.png ) |
| 2 | **Wait Faculty Desk**<br>Faculty attendance page heading is visible | **PASS** | [`screenshots/02_wait_faculty_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/02_wait_faculty_desk.png ) |
| 3 | **Open Attendance Session**<br>Faculty opens a relational attendance session persisted to acd.attendance_sessions | **PASS** | [`screenshots/03_open_attendance_session.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/03_open_attendance_session.png ) |
| 4 | **Roster Loaded**<br>Attendance roster grid appears after session open | **PASS** | [`screenshots/04_roster_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/04_roster_loaded.png ) |
| 5 | **Mark All Present**<br>Faculty marks the entire roster PRESENT | **PASS** | [`screenshots/05_mark_all_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/05_mark_all_present.png ) |
| 6 | **Open Submit Modal**<br>Faculty initiates submit to institutional ledger | **PASS** | [`screenshots/06_open_submit_modal.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/06_open_submit_modal.png ) |
| 7 | **Confirm Submit**<br>Confirm marks write into acd.attendance_records via API Gateway | **PASS** | [`screenshots/07_confirm_submit.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/07_confirm_submit.png ) |
| 8 | **Verify Faculty Success**<br>Success banner confirms attendance locked into ledger | **PASS** | [`screenshots/08_verify_faculty_success.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/08_verify_faculty_success.png ) |
| 9 | **DB Check Before Restart**<br>Confirm attendance session and records exist in PostgreSQL before service restart | **PASS** | [`screenshots/09_db_check_before_restart.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/09_db_check_before_restart.png ) |
| 10 | **Restart Attendance Service**<br>Restart ACD-06 to prove marks are not in-memory | **PASS** | [`screenshots/10_restart_attendance_service.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/10_restart_attendance_service.png ) |
| 11 | **College Admin Auth**<br>College Admin opens institutional attendance oversight after service restart | **PASS** | [`screenshots/11_college_admin_auth.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/11_college_admin_auth.png ) |
| 12 | **College Sees Persisted Session**<br>College Admin desk loads the same Postgres-backed session after restart | **PASS** | [`screenshots/12_college_sees_persisted_session.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/12_college_sees_persisted_session.png ) |
| 13 | **Subjects List From Postgres**<br>ACD-03 subject catalog returns live acd.subjects rows via gateway | **PASS** | [`screenshots/13_subjects_list_from_postgres.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/13_subjects_list_from_postgres.png ) |
| 14 | **Student Auth Dashboard**<br>Student opens portal to view attendance impact from the same persisted records | **PASS** | [`screenshots/14_student_auth_dashboard.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/14_student_auth_dashboard.png ) |
| 15 | **DB Check After Restart**<br>Re-read attendance_records after ACD-06 restart — same rows still present | **PASS** | [`screenshots/15_db_check_after_restart.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots/15_db_check_after_restart.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-H-1-599392`

---

## 4. Performance & Timings

- **Faculty Auth + Attendance Desk:** 6143 ms (Target <= 1500 ms: **WARN**)
- **College Admin Auth:** 3573 ms (Target <= 1500 ms: **WARN**)
- **Student Auth Dashboard:** 6586 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-1/screenshots)
