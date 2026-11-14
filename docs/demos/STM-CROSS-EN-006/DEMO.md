# DEMO EVIDENCE REPORT: STM-CROSS-EN-006

**Story Title:** API Gateway integration and versioned API contracts
**Execution Timestamp:** 2026-11-14T12:25:00.796Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Auth via BFF**<br>UI uses BFF → Gateway | **PASS** | [`screenshots/01_college_auth_via_bff.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots/01_college_auth_via_bff.png ) |
| 2 | **Documents Desk**<br>Gateway-backed documents desk | **PASS** | [`screenshots/02_documents_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots/02_documents_desk.png ) |
| 3 | **Gateway Types Auth Gate**<br>Versioned /api/v1 documents types require auth | **PASS** | [`screenshots/03_gateway_types_auth_gate.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots/03_gateway_types_auth_gate.png ) |
| 4 | **Gateway Students Auth Gate**<br>Versioned /api/v1 students/me require auth | **PASS** | [`screenshots/04_gateway_students_auth_gate.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots/04_gateway_students_auth_gate.png ) |
| 5 | **OpenAPI Lifecycle Spec Present**<br>Confirm lifecycle OpenAPI artifact exists via repo path evidence in DEMO notes | **PASS** | [`screenshots/05_openapi_lifecycle_spec_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots/05_openapi_lifecycle_spec_present.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-CROSS-EN-006-095051`

---

## 4. Performance & Timings

- **College Auth via BFF:** 815 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-006/screenshots)
