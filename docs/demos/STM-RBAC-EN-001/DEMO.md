# DEMO EVIDENCE REPORT: STM-RBAC-EN-001

**Story Title:** Central RBAC and ABAC enforcement for all STM services
**Execution Timestamp:** 2026-11-14T12:25:51.569Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Path Allowed**<br>College admin reaches documents governance | **PASS** | [`screenshots/01_college_admin_path_allowed.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/01_college_admin_path_allowed.png ) |
| 2 | **College Documents Heading**<br>Authorized role path | **PASS** | [`screenshots/02_college_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/02_college_documents_heading.png ) |
| 3 | **Student Owner Path**<br>Student reaches own documents desk | **PASS** | [`screenshots/03_student_owner_path.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/03_student_owner_path.png ) |
| 4 | **Student Documents Heading**<br>Student role scoped path | **PASS** | [`screenshots/04_student_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/04_student_documents_heading.png ) |
| 5 | **Unauth Gateway Blocked**<br>No role → 401 across STM ingress | **PASS** | [`screenshots/05_unauth_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/05_unauth_gateway_blocked.png ) |
| 6 | **Unauth Guardians Blocked**<br>No role → 401 guardians | **PASS** | [`screenshots/06_unauth_guardians_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/06_unauth_guardians_blocked.png ) |
| 7 | **Unauth Students Blocked**<br>No role → 401 students | **PASS** | [`screenshots/07_unauth_students_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/07_unauth_students_blocked.png ) |
| 8 | **Students RLS Forced**<br>ABAC/tenant via RLS FORCE on students | **PASS** | [`screenshots/08_students_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots/08_students_rls_forced.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-RBAC-EN-001-142572`

---

## 4. Performance & Timings

- **College Admin Path Allowed:** 871 ms (Target <= 1500 ms: **PASS**)
- **Student Owner Path:** 1044 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-RBAC-EN-001/screenshots)
