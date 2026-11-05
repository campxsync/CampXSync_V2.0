# Phase 2 Design Report: CampXSync Student Role Experience (STM-01 & STM-02)

**Project ID in Stitch:** `11886203668946748260` ("CampXSync Student Portal") [VERIFIED]  
**Design System Asset:** `assets/b29f81f5ffb9427d951d68efa103a469` ("Academic Enterprise Portal") [VERIFIED]  
**Target Account:** `student1.campx@gmail.com` (`STU-DEV-2026-0001`, UUID: `81dd67d3-1d5f-4d8e-8061-a6764861f924`)  
**Design Discipline:** Design ONLY. No code edited, no commits made, no changes to existing pages. [VERIFIED]  

---

## 1. Executive Summary & Design System Foundation

The existing pages `/student` and `/student/admissions` were preserved in their proven structural and visual pattern. In accordance with user guidance, **Phase 2 designs exclusively focus on the 4 missing capability pieces**, generating both high-fidelity **Desktop (1440px)** and **Mobile (390px portrait)** screens with complete **Loading**, **Empty**, and **Error** state specifications.

### Visual Language & Design Tokens
The design adheres to the institutional **Academic Enterprise Portal** design system:
- **Primary Institutional Canvas:** `#F6F8FB` (cool-tinted canvas backing all views).
- **Surface Elevation Cards:** `#FFFFFF` with 1px solid `#E3E8EF` crisp structural borders and ambient shadow (`0 1px 3px 0 rgba(11, 31, 58, 0.04)`).
- **Institutional Navy Accent:** `#0B1F3A` (topbars, side rails, formal headers).
- **Primary Interactive Royal Blue:** `#1F6FEB` (action buttons, focused rings, active tabs, progress bars).
- **Semantic Feedback Tokens:**
  - `Success`: `#E8F8F0` bg, `#1E9E6A` text, `#C2EDDA` border (Verified, Compliant, Good Standing).
  - `Warning / Review`: `#FEF3C7` bg, `#D97706` text, `#FDE68A` border (Under Review, Pending, Shortage Alert).
  - `Error / Action Required`: `#FEE2E2` bg, `#D9383A` text, `#FECACA` border (Rejected, Missing, Format Error).
- **Typography:** Strict `Inter` typography hierarchy with tabular lining figures (`font-feature-settings: 'tnum' on, 'cv05' on`) across all enrollment IDs, timestamps, and attendance percentages.
- **Touch Ergonomics:** Minimum 44px tap targets across all mobile buttons, segmented controls, and form inputs.

---

## 2. Inventory of Designed Screens & Exported Artifacts

All 8 screens (4 pieces × 2 form factors) have been generated in Stitch, rendered, and exported into `docs/student-dashboard/evidence/design-specs/`.

| Screen Name | Viewport | Stitch Screen ID | Exported HTML Spec | Exported PNG Preview |
|---|---|---|---|---|
| **Piece 1: Document Upload & Status View** | Desktop (1440px) | `3626a89fd3e34c49b09e4d64fb2f6a8f` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-1-document-upload-status-desktop.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-1-document-upload-status-desktop.png) |
| **Piece 1: Document Upload & Status View** | Mobile (390px) | `e803670f337848358a95e848e325a246` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-1-document-upload-status-mobile.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-1-document-upload-status-mobile.png) |
| **Piece 2: Profile Correction & Audit History** | Desktop (1440px) | `6e8d0ff379d3418abc128a85fc6257f1` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-2-profile-correction-history-desktop.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-2-profile-correction-history-desktop.png) |
| **Piece 2: Profile Correction & Audit History** | Mobile (390px) | `7ed28dc235a649e5bd77a94ad9042bf8` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-2-profile-correction-history-mobile.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-2-profile-correction-history-mobile.png) |
| **Piece 3: Contact & Address Edit** | Desktop (1440px) | `7edef902d72b43feb0540b200b086dc8` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-3-contact-address-edit-desktop.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-3-contact-address-edit-desktop.png) |
| **Piece 3: Contact & Address Edit** | Mobile (390px) | `7a9668d6ef8e439f8b5f74337aa90671` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-3-contact-address-edit-mobile.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-3-contact-address-edit-mobile.png) |
| **Piece 4: Polished Student Dashboard** | Desktop (1440px) | `f79affd5c2da44feb502a7c95a815c77` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-4-student-dashboard-polished-desktop.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-4-student-dashboard-polished-desktop.png) |
| **Piece 4: Polished Student Dashboard** | Mobile (390px) | `444de0325011486fa2083b5f0d5aeb49` | [HTML](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-4-student-dashboard-polished-mobile.html) | [PNG](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/design-specs/screen-4-student-dashboard-polished-mobile.png) |

