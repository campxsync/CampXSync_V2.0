# DEMO EVIDENCE REPORT: STM-CROSS-EN-004

**Story Title:** Tenant isolation, RLS policies and authorization regression
**Execution Timestamp:** 2026-11-14T12:24:48.429Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Auth**<br>Owner-scoped student session | **PASS** | [`screenshots/01_student_auth.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/01_student_auth.png ) |
| 2 | **Student Documents Heading**<br>Owner document path | **PASS** | [`screenshots/02_student_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/02_student_documents_heading.png ) |
| 3 | **Students RLS Forced**<br>stu.students FORCE RLS | **PASS** | [`screenshots/03_students_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/03_students_rls_forced.png ) |
| 4 | **Enrolments RLS Forced**<br>stu.enrolments FORCE RLS | **PASS** | [`screenshots/04_enrolments_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/04_enrolments_rls_forced.png ) |
| 5 | **Applications RLS Forced**<br>stu.applications FORCE RLS | **PASS** | [`screenshots/05_applications_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/05_applications_rls_forced.png ) |
| 6 | **Unauth Students Me 401**<br>Gateway auth regression | **PASS** | [`screenshots/06_unauth_students_me_401.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/06_unauth_students_me_401.png ) |
| 7 | **Unauth Lifecycle 401**<br>Gateway auth regression lifecycle | **PASS** | [`screenshots/07_unauth_lifecycle_401.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots/07_unauth_lifecycle_401.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-CROSS-EN-004-080748`

---

## 4. Performance & Timings

- **Student Auth:** 987 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-004/screenshots)
