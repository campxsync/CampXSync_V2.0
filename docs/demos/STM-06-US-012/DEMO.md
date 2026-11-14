# DEMO EVIDENCE REPORT: STM-06-US-012

**Story Title:** Manage document access permissions
**Execution Timestamp:** 2026-11-14T12:55:41.324Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens documents desk for access grant governance | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/01_college_admin_authentication.png ) |
| 2 | **Documents Desk Loaded**<br>Documents heading visible | **PASS** | [`screenshots/02_documents_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/02_documents_desk_loaded.png ) |
| 3 | **Open Access Permissions Tab**<br>Switch to access permissions surface (STM-06-US-012) | **PASS** | [`screenshots/03_open_access_permissions_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/03_open_access_permissions_tab.png ) |
| 4 | **Access Section Visible**<br>Access grant form section is available | **PASS** | [`screenshots/04_access_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/04_access_section_visible.png ) |
| 5 | **Grant Control Visible**<br>Grant access control routes through BFF → gateway → document-service | **PASS** | [`screenshots/05_grant_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/05_grant_control_visible.png ) |
| 6 | **PostgreSQL Document Links Schema**<br>Confirm doc.document_links exists for access grant persistence | **PASS** | [`screenshots/06_postgresql_document_links_schema.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/06_postgresql_document_links_schema.png ) |
| 7 | **Negative RBAC — Unauthenticated Access Grants Blocked**<br>Unauthenticated GET on access-grants returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_access_grants_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots/07_negative_rbac___unauthenticated_access_grants_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-US-012-929284`

---

## 4. Performance & Timings

- **College Admin Authentication:** 5138 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-012/screenshots)
