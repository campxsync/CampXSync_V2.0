# DEMO EVIDENCE REPORT: STM-04-EN-003

**Story Title:** Approval workflow, notifications and Kafka integration
**Execution Timestamp:** 2026-11-14T11:46:38.406Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>Registrar opens Lifecycle Desk to decide endorsed/pending petitions | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/01_college_admin_authentication.png ) |
| 2 | **Lifecycle Desk Loaded**<br>College lifecycle heading proves UI → gateway → lifecycle-service path | **PASS** | [`screenshots/02_lifecycle_desk_loaded.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/02_lifecycle_desk_loaded.png ) |
| 3 | **Pending Queue Visible**<br>Pending request count surfaces approval-workflow queue from PostgreSQL | **PASS** | [`screenshots/03_pending_queue_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/03_pending_queue_visible.png ) |
| 4 | **Target Leave Petition Row Visible**<br>Pending LEAVE petition selected for registrar approval decision | **PASS** | [`screenshots/04_target_leave_petition_row_visible.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/04_target_leave_petition_row_visible.png ) |
| 5 | **Approve Leave Petition**<br>Registrar approves petition; Java service transitions status and emits intelligence event (liveEndpoint=false) | **PASS** | [`screenshots/05_approve_leave_petition.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/05_approve_leave_petition.png ) |
| 6 | **Approval Success Feedback**<br>UI confirms approval decision persisted through gateway | **PASS** | [`screenshots/06_approval_success_feedback.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/06_approval_success_feedback.png ) |
| 7 | **PostgreSQL Approved Request Read-Back**<br>Confirm request is APPROVED and student standing is ON_LEAVE under RLS-forced tables | **PASS** | [`screenshots/07_postgresql_approved_request_read_back.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/07_postgresql_approved_request_read_back.png ) |
| 8 | **Negative RBAC — Unauthenticated Decide Blocked**<br>Unauthenticated POST decide contract returns HTTP 401 (approval workflow gated by gateway) | **PASS** | [`screenshots/08_negative_rbac___unauthenticated_decide_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots/08_negative_rbac___unauthenticated_decide_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-04-EN-003-788769`

---

## 4. Performance & Timings

- **College Admin Authentication:** 1766 ms (Target <= 1500 ms: **WARN**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-EN-003/screenshots)
