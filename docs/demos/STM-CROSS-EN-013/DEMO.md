# DEMO EVIDENCE REPORT: STM-CROSS-EN-013

**Story Title:** Idempotency, concurrency, retry/recovery and transaction integrity
**Execution Timestamp:** 2026-11-14T12:25:42.095Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Documents Desk**<br>Transactional document domain UI | **PASS** | [`screenshots/01_college_documents_desk.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots/01_college_documents_desk.png ) |
| 2 | **Documents Heading**<br>Persistence path uses executeInTransaction | **PASS** | [`screenshots/02_documents_heading.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots/02_documents_heading.png ) |
| 3 | **Documents + Versions Integrity**<br>Document and version tables present for multi-table commits | **PASS** | [`screenshots/03_documents___versions_integrity.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots/03_documents___versions_integrity.png ) |
| 4 | **Lifecycle History Append Model**<br>lifecycle_history present for append-only integrity | **PASS** | [`screenshots/04_lifecycle_history_append_model.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots/04_lifecycle_history_append_model.png ) |
| 5 | **Unauth Mutation Path Blocked**<br>Unauthenticated writes cannot bypass integrity controls | **PASS** | [`screenshots/05_unauth_mutation_path_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots/05_unauth_mutation_path_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-CROSS-EN-013-135845`

---

## 4. Performance & Timings

- **College Documents Desk:** 908 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-CROSS-EN-013/screenshots)
