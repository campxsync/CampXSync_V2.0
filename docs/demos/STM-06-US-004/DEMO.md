# Demo Report: STM-06-US-004 Institutional Officer Verifies and Approves/Rejects Student Documents

**Story ID:** STM-06-US-004  
**Module:** Student Documents Service (`document-service` on port 8098) & College Documents Desk (`/college/documents`)  
**Execution Date:** 12 November 2026  
**Status:** **READY FOR REVIEW (PASS)**  
**Recorded Video:** [`demo.webm`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/demo.webm)  

---

## 1. Acceptance Criteria Verification

| # | Acceptance Criterion | Result | Evidence / Details |
|---|---|---|---|
| AC-1 | Officer navigates to Verification Queue and views pending student submissions with metadata, version, and student identity | **PASS** | Loaded `/college/documents` Verification Queue in 1220ms. Displays student name, document title, type code, version, and submission timestamp. Screenshot: [`02-officer-verification-queue.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/02-officer-verification-queue.png) |
| AC-2 | Officer opens Review Modal showing SHA-256 integrity checksum, secure storage path, and verification controls | **PASS** | Modal opened for submission `DEMO-STM-06-US-004 Academic Transcript`. Checksum `e3b0c442...` and storage path displayed for compliance auditing. Screenshot: [`03-review-modal-opened.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/03-review-modal-opened.png) |
| AC-3 | Validation Guardrail: Rejection without mandatory reason (>= 3 chars) is strictly blocked | **PASS** | Submitting reject decision with blank reason flagged error banner: *"Rejection reason (minimum 3 characters) is mandatory when rejecting a document"*. Screenshot: [`04-validation-error-reject-missing-reason.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/04-validation-error-reject-missing-reason.png) |
| AC-4 | Officer approves document with optional verification code and notes; state transitions to APPROVED | **PASS** | Approved `8a7fe622-47b1-4f97-86de-34656d45e1aa` with code `VER-2026-BOARD-OK`. Status badge transitioned to `APPROVED` with success banner. Screenshot: [`05-approval-decision-submitted.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/05-approval-decision-submitted.png) |
| AC-5 | Officer rejects document with mandatory audit reason; state transitions to REJECTED | **PASS** | Rejected `f62be91f-e00f-4b11-8920-b7e6045511d6` with reason *"Seal and issuing tehsildar signature are blurred/illegible"*. Status badge transitioned to `REJECTED`. Screenshot: [`06-rejection-decision-with-reason.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/06-rejection-decision-with-reason.png) |
| AC-6 | Committed persistence across `doc.documents` and `doc.verifications` verified via reload and direct SQL read-back | **PASS** | Browser reload verified persistent badges. Direct PostgreSQL read-back on dev ref `azulbaxszsqxymqmlwfe` confirmed `doc.documents` statuses (`APPROVED` and `REJECTED`) and `doc.verifications` rows with result `VALID` and `INVALID`. Screenshots: [`07-persistence-reload-verified.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/07-persistence-reload-verified.png), [`08-database-committed-rows-verified.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/08-database-committed-rows-verified.png) |
| AC-7 | Student persona experiences immediate status update on `/student/documents` | **PASS** | Student logged in and observed green "Verified" badge for approved transcript and red "Rejected" badge for rejected income certificate. Screenshot: [`09-student-persona-verified-desk.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/09-student-persona-verified-desk.png) |
| AC-8 | RBAC & Negative Permission Cases: Student role blocked from verification endpoints (HTTP 403) | **PASS** | Direct API POST from student session rejected with HTTP 403 Forbidden. Screenshot: [`10-rbac-student-forbidden.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/10-rbac-student-forbidden.png) |
| AC-9 | RFC 7807 Problem Details Error Contract with Reference ID (`traceId`) on all failures | **PASS** | Standard RFC 7807 schema returned with traceId header and zero sensitive data leakage. Screenshot: [`11-foundation-rfc7807-error-contract.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/screenshots/11-foundation-rfc7807-error-contract.png) |
| AC-10 | Warm page load performance target <= 1.5 seconds | **PASS** | Measured warm load time: **1220 ms** (well below 1,500 ms threshold). |

---

## 2. Test Accounts & Personas Used

- **Institutional Officer (College Admin):** `campxsync@gmail.com` (Tenant A: `a0000000-0000-0000-0000-000000000001`, Role: `COLLEGE_ADMIN`)
- **Student 1:** `student1.campx@gmail.com` (Tenant A, User ID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`, Role: `STUDENT`)

---

## 3. Database Committed Record Evidence

- **Database Ref:** `azulbaxszsqxymqmlwfe` (Development ONLY)
- **Approved Document (`DEMO-STM-06-US-004 Academic Transcript - Sem 1-4`):**
  - Table `doc.documents`: ID=`8a7fe622-47b1-4f97-86de-34656d45e1aa`, status=`APPROVED`, current_version_no=1
  - Table `doc.verifications`: result=`VALID`, verification_code=`VER-2026-BOARD-OK`, verified_by=`70a8ffae-b5ad-4c61-a401-16f220893df4`
- **Rejected Document (`DEMO-STM-06-US-004 Income Certificate 2025-2026`):**
  - Table `doc.documents`: ID=`f62be91f-e00f-4b11-8920-b7e6045511d6`, status=`REJECTED`, current_version_no=1
  - Table `doc.verifications`: result=`INVALID`, verified_by=`70a8ffae-b5ad-4c61-a401-16f220893df4`

---

## 4. Screenshot Evidence Index

1. `01-officer-login.png`: Authentication of College Admin persona (`campxsync@gmail.com`).
2. `02-officer-verification-queue.png`: Verification Queue loaded in 1220ms with pending student submissions.
3. `03-review-modal-opened.png`: Review modal displaying document metadata, SHA-256 checksum, and audit storage path.
4. `04-validation-error-reject-missing-reason.png`: Form validation enforcing mandatory rejection reason.
5. `05-approval-decision-submitted.png`: Successful document approval with verification code and auditor notes.
6. `06-rejection-decision-with-reason.png`: Successful document rejection with mandatory reason recorded.
7. `07-persistence-reload-verified.png`: Page reload confirming persistence of APPROVED and REJECTED states.
8. `08-database-committed-rows-verified.png`: Read-only SQL query verifying committed rows in `doc.documents` and `doc.verifications`.
9. `09-student-persona-verified-desk.png`: Student desk showing updated "Verified" and "Rejected" badges.
10. `10-rbac-student-forbidden.png`: Student persona blocked from verification actions with HTTP 403 Forbidden.
11. `11-foundation-rfc7807-error-contract.png`: RFC 7807 problem+json error contract verification.
