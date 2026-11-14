# DEMO EVIDENCE REPORT: STM-03-EN-002

**Story Title:** Tenant isolation, RLS policies, grants and authorization regression tests
**Execution Timestamp:** 2026-11-14T11:10:13.643Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student authenticates via development test session and opens Course registration | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/01_student_authentication.png ) |
| 2 | **Verify Student Enrolment Desk**<br>Student enrolment desk loads — authorized tenant-scoped read path | **PASS** | [`screenshots/02_verify_student_enrolment_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/02_verify_student_enrolment_desk.png ) |
| 3 | **Select Owned Enrolment**<br>Student selects an owned enrolment (same-tenant success path) | **PASS** | [`screenshots/03_select_owned_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/03_select_owned_enrolment.png ) |
| 4 | **Verify Enrolment Details Panel**<br>Details panel proves authorized same-tenant read succeeded | **PASS** | [`screenshots/04_verify_enrolment_details_panel.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/04_verify_enrolment_details_panel.png ) |
| 5 | **PostgreSQL Same-Tenant Ownership Read-Back**<br>Confirm enrolment row is owned by student1 under pilot tenant | **PASS** | [`screenshots/05_postgresql_same_tenant_ownership_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/05_postgresql_same_tenant_ownership_read_back.png ) |
| 6 | **RLS Forced On Enrolment Tables**<br>Verify FORCE ROW LEVEL SECURITY remains active on stu enrolment tables | **PASS** | [`screenshots/06_rls_forced_on_enrolment_tables.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/06_rls_forced_on_enrolment_tables.png ) |
| 7 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>API Gateway blocks unauthenticated GET to enrolment detail with HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots/07_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-002-601486`

---

## 4. Performance & Timings

- **Student Authentication:** 2340 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-002/screenshots)
