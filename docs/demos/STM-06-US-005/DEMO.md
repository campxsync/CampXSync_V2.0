# DEMO EVIDENCE REPORT: STM-06-US-005

**Story ID:** STM-06-US-005  
**Story Title:** Student views verification status and resubmits rejected documents  
**Execution Timestamp:** 2026-11-12 14:27:20 IST  
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Java Document Service (`http://localhost:8098`), API Gateway (`http://localhost:8080`), Next.js Frontend (`http://localhost:3000`)

---

## 1. Acceptance Criteria Table

| Acceptance Criterion | Result | Evidence Link / Log |
|---|---|---|
| **AC-1: Officer Rejection Visibility on Student Desk**<br>Student can view officer feedback and specific rejection code/reason on `/student/documents`. | **PASS** | `screenshots/02_rejected_document_visible.png`<br>Rendered banner: `Reason: REJ-SEAL-BLURRY: High-resolution 300 DPI scan with visible official embossed seal required`. |
| **AC-2: Resubmit Document Action Available**<br>Rejected document row presents prominent "Resubmit Document" action button. | **PASS** | `screenshots/02_rejected_document_visible.png`<br>Button `#btn-resubmit-doc-0` rendered on rejected document row. |
| **AC-3: Resubmit Modal with Version Progression**<br>Modal shows current version, target version (`v1 -> v2`), and file upload controls. | **PASS** | `screenshots/03_resubmit_modal_opened.png`<br>Modal `#modal-resubmit-overlay` displays target title, version progression (`v1 -> v2`), and original rejection feedback. |
| **AC-4: Mandatory Correction Note Validation**<br>Client & server enforce minimum 3-character correction rationale before allowing resubmission. | **PASS** | `screenshots/04_validation_error_empty_note.png`<br>Blocked with RFC 7807 problem details: `"Please provide a detailed note explaining what was corrected (at least 3 characters)."`. |
| **AC-5: Atomic Version Increment & Status Reset**<br>`POST /api/v1/documents/submissions/{id}/resubmit` increments version atomically to v2, appends version history, and resets status to `SUBMITTED`. | **PASS** | `screenshots/06_resubmission_success_desk.png`<br>Table updated in real-time to Version `v2`, Status `SUBMITTED`. |
| **AC-6: Committed Read-Back & Database Integrity**<br>Hard page reload verifies persisted state; direct PostgreSQL query confirms version 2 and status `SUBMITTED`. | **PASS** | `screenshots/07_reload_persistence_verified.png`<br>`screenshots/08_database_persistence_readback.png`<br>Database read-back: `status=SUBMITTED`, `current_version_no=2`, `version_count=2`. |
| **AC-7: Intelligence Layer Event Publication**<br>Resubmission emits `student.document.uploaded.v1` event with incremented version payload to Intelligence Layer. | **PASS** | Java microservice logs confirm `ControlledIntelligenceAdapter` publication of event `student.document.uploaded.v1`. |
| **AC-8: Cross-Persona Verification Queue Reflection**<br>Document re-appears in College Admin queue (`/college/documents`) as `v2` in `SUBMITTED` state ready for re-review. | **PASS** | `screenshots/09_college_admin_queue_reappears.png`<br>College Admin queue loaded 33 items; resubmitted doc present as `v2` `SUBMITTED`. |
| **AC-9: Negative RBAC Guardrail (Cross-Student Access)**<br>Attempt by non-owner student to resubmit document is rejected with HTTP 403 FORBIDDEN. | **PASS** | `screenshots/10_rbac_unauthorized_resubmit_blocked.png`<br>Gateway & Document Service returned `HTTP 403 [FORBIDDEN]`. |
| **AC-10: Negative Lifecycle Guardrail (Approved Doc Freeze)**<br>Attempt to resubmit a document once in `APPROVED` status is blocked with HTTP 409 CONFLICT. | **PASS** | `screenshots/11_lifecycle_approved_conflict_blocked.png`<br>Gateway & Document Service returned `HTTP 409 [DOCUMENT_ALREADY_APPROVED]`. |

---

## 2. Test Accounts & Personas Used

- **Student Persona:**
  - Email: `student1.campx@gmail.com`
  - User ID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`
  - Tenant ID: `a0000000-0000-0000-0000-000000000001`
  - Role: `STUDENT`
- **College Administrator Persona:**
  - Email: `campxsync@gmail.com`
  - Role: `COLLEGE_ADMIN`
  - Desk: `/college/documents`

---

## 3. Records Created & Verified (Tagged DEMO-US-005)

- **Document ID:** `4a27e24c-0e30-4682-8342-a8a966d02f70`
- **Document Tag:** `DEMO-US005-4a27e24c`
- **Document Title:** `Official Secondary School Leaving Certificate (DEMO-US005-4a27e24c)`
- **Version 1 (Initial):**
  - Storage Path: `tenants/a0000000-0000-0000-0000-000000000001/docs/students/81dd67d3-1d5f-4d8e-8061-a6764861f924/v1_leaving_cert.pdf`
  - Change Note: `Initial scanned certificate upload`
  - Verification Status: `REJECTED` (`REJ-SEAL-BLURRY`)
- **Version 2 (Resubmitted):**
  - Storage Path: `tenants/a0000000-0000-0000-0000-000000000001/docs/students/81dd67d3-1d5f-4d8e-8061-a6764861f924/4a27e24c-0e30-4682-8342-a8a966d02f70/v2_secondary_certificate_300dpi_embossed_seal.pdf`
  - Change Note: `Uploaded re-scanned 300 DPI authentic certificate with clearly visible embossed university seal and registrar signature.`
  - Document Status: `SUBMITTED`

---

## 4. Performance & Timings

- **Student Desk Page Load:** Warm load in **842 ms** (Target: <= 1500 ms) — **PASS**
- **College Admin Desk Page Load:** Warm load in **1120 ms** (Target: <= 1500 ms) — **PASS**
- **Resubmission Transaction Duration:** **28 ms** (atomic version insert + document update in single PostgreSQL transaction).

---

## 5. Artifact Links

- **Full Headed Video Recording:** [demo.webm](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-005/demo.webm)
- **Screenshots Directory:** [screenshots/](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-005/screenshots/)

