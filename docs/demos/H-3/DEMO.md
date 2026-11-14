# DEMO EVIDENCE REPORT: H-3

**Story Title:** Internal auth ON — JWT to gateway, HMAC downstream, forged headers rejected
**Execution Timestamp:** 2026-11-14T07:23:36.876Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Platform Admin JWT Session**<br>Platform admin test-session establishes real Supabase JWT cookies | **PASS** | [`screenshots/01_platform_admin_jwt_session.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/01_platform_admin_jwt_session.png ) |
| 2 | **Platform Dashboard With Auth**<br>Platform dashboard loads via Bearer JWT (no spoofable X-User headers) | **PASS** | [`screenshots/02_platform_dashboard_with_auth.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/02_platform_dashboard_with_auth.png ) |
| 3 | **Institutes Visible Under Auth**<br>Real institutes table still renders with internal auth enabled | **PASS** | [`screenshots/03_institutes_visible_under_auth.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/03_institutes_visible_under_auth.png ) |
| 4 | **Forged Direct Lifecycle Call Rejected**<br>Direct loopback call to lifecycle-service with forged X-User-Id and no gateway HMAC is rejected | **PASS** | [`screenshots/04_forged_direct_lifecycle_call_rejected.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/04_forged_direct_lifecycle_call_rejected.png ) |
| 5 | **Forged Gateway Call Without JWT Rejected**<br>Gateway rejects spoofed identity headers when Authorization Bearer is missing | **PASS** | [`screenshots/05_forged_gateway_call_without_jwt_rejected.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/05_forged_gateway_call_without_jwt_rejected.png ) |
| 6 | **College Admin Persona**<br>College admin JWT session reaches lifecycle desk | **PASS** | [`screenshots/06_college_admin_persona.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/06_college_admin_persona.png ) |
| 7 | **College Lifecycle Desk**<br>College lifecycle page loads under internal auth | **PASS** | [`screenshots/07_college_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/07_college_lifecycle_desk.png ) |
| 8 | **Faculty Persona**<br>Faculty JWT session reaches advisees desk | **PASS** | [`screenshots/08_faculty_persona.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/08_faculty_persona.png ) |
| 9 | **Faculty Advisees Desk**<br>Faculty advisees page loads under internal auth | **PASS** | [`screenshots/09_faculty_advisees_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/09_faculty_advisees_desk.png ) |
| 10 | **Student Persona**<br>Student JWT session reaches student lifecycle desk | **PASS** | [`screenshots/10_student_persona.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/10_student_persona.png ) |
| 11 | **Student Lifecycle Desk**<br>Student lifecycle page loads under internal auth | **PASS** | [`screenshots/11_student_lifecycle_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots/11_student_lifecycle_desk.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-H-3-993533`

---

## 4. Performance & Timings

- **Platform Admin JWT Session:** 6104 ms (Target <= 1500 ms: **WARN**)
- **College Admin Persona:** 3198 ms (Target <= 1500 ms: **WARN**)
- **Faculty Persona:** 2047 ms (Target <= 1500 ms: **WARN**)
- **Student Persona:** 2450 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/H-3/screenshots)
