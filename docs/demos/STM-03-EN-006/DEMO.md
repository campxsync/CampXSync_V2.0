# DEMO EVIDENCE REPORT: STM-03-EN-006

**Story Title:** Kafka events, notifications, centralized logging and observability
**Execution Timestamp:** 2026-11-14T11:20:28.330Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **Student Authentication**<br>Student opens Course Registration to exercise in-service alerts (not an external Kafka consumer) | **PASS** | [`screenshots/01_student_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/01_student_authentication.png ) |
| 2 | **Student Enrolment Desk Visible**<br>Student desk loads with observability-facing UI contracts | **PASS** | [`screenshots/02_student_enrolment_desk_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/02_student_enrolment_desk_visible.png ) |
| 3 | **In-Service Alerts Panel Present**<br>Alerts region is present for STM-03 in-service notifications (ControlledIntelligenceAdapter liveEndpoint=false) | **PASS** | [`screenshots/03_in_service_alerts_panel_present.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/03_in_service_alerts_panel_present.png ) |
| 4 | **Select Owned Enrolment**<br>Student selects enrolment to confirm logged UI→gateway→service path remains healthy | **PASS** | [`screenshots/04_select_owned_enrolment.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/04_select_owned_enrolment.png ) |
| 5 | **Details Panel Visible**<br>Details panel confirms service response after centralized logging path | **PASS** | [`screenshots/05_details_panel_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/05_details_panel_visible.png ) |
| 6 | **PostgreSQL Enrolment Read-Back**<br>Persistence remains source of truth alongside intelligence emit contract | **PASS** | [`screenshots/06_postgresql_enrolment_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/06_postgresql_enrolment_read_back.png ) |
| 7 | **Negative RBAC — Unauthenticated Alerts Contract Blocked**<br>Unauthenticated GET on in-service alerts returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_alerts_contract_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots/07_negative_rbac___unauthenticated_alerts_contract_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-006-218197`

---

## 4. Performance & Timings

- **Student Authentication:** 2243 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-006/screenshots)
