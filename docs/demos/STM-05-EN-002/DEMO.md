# DEMO EVIDENCE REPORT: STM-05-EN-002

**Story Title:** RLS and resource-scoped guardian access controls
**Execution Timestamp:** 2026-11-14T12:49:54.739Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Guardians desk for RLS/resource-scope proof | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/01_college_admin_authentication.png ) |
| 2 | **Guardians Desk Loaded**<br>College guardians heading proves staff path under tenant scope | **PASS** | [`screenshots/02_guardians_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/02_guardians_desk_loaded.png ) |
| 3 | **Student Id Scope Input**<br>Student id input enforces resource-scoped guardianship lookups | **PASS** | [`screenshots/03_student_id_scope_input.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/03_student_id_scope_input.png ) |
| 4 | **Student Guardians Self-Service**<br>Student opens own guardians desk (owner resource scope) | **PASS** | [`screenshots/04_student_guardians_self_service.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/04_student_guardians_self_service.png ) |
| 5 | **Student Guardians Heading**<br>Student guardians heading confirms owner-scoped UI | **PASS** | [`screenshots/05_student_guardians_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/05_student_guardians_heading.png ) |
| 6 | **PostgreSQL Guardianships RLS Forced**<br>Verify stu.guardianships RLS enabled and FORCED | **PASS** | [`screenshots/06_postgresql_guardianships_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/06_postgresql_guardianships_rls_forced.png ) |
| 7 | **Emergency Contacts RLS Enabled**<br>Verify core.person_emergency_contacts has RLS enabled | **PASS** | [`screenshots/07_emergency_contacts_rls_enabled.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/07_emergency_contacts_rls_enabled.png ) |
| 8 | **Negative RBAC — Unauthenticated Guardians Blocked**<br>Unauthenticated GET on guardians contract returns HTTP 401 | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_guardians_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots/08_negative_rbac___unauthenticated_guardians_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-05-EN-002-577797`

---

## 4. Performance & Timings

- **College Admin Authentication:** 7165 ms (Target <= 1500 ms: **WARN**)
- **Student Guardians Self-Service:** 2348 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-002/screenshots)
