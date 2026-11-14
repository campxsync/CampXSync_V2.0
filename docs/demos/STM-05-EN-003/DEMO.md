# DEMO EVIDENCE REPORT: STM-05-EN-003

**Story Title:** Consent lifecycle, verification and revocation enforcement
**Execution Timestamp:** 2026-11-14T11:59:02.754Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Guardians desk for consent grant/revoke controls | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/01_college_admin_authentication.png ) |
| 2 | **Guardians Desk Loaded**<br>Guardians heading proves UI → gateway → guardian-service path | **PASS** | [`screenshots/02_guardians_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/02_guardians_desk_loaded.png ) |
| 3 | **Consent Grant Control Visible**<br>Grant access control exposes consent category grant path | **PASS** | [`screenshots/03_consent_grant_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/03_consent_grant_control_visible.png ) |
| 4 | **Consent Period Control Visible**<br>Save period control exposes consent validity window management | **PASS** | [`screenshots/04_consent_period_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/04_consent_period_control_visible.png ) |
| 5 | **Revocation Control Visible**<br>Revoke access control exposes consent revocation enforcement path | **PASS** | [`screenshots/05_revocation_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/05_revocation_control_visible.png ) |
| 6 | **PostgreSQL Guardianship Consent Scope Read-Back**<br>Confirm guardianship rows with access flags exist for pilot tenant | **PASS** | [`screenshots/06_postgresql_guardianship_consent_scope_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/06_postgresql_guardianship_consent_scope_read_back.png ) |
| 7 | **Negative RBAC — Unauthenticated Revoke Blocked**<br>Unauthenticated POST revoke-style guardians contract returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_revoke_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots/07_negative_rbac___unauthenticated_revoke_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-05-EN-003-533557`

---

## 4. Performance & Timings

- **College Admin Authentication:** 2757 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-003/screenshots)