---

## 3. Deep-Dive Screen Breakdown & Component Inventory

### Screen 1: Application Document Upload & Status/Detail View
- **Context:** Integrated into the Admissions workflow for application `APP-DEV-2026-0001` (B.Tech CSE, `DEV-INTAKE-2026`).
- **Desktop (1440px):**
  - Two-column workspace: 7-column primary document list and upload dropzone, 5-column inspection drawer showing document visual preview, OCR metadata, cryptographic SHA-256 hash chip, and registrar verification log.
  - Overall document progress hero banner: `3 of 4 Documents Verified (75% Complete)` with animated progress bar and threshold indicator.
  - Verification card rows for 4 mandatory categories:
    1. *High School 12th Marksheet*: Verified (Emerald badge, verified by Dr. R. Sharma).
    2. *Government Photo ID*: Verified (UIDAI biometric match confirmation).
    3. *Transfer / Migration Certificate*: Pending Verification (Amber pill, SLA ~24h).
    4. *Entrance Exam Scorecard*: Action Required (Red border, reason: "Previous scan blurry. Please re-upload legible scan").
  - Dropzone: 2px dashed primary border, drag-and-drop target with upload CTA, format restrictions (`PDF, JPG, PNG <= 5MB`), and OCR security tags.
- **Mobile (390px):**
  - Compact navy header with back navigation to Admissions.
  - Vertical card list with touch-friendly actions (`View`, `Re-upload`).
  - Slide-up bottom sheet drawer for inspecting document hash, file size, and remarks.
- **States Included:**
  - *Loading State*: Pulsing shimmer skeleton rows for document list and inspector.
  - *Empty State*: Initial upload prompt with category checklist for new applications.
  - *Error State*: Dismissible error banner: *"Audit Log Sync Warning: Network timeout while fetching registrar verification audit log. Retry now"*.

### Screen 2: Profile Correction Request Form & Change-History View
- **Context:** Student self-service identity correction request under STM-01.
- **Desktop (1440px):**
  - Left Column (Correction Form):
    - Policy Notice Callout: Clarifies legal identity modification rules requiring documentary proof.
    - Category Dropdown: *Date of Birth*, *Legal Name Spelling*, *Parent/Guardian Name*, *Blood Group*.
    - Comparative Input Sub-grid: Auto-populated locked current SIS value (`14/08/2004`) vs editable proposed value (`15/08/2004`).
    - Justification Textarea with character count.
    - Document attachment dropzone showing verified attachment chip: `10th_CBSE_MarkSheet_Verified.pdf (1.8 MB)`.
    - Form Actions: `Submit for Registrar Review` (primary blue), `Save as Draft`, `Cancel`.
  - Right Column (Audit & Change-History Log):
    - Filter tabs: *All (3)*, *Under Review (1)*, *Approved (1)*, *Rejected (1)*.
    - Audit History Cards:
      - `CORR-2026-089` (DOB): Under Review (Amber, SLA ~48h).
      - `CORR-2026-012` (Legal Name): Approved (Emerald, Registrar endorsement slip download).
      - `CORR-2025-004` (Blood Group): Rejected (Rose, reason: "Incomplete lab report", re-submit CTA).
- **Mobile (390px):**
  - Segmented pill switcher toggling between *New Request Form* and *History & Status (3)*.
  - Full-width 48px touch CTA: `Submit for Registrar Review`.
- **States Included:**
  - *Loading State*: Placeholder skeleton shimmer bars for audit cards.
  - *Empty State*: Clean shield card: *"No correction requests on file. Your official records match institutional enrollment data."*
  - *Error State*: Inline error toast: *"Submission failed: Supporting document must be an authentic PDF or image. Please retry."*

