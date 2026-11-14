# DEMO EVIDENCE REPORT: STM-06-EN-004

**Story Title:** RLS, tenant isolation and document-level authorization
**Execution Timestamp:** 2026-11-14T12:12:57.280Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Document Governance desk for RLS/isolation proof | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/01_college_admin_authentication.png ) |
| 2 | **Documents Desk Loaded**<br>College documents heading proves authenticated staff path | **PASS** | [`screenshots/02_documents_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/02_documents_desk_loaded.png ) |
| 3 | **Catalog Tab Visible**<br>Document types catalog remains the metadata governance surface under tenant scope | **PASS** | [`screenshots/03_catalog_tab_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/03_catalog_tab_visible.png ) |
| 4 | **Student Documents Desk**<br>Student document self-service desk remains reachable for document-level ownership path | **PASS** | [`screenshots/04_student_documents_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/04_student_documents_desk.png ) |
| 5 | **Student Documents Heading**<br>Student documents heading proves owner-scoped UI path | **PASS** | [`screenshots/05_student_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/05_student_documents_heading.png ) |
| 6 | **PostgreSQL Documents RLS Enabled**<br>Verify doc.documents has RLS enabled (FORCE deferred pending DDL approval) | **PASS** | [`screenshots/06_postgresql_documents_rls_enabled.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/06_postgresql_documents_rls_enabled.png ) |
| 7 | **PostgreSQL Stage Requirements RLS Forced**<br>Verify stage_document_requirements has RLS enabled and FORCED | **PASS** | [`screenshots/07_postgresql_stage_requirements_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/07_postgresql_stage_requirements_rls_forced.png ) |
| 8 | **Negative RBAC — Unauthenticated Documents Blocked**<br>Unauthenticated GET on document types contract returns HTTP 401 | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_documents_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots/08_negative_rbac___unauthenticated_documents_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-EN-004-366704`

---

## 4. Performance & Timings

- **College Admin Authentication:** 2074 ms (Target <= 1500 ms: **WARN**)
- **Student Documents Desk:** 978 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-004/screenshots)
