# DEMO EVIDENCE REPORT: STM-CROSS-EN-008

**Story Title:** Intelligence Services integration with domain authorization safeguards
**Execution Timestamp:** 2026-11-14T12:25:06.546Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Documents Desk**<br>Domain mutations emit intelligence events via ControlledIntelligenceAdapter | **PASS** | [`screenshots/01_college_documents_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/screenshots/01_college_documents_desk.png ) |
| 2 | **Documents Heading**<br>Authorized domain path | **PASS** | [`screenshots/02_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/screenshots/02_documents_heading.png ) |
| 3 | **Outbox Schema**<br>sys.outbox_events available for intelligence emissions (liveEndpoint may be false) | **PASS** | [`screenshots/03_outbox_schema.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/screenshots/03_outbox_schema.png ) |
| 4 | **Unauth Types Blocked**<br>Unauthorized actors cannot trigger domain/intel path | **PASS** | [`screenshots/04_unauth_types_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/screenshots/04_unauth_types_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-CROSS-EN-008-101235`

---

## 4. Performance & Timings

- **College Documents Desk:** 1078 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-008/screenshots)
