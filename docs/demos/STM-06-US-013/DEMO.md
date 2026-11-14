# DEMO EVIDENCE REPORT: STM-06-US-013

**Story Title:** Manage document-sharing consent
**Execution Timestamp:** 2026-11-14T12:55:50.902Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student opens documents desk for sharing consent | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/01_student_authentication.png ) |
| 2 | **Student Documents Heading**<br>Student documents desk loaded | **PASS** | [`screenshots/02_student_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/02_student_documents_heading.png ) |
| 3 | **Consent Section Visible**<br>Sharing consent section exposes purpose capture | **PASS** | [`screenshots/03_consent_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/03_consent_section_visible.png ) |
| 4 | **Consent Purpose Input**<br>Purpose input available for stated sharing outcome | **PASS** | [`screenshots/04_consent_purpose_input.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/04_consent_purpose_input.png ) |
| 5 | **Record Consent Control**<br>Record consent routes through student BFF → gateway → document-service | **PASS** | [`screenshots/05_record_consent_control.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/05_record_consent_control.png ) |
| 6 | **PostgreSQL Document Links Present**<br>Confirm document_links table supports SHARE_CONSENT relations | **PASS** | [`screenshots/06_postgresql_document_links_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/06_postgresql_document_links_present.png ) |
| 7 | **Negative RBAC — Unauthenticated Consent Blocked**<br>Unauthenticated GET on sharing-consent returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_consent_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots/07_negative_rbac___unauthenticated_consent_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-06-US-013-941768`

---

## 4. Performance & Timings

- **Student Authentication:** 2937 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-013/screenshots)