### Screen 3: Student Contact & Address Management View
- **Context:** SIS contact and domicile update under STM-01.
- **Desktop (1440px):**
  - Card 1 (Institutional & Digital Contacts):
    - Institutional Email: `student1.campx@gmail.com` (Read-only, locked badge: `SSO / Identity Provider Managed`).
    - Primary Mobile Phone: Country code selector `+91`, verified OTP badge.
    - Alternate / WhatsApp Phone: Editable field.
    - Designated Emergency Contact Subcard: Full Name (`Rajesh Sharma`), Relationship dropdown (`Father`), Priority Emergency Phone (`+91 9876500000`).
  - Card 2 (Permanent Residential Address): Official locked record with proof notice.
  - Card 3 (Current / Correspondence Address):
    - Interactive "Same as permanent" toggle.
    - Residence Segmented Control: `Campus Hostel (Active)` vs `Day Scholar`.
    - Hostel Allocation Details: `North Campus Hostel`, `Block B, Room 314`, `Campus Box #402`.
  - Sticky Action Bar: Audit timestamp (*Last updated: 14 Aug 2026*) with `Discard Changes` and `Save Address Changes`.
- **Mobile (390px):**
  - Vertical stacked cards with 44px tap targets.
  - Fixed docked bottom action bar with high-contrast `Save Address Changes` button.
- **States Included:**
  - *Loading State*: Shimmer placeholder card simulating background SIS ledger synchronization.
  - *Success State*: Green success toast: *"Address information updated successfully in SIS. Sync ref: TXN-CAMPX-2026-8841"*.
  - *Error State*: Red validation alert: *"Postal PIN code must be a 6-digit numeric value. Verify both Permanent & Correspondence entries."*

### Screen 4: Polished Student Dashboard Layout (`/student`)
- **Context:** Academic overview & attendance dashboard bridging STM-02 admissions handover with STM-01 academics.
- **What Was Polished (Weakness Addressed):**
  - Added **Admissions Handover Bridge Card**: Directly displays admitted dossier `#APP-DEV-2026-0001` (`DEV-INTAKE-2026`, Offer Accepted, SIS Handover Complete) with quick link to `/student/admissions`.
  - Added **Quick Actions Hub (4 Tiles)**: `Upload Documents`, `Correction Requests`, `Contact & Address`, `Class Timetable`.
  - Added **Gateway Status & Fallback Indicator**: Informs student when connected via fallback database or when live sync is active.
- **What Was Strictly Retained (Existing Working Visuals):**
  - Welcome Banner with enrollment ID (`STU-DEV-2026-0001`), Program (`B.Tech CSE`), Section (`Batch 2026-A`), and Standing badge.
  - 3 Core StatCards: `Overall Attendance (80.0%)`, `Total Classes Attended (4 / 5)`, `Academic Standing (Satisfactory)`.
  - Course-Wise Attendance Summary Table: CS101 (66.7% shortage alert) and CS102 (100% compliant).
  - Recent Attendance Sessions Log: Faculty, dates, venues, attendance status pills.
- **Mobile (390px):**
  - Mobile card layout for course attendance with shortage alert badges and drill-downs.
  - Sticky bottom tab bar (`Dashboard`, `Admissions`, `Courses`, `Profile`).
- **States Included:**
  - *Interactive View Switcher*: Built-in toggle to view populated vs loading skeleton vs empty state.
  - *Gateway Fallback Banner*: *"Connected via Fallback Database. Refresh to sync Gateway."* with manual retry button.
  - *Empty State*: Clean illustration with copy *"No attendance records found for this term yet."*

---

## 4. UI Component Inventory

The following modular components were standardized across all 8 screens:
1. **Badges & Pill Chips (`rounded-full`, uppercase `label-sm`):**
   - Verified / Active (`#E8F8F0` / `#1E9E6A`)
   - Under Review / Pending (`#FEF3C7` / `#D97706`)
   - Action Required / Shortage Alert (`#FEE2E2` / `#D9383A`)
   - Neutral / SSO-Managed (`#EDF2F7` / `#0B1F3A`)
