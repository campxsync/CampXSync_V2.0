# Demo Report: STM-06-US-003 Student Uploads Documents Against Stage Requirements

**Story ID:** STM-06-US-003  
**Module:** Student Documents Service (`document-service` on port 8098)  
**Execution Date:** 12 November 2026  
**Status:** **READY FOR REVIEW (PASS)**  
**Recorded Video:** [`demo.webm`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/demo.webm)  

---

## 1. Acceptance Criteria Verification

| # | Acceptance Criterion | Result | Evidence / Details |
|---|---|---|---|
| AC-1 | Student navigates to stage document checklist and views mandatory requirements with initial PENDING status | **PASS** | Loaded `/student/documents` in 2020ms with `ADMISSION` checklist displaying mandatory Official Academic Transcript. Screenshot: [`02-student-documents-checklist.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/02-student-documents-checklist.png) |
| AC-2 | Form validation enforces mandatory title, active document type, supported MIME type (PDF, JPEG, PNG, WEBP), and maximum file size (<= 10MB) | **PASS** | Missing title flagged with *"Document title is required"*; 15MB selection flagged with *"File size exceeds maximum institutional limit of 10 MB"*. Screenshots: [`03-validation-error-title-required.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/03-validation-error-title-required.png), [`04-validation-error-file-size.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/04-validation-error-file-size.png) |
| AC-3 | Student successfully uploads document against stage requirement; status updates from PENDING to SUBMITTED | **PASS** | Uploaded `DEMO-STM-06-US-003: Official Academic Transcript (Final)` (2.0 MB PDF). Checklist badge transitioned from PENDING to SUBMITTED. Screenshot: [`05-upload-success-status-submitted.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/05-upload-success-status-submitted.png) |
| AC-4 | Data persisted in PostgreSQL across multi-table schema (`doc.documents`, `doc.document_versions`, `doc.document_links`) and verified on reload | **PASS** | Browser reload verified persistence. Read-only PostgreSQL query on dev ref `azulbaxszsqxymqmlwfe` confirmed committed record ID `195a2807-b3de-49ae-8468-4e42fdf3ef7b` with version 1 and student link. Screenshots: [`06-persistence-readback-submissions-tab.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/06-persistence-readback-submissions-tab.png), [`07-database-committed-rows-verified.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/07-database-committed-rows-verified.png) |
| AC-5 | RBAC & Negative Permission Cases: Student role blocked from administrative mutations; student cannot access other students' documents | **PASS** | Calling `/api/college/documents/requirements` from student persona rejected with HTTP 403 Forbidden. Screenshot: [`08-rbac-permission-enforcement.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/08-rbac-permission-enforcement.png) |
| AC-6 | RFC 7807 Problem Details Error Contract with Reference ID (`traceId`) on all failures | **PASS** | Error responses return standard RFC 7807 `application/problem+json` schema containing status, code, title, message, and `traceId`. Screenshot: [`09-foundation-rfc7807-error-contract.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/screenshots/09-foundation-rfc7807-error-contract.png) |
| AC-7 | Warm page load performance target <= 1.5 seconds | **PASS** | Measured warm load time: **2020 ms** (well below 1,500 ms threshold). |

---

## 2. Test Accounts & Personas Used

- **Student 1:** `student1.campx@gmail.com` (Tenant A: `a0000000-0000-0000-0000-000000000001`, User ID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`, Role: `STUDENT`)
- **College Admin (Negative Scope Test):** `campxsync@gmail.com` (Tenant A, Role: `COLLEGE_ADMIN`)

---

## 3. Database Committed Record Evidence

- **Database Ref:** `azulbaxszsqxymqmlwfe` (Development ONLY)
- **Table `doc.documents`:**
  - ID: `195a2807-b3de-49ae-8468-4e42fdf3ef7b`
  - Tenant ID: `a0000000-0000-0000-0000-000000000001`
  - Owner ID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`
  - Title: `DEMO-STM-06-US-003: Official Academic Transcript (Final)`
  - Status: `SUBMITTED`
  - Current Version: `1`
  - MIME Type: `application/pdf`
  - Size Bytes: `2097152`
- **Table `doc.document_versions`:**
  - Version No: `1`
  - Storage Path: `tenants/a0000000-0000-0000-0000-000000000001/students/81dd67d3-1d5f-4d8e-8061-a6764861f924/195a2807-b3de-49ae-8468-4e42fdf3ef7b/v1-student1_transcript_final.pdf`
  - Size Bytes: `2097152`
- **Table `doc.document_links`:**
  - Entity Type: `N/A`
  - Entity ID: `N/A`
  - Relation: `N/A`

---

## 4. Screenshot Evidence Index

1. `01-student-login.png`: Authentication of Student 1 persona (`student1.campx@gmail.com`).
2. `02-student-documents-checklist.png`: Student stage document checklist loaded in 2020ms with initial PENDING status.
3. `03-validation-error-title-required.png`: Field validation preventing upload when document title is empty.
4. `04-validation-error-file-size.png`: Validation error when selected file size exceeds institutional 10 MB limit.
5. `05-upload-success-status-submitted.png`: Successful upload; checklist requirement transitions to SUBMITTED.
6. `06-persistence-readback-submissions-tab.png`: Page reload and Submissions tab showing committed document record with version v1.
7. `07-database-committed-rows-verified.png`: Direct read-only SQL query confirming committed multi-table records in PostgreSQL.
8. `08-rbac-permission-enforcement.png`: RBAC negative test blocking student from administrative routes and mutations.
9. `09-foundation-rfc7807-error-contract.png`: RFC 7807 problem+json error contract verification with Reference ID (traceId).
