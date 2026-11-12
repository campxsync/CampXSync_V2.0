# DEMO EVIDENCE REPORT: STM-06-US-006

**Story Title:** Document Expiry, Retention & Regulatory Audit Trail
**Execution Timestamp:** 2026-11-12T09:47:04.792Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Portal Authentication**<br>Student authenticates to CampXSync with authorized student session | **PASS** | [`screenshots/01_portal_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/01_portal_authentication.png ) |
| 2 | **Navigate to Student Documents Desk**<br>Student documents workspace loaded with checklist and active submissions tabs | **PASS** | [`screenshots/02_navigate_to_student_documents_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/02_navigate_to_student_documents_desk.png ) |
| 3 | **Switch to My Submissions Tab**<br>View student's committed document records, versions, retention policies, and expiry warnings | **PASS** | [`screenshots/03_switch_to_my_submissions_tab.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/03_switch_to_my_submissions_tab.png ) |
| 4 | **Verify Expiry Badges & Retention Policies**<br>System detects document expiration thresholds: EXPIRING SOON (<30d) and EXPIRED badges rendered with retention policy codes | **PASS** | [`screenshots/04_verify_expiry_badges___retention_policies.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/04_verify_expiry_badges___retention_policies.png ) |
| 5 | **Inspect Regulatory Audit Trail**<br>Click 'Audit Trail' button on the document to open the regulatory access history modal | **PASS** | [`screenshots/05_inspect_regulatory_audit_trail.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/05_inspect_regulatory_audit_trail.png ) |
| 6 | **Verify Regulatory Details & Empty Initial Log**<br>Audit modal displays document retention policy (RET_10_YEARS), calculated expiry date, and access history | **PASS** | [`screenshots/06_verify_regulatory_details___empty_initial_log.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/06_verify_regulatory_details___empty_initial_log.png ) |
| 7 | **Trigger Regulatory Download & Access Log**<br>Student clicks 'Download & Record Access' button, dispatching POST /api/v1/documents/:id/access-log through API Gateway into doc.document_deliveries | **PASS** | [`screenshots/07_trigger_regulatory_download___access_log.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/07_trigger_regulatory_download___access_log.png ) |
| 8 | **Verify Live Committed Regulatory Delivery Log**<br>Regulatory access entry appears live in the audit table with Channel DOWNLOAD, Status DOWNLOADED, and client IP | **PASS** | [`screenshots/08_verify_live_committed_regulatory_delivery_log.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/08_verify_live_committed_regulatory_delivery_log.png ) |
| 9 | **Close Audit Modal**<br>Close audit modal and return to submissions overview | **PASS** | [`screenshots/09_close_audit_modal.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/09_close_audit_modal.png ) |
| 10 | **PostgreSQL Database committed check**<br>Read-only query verifies doc.document_deliveries has committed the audit log record with channel DOWNLOAD and status DOWNLOADED | **PASS** | [`screenshots/10_postgresql_database_committed_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/10_postgresql_database_committed_check.png ) |
| 11 | **Negative RBAC Enforcement Check**<br>API Gateway and Document Service reject unauthenticated/unauthorized access to document audit trail with HTTP 401/403 | **PASS** | [`screenshots/11_negative_rbac_enforcement_check.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots/11_negative_rbac_enforcement_check.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-US-006-807830`

---

## 4. Performance & Timings

- **Portal Authentication:** 3147 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/screenshots)
