# DEMO EVIDENCE REPORT: STM-10-EN-006

**Story Title:** RLS, security testing, centralized logging, observability and documentation
**Execution Timestamp:** 2026-11-14T12:20:28.926Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Portal Authentication**<br>Authenticated student opens portal under tenant-scoped session | **PASS** | [`screenshots/01_student_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/01_student_portal_authentication.png ) |
| 2 | **Dashboard Loaded**<br>Student dashboard proves authenticated portal path | **PASS** | [`screenshots/02_dashboard_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/02_dashboard_loaded.png ) |
| 3 | **Profile Panel Scoped**<br>Profile panel exposes enrollment identity under session scope | **PASS** | [`screenshots/03_profile_panel_scoped.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/03_profile_panel_scoped.png ) |
| 4 | **Documents Desk Security Path**<br>Documents desk remains available for owner-scoped reads | **PASS** | [`screenshots/04_documents_desk_security_path.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/04_documents_desk_security_path.png ) |
| 5 | **Documents Heading Visible**<br>Documents heading confirms owner-scoped UI | **PASS** | [`screenshots/05_documents_heading_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/05_documents_heading_visible.png ) |
| 6 | **PostgreSQL Students RLS Forced**<br>Verify stu.students has RLS enabled and FORCED | **PASS** | [`screenshots/06_postgresql_students_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/06_postgresql_students_rls_forced.png ) |
| 7 | **Negative RBAC — Unauthenticated Students Me Blocked**<br>Unauthenticated GET /api/v1/students/me returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_students_me_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/07_negative_rbac___unauthenticated_students_me_blocked.png ) |
| 8 | **Negative RBAC — Unauthenticated Documents Blocked**<br>Unauthenticated document types contract returns HTTP 401 | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_documents_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots/08_negative_rbac___unauthenticated_documents_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-10-EN-006-819216`

---

## 4. Performance & Timings

- **Student Portal Authentication:** 1556 ms (Target <= 1500 ms: **WARN**)
- **Documents Desk Security Path:** 769 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-10-EN-006/screenshots)
