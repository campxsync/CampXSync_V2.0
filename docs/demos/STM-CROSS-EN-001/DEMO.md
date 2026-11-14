# DEMO EVIDENCE REPORT: STM-CROSS-EN-001

**Story Title:** Audit existing Supabase/PostgreSQL schemas, migrations, tables, FKs, grants and RLS
**Execution Timestamp:** 2026-11-14T12:22:45.353Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens documents desk as operational entry for schema-backed services | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Documents Desk Loaded**<br>Documents desk proves live UI path against audited doc schema | **PASS** | [`screenshots/02_documents_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/02_documents_desk_loaded.png ) |
| 3 | **Students RLS Forced**<br>Verify stu.students RLS enabled and FORCED | **PASS** | [`screenshots/03_students_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/03_students_rls_forced.png ) |
| 4 | **Guardianships RLS Forced**<br>Verify stu.guardianships RLS enabled and FORCED | **PASS** | [`screenshots/04_guardianships_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/04_guardianships_rls_forced.png ) |
| 5 | **Lifecycle Status RLS Forced**<br>Verify stu.lifecycle_status RLS enabled and FORCED | **PASS** | [`screenshots/05_lifecycle_status_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/05_lifecycle_status_rls_forced.png ) |
| 6 | **Stage Requirements RLS Forced**<br>Verify doc.stage_document_requirements RLS enabled and FORCED | **PASS** | [`screenshots/06_stage_requirements_rls_forced.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/06_stage_requirements_rls_forced.png ) |
| 7 | **Documents RLS Enabled**<br>Verify doc.documents has RLS enabled (FORCE gap documented in audit report) | **PASS** | [`screenshots/07_documents_rls_enabled.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/07_documents_rls_enabled.png ) |
| 8 | **Outbox Schema Present**<br>Verify sys.outbox_events table exists for intelligence outbox | **PASS** | [`screenshots/08_outbox_schema_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots/08_outbox_schema_present.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-CROSS-EN-001-955176`

---

## 4. Performance & Timings

- **College Admin Authentication:** 1404 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-001/screenshots)
