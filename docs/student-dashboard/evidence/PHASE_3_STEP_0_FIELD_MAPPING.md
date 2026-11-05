# Phase 3 Step 0: Field-to-Source Mapping & API Realism Audit

**Target Account:** `student1.campx@gmail.com` (`STU-DEV-2026-0001`, UUID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`)  
**Scope:** Strict field-to-source mapping for all student UI screens before any coding.  
**Rule Compliance:** No hard-coded sample data. Drop all unmapped mock fields. No backend changes. No link to non-existent pages.  

---

## 1. Field-to-Source Mapping Table

### Screen 1: Application Documents (Upload & Status) — Scheduled for Phase 3b

| UI Element | DTO Field or DB Column | API Endpoint & Method | Backend Service | Exists? | Action / Decision |
|---|---|---|---|---|---|
| **Application ID** | `stu.applications.id` / `application_number` | `GET /api/v1/admissions/applications` | Admissions Service (8094) | **YES** | Kept. Binds to live student application (`APP-DEV-2026-0001`). |
| **Program Name** | `acd.programs.name` / `code` | `GET /api/v1/admissions/applications` | Admissions Service (8094) | **YES** | Kept (`B.Tech Computer Science & Engineering`). |
| **Intake Name** | `stu.admission_intakes.name` | `GET /api/v1/admissions/intakes/active` | Admissions Service (8094) | **YES** | Kept (`DEV-INTAKE-2026`). |
| **Application Status** | `stu.applications.status` | `GET /api/v1/admissions/applications` | Admissions Service (8094) | **YES** | Kept (`HANDED_OVER`). |
| **Document ID** | `ApplicationDocumentEntity.id` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Unique identifier per document. |
| **Document Type** | `ApplicationDocumentEntity.documentType` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept (`MARKSHEET_10`, `MARKSHEET_12`, `ID_PROOF`, `TRANSFER_CERT`, `ENTRANCE_SCORECARD`). |
| **File Name** | `ApplicationDocumentEntity.fileName` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Shows authentic uploaded filename. |
| **File Size (Bytes)** | `ApplicationDocumentEntity.fileSizeBytes` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Formatted to KB / MB in UI. |
| **MIME Type** | `ApplicationDocumentEntity.mimeType` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept (`application/pdf`, `image/jpeg`). |
| **Verification Status** | `ApplicationDocumentEntity.verificationStatus` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Enum: `PENDING`, `VERIFIED`, `REJECTED`. |
| **Rejection Reason** | `ApplicationDocumentEntity.rejectionReason` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Displayed only when status is `REJECTED`. |
| **Verified Timestamp** | `ApplicationDocumentEntity.verifiedAt` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. ISO timestamp formatted to locale string. |
| **Upload Timestamp** | `ApplicationDocumentEntity.createdAt` | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (8094) | **YES** | Kept. Formatted to locale string. |
| **UIDAI / NAD Stamps** | *None* | *None* | *None* | **NO** | **DROPPED.** Unmapped external authority stamp. |
| **SHA-256 Hash Chip** | *None* | *None* | *None* | **NO** | **DROPPED.** Not stored in `stu.application_documents`. |
| **Registrar Verification Log** | *None* | *None* | *None* | **NO** | **DROPPED.** No document audit sub-table exists. |
| **Automated OCR Score** | *None* | *None* | *None* | **NO** | **DROPPED.** No OCR extractor in STM-02. |

---

### Screen 2: Profile Correction Requests & History — Phase 3a

| UI Element | DTO Field or DB Column | API Endpoint & Method | Backend Service | Exists? | Action / Decision |
|---|---|---|---|---|---|
| **Target Field Dropdown** | `SubmitCorrectionRequest.targetField` | `POST /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept (`legalName`, `dateOfBirth`, `gender`, `primaryEmail`). |
| **Current Value (Locked)** | `StudentProfileResponse` fields | `GET /api/v1/students/me` | Student Service (8093) | **YES** | Kept. Read-only value populated from live student profile. |
| **Proposed Value Input** | `SubmitCorrectionRequest.proposedValue` | `POST /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. User input. |
| **Correction Reason** | `SubmitCorrectionRequest.reason` | `POST /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. Multi-line justification. |
| **Request ID** | `StudentCorrectionRequestEntity.id` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. Unique request identifier. |
| **Request Target Field** | `StudentCorrectionRequestEntity.targetField` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. |
| **Old Value (Audit)** | `StudentCorrectionRequestEntity.oldValue` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. |
| **New Value (Audit)** | `StudentCorrectionRequestEntity.newValue` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. |
| **Request Status** | `StudentCorrectionRequestEntity.status` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. Enum: `PENDING`, `APPROVED`, `REJECTED`. |
| **Status Filter Tabs** | `StudentCorrectionRequestEntity.status` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | **CONFIRMED & KEPT.** Status is returned by API; tabs filter by status (`All`, `Pending`, `Approved`, `Rejected`). |
| **Decision Remarks** | `StudentCorrectionRequestEntity.decisionReason` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. Shows reviewer feedback. |
| **Decided Timestamp** | `StudentCorrectionRequestEntity.decidedAt` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. |
| **Submission Timestamp** | `StudentCorrectionRequestEntity.createdAt` | `GET /api/v1/students/{id}/corrections` | Student Service (8093) | **YES** | Kept. |
| **Change History Action** | `ProfileChangeHistoryDto.action` | `GET /api/v1/students/{id}/history` | Student Service (8093) | **YES** | Kept (`STUDENT_ENROLLED`, `PROFILE_UPDATED`, etc.). |
| **Change History Summary**| `ProfileChangeHistoryDto.summary` | `GET /api/v1/students/{id}/history` | Student Service (8093) | **YES** | Kept. Human-readable audit line. |
| **Change History Timestamp**| `ProfileChangeHistoryDto.timestamp` | `GET /api/v1/students/{id}/history` | Student Service (8093) | **YES** | Kept. |
| **Proof-Upload on Corrections** | *None* | `SubmitCorrectionRequest` only accepts `evidenceDocumentId: UUID` | Student Service (8093) | **NO** | **DROPPED.** Form does not support direct multipart file upload. |
| **Download Endorsement Slip** | *None* | *None* | *None* | **NO** | **DROPPED.** No PDF generator endpoint exists. |
| **SLA Countdown Timer** | *None* | *None* | *None* | **NO** | **DROPPED.** Administrative estimate, not an API field. |

---

### Screen 3: Contact & Address Management — Phase 3a

| UI Element | DTO Field or DB Column | API Endpoint & Method | Backend Service | Exists? | Action / Decision |
|---|---|---|---|---|---|
| **Student SSO Email** | `core.persons.primary_email` | `GET /api/v1/students/me` | Student Service (8093) | **YES** | Kept. Read-only (`student1.campx@gmail.com`). |
| **Primary Phone** | `core.persons.primary_phone` | `GET /api/v1/students/me` | Student Service (8093) | **YES** | Kept (`+91 9876543210`). |
| **Address Type** | `StudentAddressRequest.addressType` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept (`PERMANENT` or `CURRENT`). |
| **Address Line 1** | `StudentAddressRequest.line1` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **Address Line 2** | `StudentAddressRequest.line2` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **Landmark** | `StudentAddressRequest.landmark` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **City** | `StudentAddressRequest.city` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **District** | `StudentAddressRequest.district` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **State Code** | `StudentAddressRequest.stateCode` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept. |
| **Postal Code** | `StudentAddressRequest.postalCode` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept (6-digit numeric validation). |
| **Country Code** | `StudentAddressRequest.countryCode` | `PUT /api/v1/students/{id}/address` | Student Service (8093) | **YES** | Kept (`IND`). |
| **Communication Channels**| `StudentPreferencesDto.communicationChannels` | `GET/PUT /api/v1/students/{id}/preferences` | Student Service (8093) | **YES** | Kept (`EMAIL`, `SMS`). |
| **Hostel / Room Allocation**| *None* | *None* | *None* | **NO** | **DROPPED.** No campus housing table in STM-01/02. |
| **Residence Segmented Control**| *None* | *None* | *None* | **NO** | **DROPPED.** Unmapped in `core.addresses`. |
| **Emergency Contact Editing** | *None* | Address endpoint does not accept contacts | Student Service (8093) | **NO** | **DROPPED from address edit form.** Guardian records are staff-managed via `POST /api/v1/students/{id}/guardians`. |
| **OTP Verification Badge** | *None* | *None* | *None* | **NO** | **DROPPED.** No SMS OTP gateway. |
| **Alternate / WhatsApp Phone** | *None* | `core.persons` only has `primary_phone` | Core / IAM | **NO** | **DROPPED.** Unmapped field. |

---

### Screen 4: Polished Student Dashboard Layout (`/student`) — Phase 3a

| UI Element | DTO Field or DB Column | API Endpoint & Method | Backend Service | Exists? | Action / Decision |
|---|---|---|---|---|---|
| **Welcome Header** | `core.persons.first_name + last_name` | `GET /api/v1/students/me` / DB fallback | Student Service (8093) | **YES** | Kept as currently working (`Student One`). |
| **Enrollment Number** | `stu.students.enrollment_no` | `GET /api/v1/students/me` / DB fallback | Student Service (8093) | **YES** | Kept (`STU-DEV-2026-0001`). |
| **Program & Section** | `acd.programs.name`, `acd.batches.name` | Gateway / DB fallback | Academic Service | **YES** | Kept (`B.Tech CSE`, `BATCH-2026-A`). |
| **Overall Attendance %** | Computed (`attended / conducted`) | `acd.attendance_records` + sessions | Academic / Attendance Service | **YES** | Kept (`80.0%`). |
| **Classes Attended** | Count of sessions | `acd.attendance_records` | Attendance Service | **YES** | Kept (`4 / 5`). |
| **Academic Standing** | Threshold compliance | Calculated (<75% threshold) | UI Logic | **YES** | Kept (`Satisfactory`). |
| **Course Summary Table** | Subject code, name, conducted, attended, absent, % | DB query / Attendance Service | Academic / Attendance Service | **YES** | Kept (CS101: 66.7%, CS102: 100%). |
| **Recent Sessions Log** | Date, subject, faculty, status | DB query / Attendance Service | Attendance Service | **YES** | Kept (Oct 25 Present, Oct 24 Present, Oct 23 Absent). |
| **Admissions Handover Bridge Card** | `stu.applications` (`id`, `status`), `intake` | `GET /api/v1/admissions/applications` | Admissions Service (8094) | **YES** | **KEPT.** Displays application status (`HANDED_OVER`) with CTA linking to `/student/admissions`. |
| **Quick Action: Contact & Address** | Navigation link | In-app link to `/student/address` | Frontend Page | **YES (3a)** | Kept. |
| **Quick Action: Correction Requests** | Navigation link | In-app link to `/student/corrections` | Frontend Page | **YES (3a)** | Kept. |
| **Quick Action: Admissions Dossier** | Navigation link | In-app link to `/student/admissions` | Frontend Page | **YES** | Kept. |
| **Fallback Database Banner** | System alert | System state | *None* | **NO** | **REMOVED COMPLETELY.** per explicit user instruction. |
| **Quick Action: Class Timetable** | *None* | Page does not exist | *None* | **NO** | **DROPPED.** Unmapped page link. |
| **Courses & Academic Records Links** | *None* | Pages do not exist | *None* | **NO** | **DROPPED.** Unmapped page links. |
| **Leave of Absence CTA** | *None* | *None* | *None* | **NO** | **DROPPED.** No student self-service leave endpoint. |
| **Report Session Discrepancy Modal**| *None* | *None* | *None* | **NO** | **DROPPED.** Grievance ticketing not backed by API. |

---

## 2. Confirmation on Correction Requests Status Endpoint

**Question:** Does an endpoint list the student's own correction requests with status?  
**Result:** **YES `[VERIFIED]`**.  
- **Endpoint:** `GET /api/v1/students/{id}/corrections`  
- **Authorization:** `StudentDomainService.getCorrectionRequests` executes `enforceReadAccess(context, student)`, verifying that an authenticated student can query their own correction requests.  
- **Underlying Source:** `doc.lifecycle_requests` where `entity_type = 'STUDENT' AND entity_id = ? AND request_type = 'CORRECTION'`.  
- **Status Field:** Returns `status` column with values `'PENDING'`, `'APPROVED'`, `'REJECTED'`.  
- **Decision:** The status filter tabs (`All`, `Pending`, `Approved`, `Rejected`) **ARE retained** because the endpoint natively returns the status field.

---

## 3. Proposed Additive-Only Navigation Entries (`nav-config.ts`)

For Phase 3a, the proposed additive entries for `student` in `frontend/src/lib/nav-config.ts` are:

```ts
student: [
  { title: 'My Attendance', href: '/student', icon: ClipboardCheck },
  { title: 'Admissions & Applications', href: '/student/admissions', icon: FileText },
  // Proposed Additive Entries for Phase 3a approval:
  { title: 'Contact & Address', href: '/student/address', icon: Building2 },
  { title: 'Profile Corrections', href: '/student/corrections', icon: History },
],
```

*Platform Admin, College Admin, and Faculty arrays remain completely untouched. No links to unbuilt pages (`/timetable`, `/courses`, `/profile`) are added.*

---

## 4. Phase 3a Execution Scope & Stop Boundary

- **Phase 3a Deliverables:**
  1. `/student/address`: Student contact & address edit form (permanent/current address, postal code validation, communication preferences) wired to Gateway `PUT /api/v1/students/{id}/address` and `GET /api/v1/students/me`.
  2. `/student/corrections`: Correction request form (`targetField`, `proposedValue`, `reason`) wired to `POST /api/v1/students/{id}/corrections`, plus correction history table with status tabs (`PENDING`, `APPROVED`, `REJECTED`) wired to `GET /api/v1/students/{id}/corrections`, and audit log timeline wired to `GET /api/v1/students/{id}/history`.
  3. `/student` Dashboard Polish: Retaining existing attendance KPI cards and course summary tables while integrating the Admissions Handover Bridge Card (linking to `/student/admissions`) and Quick Action links. Fallback database banner removed.
- **Phase 3b Scope (Deferred to separate step):**
  - Application documents read-only view and tagged seed.
- **Stop Boundary:** Complete 3a implementation and STOP for review before any Phase 3b work or commits.

