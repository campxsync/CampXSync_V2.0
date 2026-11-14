# DEMO EVIDENCE REPORT: STM-05-EN-001

**Story Title:** Supabase relationship, contact, consent and permission persistence
**Execution Timestamp:** 2026-11-14T11:54:49.466Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator opens Guardians desk backed by PostgresGuardianPersistence | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/01_college_admin_authentication.png ) |
| 2 | **Guardians Desk Loaded**<br>College guardians heading proves UI → gateway → guardian-service path | **PASS** | [`screenshots/02_guardians_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/02_guardians_desk_loaded.png ) |
| 3 | **Guardian Form Section Visible**<br>Relationship persistence form exposes student and guardian capture fields | **PASS** | [`screenshots/03_guardian_form_section_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/03_guardian_form_section_visible.png ) |
| 4 | **Student Id Input Visible**<br>Student record id input is available for guardianship persistence scope | **PASS** | [`screenshots/04_student_id_input_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/04_student_id_input_visible.png ) |
| 5 | **Add Guardian Control Visible**<br>Add guardian action routes create through BFF → gateway → Java persistence | **PASS** | [`screenshots/05_add_guardian_control_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/05_add_guardian_control_visible.png ) |
| 6 | **PostgreSQL Guardianship Read-Back**<br>Confirm stu.guardianships rows exist for pilot tenant under RLS-forced schema | **PASS** | [`screenshots/06_postgresql_guardianship_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/06_postgresql_guardianship_read_back.png ) |
| 7 | **Schema Ownership / RLS Audit**<br>Verify guardianships RLS is enabled and FORCED | **PASS** | [`screenshots/07_schema_ownership___rls_audit.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/07_schema_ownership___rls_audit.png ) |
| 8 | **Negative RBAC — Unauthenticated Gateway Blocked**<br>Unauthenticated GET on guardians contract returns HTTP 401 | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_gateway_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots/08_negative_rbac___unauthenticated_gateway_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-05-EN-001-278250`

---

## 4. Performance & Timings

- **College Admin Authentication:** 3780 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-05-EN-001/screenshots)
