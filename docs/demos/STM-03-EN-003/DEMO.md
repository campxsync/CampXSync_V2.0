# DEMO EVIDENCE REPORT: STM-03-EN-003

**Story Title:** Concurrent seat allocation, transaction integrity and idempotent registration
**Execution Timestamp:** 2026-11-14T11:11:48.482Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Course Registration Desk to manage section capacity | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded**<br>College enrolment desk loads with catalogue/capacity controls | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/02_verify_enrolment_desk_loaded.png ) |
| 3 | **Open Catalogue Section**<br>Catalogue section exposes capacity input used for seat allocation | **PASS** | [`screenshots/03_open_catalogue_section.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/03_open_catalogue_section.png ) |
| 4 | **Select Academic Term**<br>Bind the live academic term so section capacity reads go through Java persistence | **PASS** | [`screenshots/04_select_academic_term.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/04_select_academic_term.png ) |
| 5 | **Confirm Term Bound**<br>UI confirms term selection for capacity-scoped operations | **PASS** | [`screenshots/05_confirm_term_bound.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/05_confirm_term_bound.png ) |
| 6 | **PostgreSQL Section Capacity Integrity Read-Back**<br>Confirm enrolment sections store capacity with enrolled_count never exceeding capacity | **PASS** | [`screenshots/06_postgresql_section_capacity_integrity_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/06_postgresql_section_capacity_integrity_read_back.png ) |
| 7 | **Negative RBAC — Unauthenticated Register Blocked**<br>API Gateway blocks unauthenticated POST registration (seat allocation) with HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_register_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots/07_negative_rbac___unauthenticated_register_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-003-699774`

---

## 4. Performance & Timings

- **College Admin Authentication:** 1506 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-003/screenshots)
