# DEMO EVIDENCE REPORT: STM-06-EN-001

**Story Title:** Supabase document metadata persistence and CRUD integration
**Execution Timestamp:** 2026-11-14T12:06:25.142Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Document Governance desk backed by PostgresDocumentPersistence | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Documents Desk Loaded**<br>College documents heading proves UI → gateway → document-service path | **PASS** | [`screenshots/02_documents_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/02_documents_desk_loaded.png ) |
| 3 | **Open Document Types Catalog**<br>Switch to document types catalog for metadata CRUD surface | **PASS** | [`screenshots/03_open_document_types_catalog.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/03_open_document_types_catalog.png ) |
| 4 | **Catalog Tab Active**<br>Document types catalog tab remains selected for metadata governance | **PASS** | [`screenshots/04_catalog_tab_active.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/04_catalog_tab_active.png ) |
| 5 | **Open Stage Requirements Tab**<br>Stage requirements tab exposes persisted programme/stage metadata rules | **PASS** | [`screenshots/05_open_stage_requirements_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/05_open_stage_requirements_tab.png ) |
| 6 | **PostgreSQL Document Types Read-Back**<br>Confirm doc.document_types rows exist for pilot tenant under RLS-enabled schema | **PASS** | [`screenshots/06_postgresql_document_types_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/06_postgresql_document_types_read_back.png ) |
| 7 | **Schema Ownership / RLS Audit**<br>Verify stage_document_requirements has RLS enabled and FORCED | **PASS** | [`screenshots/07_schema_ownership___rls_audit.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/07_schema_ownership___rls_audit.png ) |
| 8 | **Negative RBAC — Unauthenticated Types Blocked**<br>Unauthenticated GET on document types contract returns HTTP 401 | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_types_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots/08_negative_rbac___unauthenticated_types_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-EN-001-960628`

---

## 4. Performance & Timings

- **College Admin Authentication:** 5031 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-001/screenshots)
