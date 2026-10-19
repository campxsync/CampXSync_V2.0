# CampXSync — 5-Minute Evaluator & Stakeholder Demo Script

**Document**: `docs/trial/DEMO_SCRIPT.md`  
**Target Environment**: Live Vercel Deployment ([https://frontend-murex-one-58.vercel.app](https://frontend-murex-one-58.vercel.app))  
**Target Audience**: Institution Deans, Registrars, IT Directors & Technical Evaluators  
**Total Running Time**: Exactly 5 Minutes  

---

## Demo Timeline Overview

```
[0:00] ─── Intro & Auth ─── [0:45] ─── Platform Admin ─── [1:45] ─── College Admin ─── [2:45] ─── Faculty Attendance ─── [3:45] ─── Audited Corrections ─── [4:30] ─── Preferences & Wrap-up ─── [5:00]
```

---

## Detailed Minute-by-Minute Script

### [0:00 - 0:45] Section 1: Executive Introduction & Brand Architecture
- **Screen**: Navigate to [https://frontend-murex-one-58.vercel.app/login](https://frontend-murex-one-58.vercel.app/login).
- **Spoken Narrative**:
  > *"Welcome to CampXSync. Higher education institutions today struggle with fragmented spreadsheets, unreliable attendance capture, and weak compliance trails. CampXSync is an enterprise operating system built from the ground up on single-origin multi-tenancy and PostgreSQL Row-Level Security.*
  > 
  > *Notice our login interface: it follows our calm, trustworthy Section 5 enterprise design system with WCAG AA compliance. For this evaluation, we can sign in using live Supabase Auth credentials or use our one-click role shortcuts. Let's start with the Faculty Member experience."*
- **Actions**:
  1. Point out the Navy-950 brand panel highlighting timetable validation and immutable audit trails.
  2. Click **"Faculty (Dr. Robert Vance)"** on the demo role switcher.
  3. Land seamlessly on the Faculty Workspace (`/faculty`).

---

### [0:45 - 1:45] Section 2: Faculty Workspace & Real-Time Attendance Capture
- **Screen**: `/faculty` and `/faculty/attendance`.
- **Spoken Narrative**:
  > *"As Dr. Vance, a computer science professor, my daily teaching schedule is right in front of me. Every class session is tied directly to an authoritative timetable slot, eliminating phantom sessions or accidental duplicate entries.*
  > 
  > *Let's take attendance for CS301 - Data Structures & Algorithms. Clicking 'Take Attendance' loads the verified student roster for Batch 2024-CSE-A.*
  > 
  > *Notice the ergonomics: 48px touch targets for mobile tablets, and full keyboard navigation. I can use the arrow keys to move down the roster, and tap 'P', 'A', or 'L' to mark Present, Absent, or Leave. Notice the sticky bottom bar updating in real time: 38 Present, 2 Absent, 0 Unmarked.*
  > 
  > *When I click 'Submit Attendance', the system presents a formal confirmation dialog informing me that submission commits directly to the institutional ledger and locks the session. I click 'Confirm & Submit to Ledger'. The session transitions to read-only locked status. Even if I reload the page, the lock is authoritative and permanent."*
- **Actions**:
  1. Click **"Take Attendance"** on today's active slot.
  2. Mark 2 students as Absent using the red `A` button or pressing `A` on keyboard.
  3. Highlight the sticky summary bar counter updating live.
  4. Click **"Submit Attendance"** and confirm in the modal.
  5. Reload the browser to prove the read-only locked state persists from PostgreSQL.

---

### [1:45 - 2:45] Section 3: College Administration & Institutional Roster Master
- **Screen**: Use topbar menu to switch to **"College Administrator"** (`/college`).
- **Spoken Narrative**:
  > *"Now let's switch to the institutional perspective as a College Administrator for Delhi Institute of Technology.*
  > 
  > *Our executive dashboard displays live, authoritative metrics queried directly from Supabase Staging: 49 enrolled students, 12 active faculty, and an overall institutional attendance rate of 95.1%. Below, Recharts displays our 14-day attendance trend alongside departmental distributions.*
  > 
  > *In the sidebar, College Admins manage the institutional hierarchy under strict tenant boundaries. Under 'Departments & Programs', we see degree programs like B.Tech CSE; under 'Faculty' and 'Students', we have full roster management with tenant-scoped soft-delete protection."*
- **Actions**:
  1. Click the role switcher to sign in as **College Admin**.
  2. Point out the live KPI summary cards and the 14-day interactive Recharts graph.
  3. Briefly open `/college/departments` to show academic hierarchy.
  4. Briefly open `/college/students` to show the enrolled student body.

---

### [2:45 - 3:45] Section 4: Statutory Shortage Analytics (<75%) & Audited Corrections
- **Screen**: Navigate to `/college/attendance`.
- **Spoken Narrative**:
  > *"Here is where CampXSync provides true academic governance. University regulations in India mandate a statutory minimum of 75% attendance for examination eligibility.*
  > 
  > *Clicking our 'Shortage Alert (<75%)' filter instantly highlights students at risk of debarment, without complex manual calculations.*
  > 
  > *What happens when a student submits a valid medical certificate or dean's permission for an absence? In legacy systems, this is where data corruption happens. In CampXSync, faculty cannot edit locked records, but administrators have an Audited Correction Drawer.*
  > 
  > *Let's click 'Correct Attendance' for a student. The drawer slides out showing their record at version 3. I change their status from Absent to 'On Duty' and enter a mandatory reason: 'Medical certificate verified by HOD'.*
  > 
  > *When I save, the system enforces optimistic concurrency locking: it verifies the expected version, increments the record to version 4, and writes immutable change events to our institutional audit ledger."*
- **Actions**:
  1. Click the **"Shortage Alert (<75%)"** filter pill.
  2. Click **"Correct Attendance"** on an absent student.
  3. Change status to `ON_DUTY`.
  4. Enter justification: *"Medical certificate verified by HOD on 16 Oct 2026"*.
  5. Click **"Save Correction"** and point out the updated version badge (`v4`) and success toast.

---

### [3:45 - 4:30] Section 5: Platform Multi-Tenant Operator & Security Isolation
- **Screen**: Use role switcher to sign in as **"Platform Administrator"** (`/platform`).
- **Spoken Narrative**:
  > *"Finally, let's step out into the shoes of the SaaS Platform Operator governing the entire CampXSync ecosystem.*
  > 
  > *From `/platform`, we monitor tenancies across campuses. Notice our architectural guarantee: Platform Administrators see macro tenant counts and system roles, but Row-Level Security strictly forbids platform operators from reading student personal rows or faculty payrolls.*
  > 
  > *Under 'Institutes', we can launch our 5-step Tenant Onboarding Saga, which provisions new tenancies, seeds 30 system roles, maps 2,138 permissions, and provisions initial college administrators with full idempotency."*
- **Actions**:
  1. Switch to **Platform Admin**.
  2. Point out the cross-tenant aggregate statistics.
  3. Open `/platform/institutes` and show the registry search and filter tabs.
  4. Click **"Onboard New Institute"** and showcase the 5-step onboarding wizard.

---

### [4:30 - 5:00] Section 6: Workspace Preferences, Accessibility & Wrap-Up
- **Screen**: Open the Settings Gear modal in the top header.
- **Spoken Narrative**:
  > *"Every user across all roles can personalize their workspace through our accessible Preferences system. We can toggle light and dark themes, adjust table density from comfortable to compact, set default date ranges, and enable high-contrast accessibility.*
  > 
  > *These settings aren't just saved in browser cookies—they synchronize authoritatively to PostgreSQL in `iam.user_profiles.preferences` so that faculty preferences follow them across desktop, tablet, and mobile.*
  > 
  > *To summarize: CampXSync MVP v2.0 is live on Vercel, connected to Supabase Staging, verified across 36 security gates and 46 visual QA checks, and ready for institutional trial. Thank you."*
- **Actions**:
  1. Open the Preferences modal.
  2. Toggle Density to "Compact", click "Save Preferences".
  3. Conclude the demonstration.
