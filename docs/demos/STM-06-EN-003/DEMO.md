# DEMO EVIDENCE REPORT: STM-06-EN-003

**Story Title:** Secure upload/download, file validation, malware scanning and signed URL handling
**Execution Timestamp:** 2026-11-14T13:01:52.635Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student opens documents desk for secure signed download controls | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/01_student_authentication.png ) |
| 2 | **Student Documents Heading**<br>Student documents desk loaded | **PASS** | [`screenshots/02_student_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/02_student_documents_heading.png ) |
| 3 | **Secure Download Section Visible**<br>Signed URL / malware-gated download section is available | **PASS** | [`screenshots/03_secure_download_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/03_secure_download_section_visible.png ) |
| 4 | **Signed URL Document Input**<br>Document id input for signed URL issuance | **PASS** | [`screenshots/04_signed_url_document_input.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/04_signed_url_document_input.png ) |
| 5 | **Issue Signed URL Control**<br>Issue signed URL routes through student BFF → gateway → document-service | **PASS** | [`screenshots/05_issue_signed_url_control.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/05_issue_signed_url_control.png ) |
| 6 | **PostgreSQL Document Links Present**<br>Confirm document_links supports MALWARE_SCAN_PASSED persistence without DDL | **PASS** | [`screenshots/06_postgresql_document_links_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/06_postgresql_document_links_present.png ) |
| 7 | **Negative RBAC — Unauthenticated Signed URL Blocked**<br>Unauthenticated POST on signed-url returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_signed_url_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots/07_negative_rbac___unauthenticated_signed_url_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-EN-003-302981`

---

## 4. Performance & Timings

- **Student Authentication:** 3370 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-EN-003/screenshots)