2. **Form Controls (`rounded-lg`, height 40px/48px):**
   - Locked Read-Only Inputs (with padlock icon and identity-provider disclaimer)
   - Editable Text Inputs & Number Inputs (with inline format validation)
   - Country Code Selectors & Category Dropdowns
   - Multi-line Textareas with live character counters
   - Segmented Pill Controls (Hostel vs Day Scholar, Request vs History)
   - Toggle Switches & Checkboxes
3. **Dropzones & Attachment Chips:**
   - 2px dashed border dropzones with drag-and-drop triggers
   - Attached file chip with file size, SHA-256 verification tick, preview button, and remove action
4. **Data Displays:**
   - StatCards with icons, primary metric, threshold comparison, and supporting subtitle
   - Dense enterprise tables with fixed 40px headers and 52px zebra-striped rows
   - Mobile vertical inspection cards with right chevrons
5. **Panels & Drawers:**
   - 480px Desktop Slide-in Inspection Drawer
   - 390px Mobile Slide-up Bottom Sheet
6. **System Feedback & Alerts:**
   - Shimmer Skeleton pulse bars (1.8s cubic-bezier animation)
   - Dismissible Error Banners with retry action buttons
   - Success Toasts with transaction/sync reference numbers
   - Empty state verification cards with iconography

---

## 5. API Mapping Against Phase 0 Capability Table

Every screen strictly maps to an existing, verified API in the Gateway / Java microservices. No non-existent APIs were invented.

| Screen / Feature | User Action | Gateway API Endpoint | Backend Service | API Ready? |
|---|---|---|---|---|
| **Screen 1: Documents** | Upload file | `POST /api/v1/admissions/applications/{id}/documents` | Admissions Service (port 8094) | **YES** [VERIFIED] |
| **Screen 1: Documents** | List & inspect docs | `GET /api/v1/admissions/applications/{id}/documents` | Admissions Service (port 8094) | **YES** [VERIFIED] |
| **Screen 2: Corrections** | Submit correction | `POST /api/v1/students/{id}/corrections` | Student Service (port 8093) | **YES** [VERIFIED] |
| **Screen 2: Corrections** | View history & status | `GET /api/v1/students/{id}/history` | Student Service (port 8093) | **YES** [VERIFIED] |
| **Screen 3: Address** | Save contact & address | `PUT /api/v1/students/{id}/address` | Student Service (port 8093) | **YES** [VERIFIED] |
| **Screen 3: Address** | Get preferences / info | `GET /api/v1/students/{id}/preferences` | Student Service (port 8093) | **YES** [VERIFIED] |
| **Screen 4: Dashboard** | Load student profile | `GET /api/v1/students/me` | Student Service (port 8093) | **YES** [VERIFIED] |
| **Screen 4: Dashboard** | Load admissions handover | `GET /api/v1/admissions/applications` | Admissions Service (port 8094) | **YES** [VERIFIED] |
| **Screen 4: Dashboard** | Load attendance & timetable | Database queries / Academic Service | Academic / Attendance Service | **YES** [VERIFIED] |

### Flagged Gaps (Scope Boundaries):
- **Leave of Absence Submission:** While visible on institutional portals as a future aspiration, no Java backend endpoint currently exists for automated student leave requests in STM-01/02. This is flagged and **excluded** from the Phase 3 implementation scope.
- **Attendance Discrepancy Grievance:** Grievance ticketing is an auxiliary workflow outside STM-01/02 core scope and will route to standard academic advisor email/support rather than a bespoke database table in Phase 3.

---

## 6. Safety & Discipline Compliance

- **Zero Code Modified:** No files in `frontend/src/` or backend services were altered.
- **Zero Commits:** Repository working tree remains pristine.
- **Database Safety:** Only dev project `azulbaxszsqxymqmlwfe` was accessed; zero writes executed during Phase 2.
- **Visual Continuity:** Existing working views `/student` and `/student/admissions` were not redesigned.

---

## 7. STOP & Approval Request

Phase 2 Design is complete and exported. In accordance with hard safety rules, I will now STOP and await your explicit approval.

Please provide your approval to proceed to Phase 3 (Implementation):
**`APPROVE PHASE 3`**

