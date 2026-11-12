# Demo Report: STM-06-US-002 Define Mandatory Documents for Programmes and Stages

**Story ID:** STM-06-US-002  
**Module:** Student Documents Service (`document-service` on port 8098)  
**Execution Date:** 12 November 2026  
**Status:** **READY FOR REVIEW (PASS)**  
**Recorded Video:** [`demo.webm`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/demo.webm)  

---

## 1. Acceptance Criteria Verification

| # | Acceptance Criterion | Result | Evidence / Details |
|---|---|---|---|
| AC-1 | College Admin / Registrar defines mandatory & optional stage document requirements with academic stage, document type, programme scope, counts, and submission deadline | **PASS** | Successfully defined requirement `DEMO-STM-06-US-002` for `EXAMINATION` stage under B.Tech CSE with 7-day deadline. Screenshot: [`04-stage-requirement-created.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/04-stage-requirement-created.png) |
| AC-2 | Duplicate requirement prevention (unique per stage + doc_type + program) enforced with HTTP 409 Conflict | **PASS** | Attempting to define existing `ADMISSION` + `DOC-TRANSCRIPT` returned 409 Conflict: *"A requirement for this document type and academic stage already exists in this scope"*. Screenshot: [`03-validation-duplicate-rejected.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/03-validation-duplicate-rejected.png) |
| AC-3 | Negative Permission Test: Non-administrative roles (Student) blocked with HTTP 403 Forbidden | **PASS** | Student persona (`student1.campx@gmail.com`) attempted mutation and received 403 Forbidden: *"Forbidden: College Admin or Registrar role required"*. Screenshot: [`07-student-access-denied.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/07-student-access-denied.png) |
| AC-4 | Data persisted in PostgreSQL (`doc.stage_document_requirements`) with tenant isolation and verified on UI reload | **PASS** | Reloading page verified persisted row. Read-only PostgreSQL query confirmed committed row ID `da9e3bfb-5408-41a4-8178-90cf0c3e484b` on dev ref `azulbaxszsqxymqmlwfe`. Screenshot: [`05-persisted-reload-verified.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/05-persisted-reload-verified.png), [`06-database-readback-verified.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/06-database-readback-verified.png) |
| AC-5 | Warm page load performance target <= 1.5 seconds | **PASS** | Warm page load measured at **1227 ms** (well below 1,500 ms SLA). Screenshot: [`02-stage-requirements-desk.png`](file:///D:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/screenshots/02-stage-requirements-desk.png) |

---

## 2. Test Accounts & Personas Used

- **College Admin:** `campxsync@gmail.com` (Tenant A: `a0000000-0000-0000-0000-000000000001`, Role: `COLLEGE_ADMIN`)
- **Student (Negative Test):** `student1.campx@gmail.com` (Tenant A, Role: `STUDENT`)

---

## 3. Database Committed Record Evidence

- **Table:** `doc.stage_document_requirements`
- **Database Ref:** `azulbaxszsqxymqmlwfe`
- **Record ID:** `da9e3bfb-5408-41a4-8178-90cf0c3e484b`
- **Academic Stage:** `EXAMINATION`
- **Document Type:** `DOC-NATIONAL-ID` (`fdf88717-6db1-45f8-8e27-8e085304a14b`)
- **Programme Scope:** `DEV-BTECH-CSE` (`a0000000-0000-0000-0000-000000000040`)
- **Mandatory:** `true`
- **Min / Max Count:** `1 / 1`
- **Submission Deadline:** `7 days`
- **Description:** `DEMO-STM-06-US-002: Mandatory National ID verification before Semester Examination`
- **Row Status:** Active (`is_active = true`)

---

## 4. Screenshot Evidence Index

1. `01-college-admin-login.png`: Authentication of College Admin persona.
2. `02-stage-requirements-desk.png`: Loading Documents Desk, measuring warm load time (1227ms).
3. `03-validation-duplicate-rejected.png`: Conflict prevention HTTP 409 rejection banner.
4. `04-stage-requirement-created.png`: Creation of new stage requirement via full architectural path.
5. `05-persisted-reload-verified.png`: Browser reload and filter verifying committed persistence.
6. `06-database-readback-verified.png`: Direct PostgreSQL query confirming database row.
7. `07-student-access-denied.png`: RBAC negative test blocking student mutation with 403 Forbidden.
