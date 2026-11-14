# DEMO EVIDENCE REPORT: STM-03-EN-001

**Story Title:** Supabase persistence, CRUD, schema ownership audit and database integration
**Execution Timestamp:** 2026-11-14T11:07:44.608Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator authenticates via development test session and opens the Course Registration Desk | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded**<br>College enrolment desk renders — UI path to Java enrolment service backed by PostgresEnrollmentPersistence | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/02_verify_enrolment_desk_loaded.png ) |
| 3 | **Select Academic Term**<br>Registrar selects the existing 2026 ODD academic term so catalogue/CRUD reads use live JDBC | **PASS** | [`screenshots/03_select_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/03_select_academic_term.png ) |
| 4 | **Confirm Term Bound**<br>UI confirms the academic term is selected for persistence-scoped operations | **PASS** | [`screenshots/04_confirm_term_bound.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/04_confirm_term_bound.png ) |
| 5 | **PostgreSQL Enrolment Row Read-Back**<br>Read-only query proves stu.enrolments CRUD data exists for the pilot tenant via Supabase PostgreSQL | **PASS** | [`screenshots/05_postgresql_enrolment_row_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/05_postgresql_enrolment_row_read_back.png ) |
| 6 | **Schema Ownership / RLS Audit**<br>Verify STM-03 enrolment tables have RLS enabled and FORCED under stu schema (service-owned model) | **PASS** | [`screenshots/06_schema_ownership___rls_audit.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/06_schema_ownership___rls_audit.png ) |
| 7 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated GET to enrolment list with HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-001-451986`

---

## 4. Performance & Timings

- **College Admin Authentication:** 4413 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-001/screenshots)
