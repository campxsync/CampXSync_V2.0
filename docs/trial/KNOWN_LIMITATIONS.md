# CampXSync — Prerequisites, Known Limitations & Operational Constraints

**Document**: `docs/trial/KNOWN_LIMITATIONS.md`  
**Target Environment**: Supabase Staging (`kpwdvvcschankgggitnj`) & Vercel Preview/Live Deployments  
**Release Baseline**: MVP v2.0 (Phase 11 Trial Pack)  

---

## 1. Evaluator Prerequisites

To run the CampXSync trial without interference:

1. **Browser Compatibility**:
   - Modern evergreen desktop or mobile browser: Google Chrome (>= 120), Microsoft Edge (>= 120), Mozilla Firefox (>= 120), or Apple Safari (>= 17).
   - Cookies and Local Storage must be enabled for session token management and client UI preference persistence.
   - JavaScript enabled (React 19 / Next.js 15 client bundle execution).
2. **Network Connectivity**:
   - Outbound HTTPS (port 443) connectivity to Vercel Edge (`https://frontend-murex-one-58.vercel.app`, `https://*.vercel.app`).
   - Outbound HTTPS (port 443) to Supabase Staging API (`https://kpwdvvcschankgggitnj.supabase.co`).
3. **Session Credentials**:
   - No pre-configuration needed. The evaluator may use either:
     - Direct Supabase Auth: `campxsync@gmail.com` with staging password.
     - One-click trial role switch buttons directly on the `/login` landing page.

---

## 2. Documented Known Limitations

These limitations are intentional constraints of the MVP vertical slice scope and safety guardrails:

### 2.1 Scope Boundaries (Out-of-Scope Modules)
- **Examinations (EXM-01..09)**: Examination scheduling, hall-ticket generation, blind valuation, marks finalization, and SGPA/CGPA result publication are intentionally not implemented in this MVP vertical slice. They are fully specified in the architecture archive and prioritized for Phase 4 of the product roadmap.
- **Finance & Fee Ledger (FIN-01..10)**: Fee collection gateways, student receipts, scholarship concessions, and general ledger expense tracking are intentionally not implemented in this MVP vertical slice and are scheduled for Phase 4.
- **Advanced LMS (LMS-01..10)**: Lesson planning, virtual classroom integrations, and student assignment submissions are scheduled for Phase 3. The MVP slice provides the authoritative course and subject master foundation (`acd.courses`, `acd.subjects`).
- **Student Portal**: Student attendance summary is available as a read-only demonstration (`/student`). Full student self-service (grievance filing, elective bidding) is scheduled for post-trial delivery.

### 2.2 Staging Safety Write Boundaries
- **Tenant Provisioning Dry-Run Default**:
  - In the Vercel trial environment, executing the 5-step Institute Onboarding Wizard (`/platform/institutes/new`) runs in **Dry-Run Simulation Mode** by default.
  - This is a deliberate safety measure to prevent arbitrary database clutter in the shared staging project. Real PostgreSQL writes for new tenancies require the explicit owner approval header (`x-provision-approval: GO PROVISION-TENANT`).

### 2.3 Academic Session Concurrency & Locking Rules
- **One Session Per Timetable Slot**:
  - A timetable slot can only have one active attendance session at any given time. Attempting to create a duplicate session for an already open slot returns the existing session (`isExisting: true`), preventing ledger duplicate anomalies.
- **Immutable Ledger Lock**:
  - Once faculty clicks "Confirm & Submit to Ledger", the session transitions to `SUBMITTED` status and is permanently locked against further faculty edits (HTTP 409 Conflict on write attempts).
  - Any subsequent adjustment requires administrative intervention via the Audited Correction Drawer.

### 2.4 Administrative Correction Rules
- **Optimistic Concurrency Control**:
  - Attendance corrections require College Admin authorization and must transmit the current `expectedVersion`. If another administrator modified the record concurrently, the API rejects the request with HTTP 409 Conflict.
- **Mandatory Justification Reason**:
  - All corrections require a textual reason of at least 5 characters (e.g., *"Medical certificate verified by HOD"*). Shorter or blank justifications are rejected with HTTP 400 Bad Request.

### 2.5 Architecture Evolution (Vercel vs Localhost Java Services)
- The local Spring Boot microservices (`services/*`) and API Gateway (`localhost:8080`) are retained in the repository for on-premise container deployments. On Vercel, all client requests are serviced serverlessly via Next.js Edge Middleware and Route Handlers directly interfacing with Supabase Staging PostgreSQL. The local Java services are not needed or executed during cloud trial evaluation.

---

## 3. Genuine Outstanding Blockers

- **Blocker Status**: **ZERO BLOCKERS (0)**.
- All 11 phases of the MVP delivery roadmap have passed their respective quality, security, visual QA, and deployed acceptance gates:
  - Database schema and RLS policies verified on Supabase Staging (`kpwdvvcschankgggitnj`).
  - Dual-tenant isolation verified with zero cross-tenant data leakage.
  - Vercel live preview and alias deployments accessible and healthy.
  - Automated Playwright browser acceptance suite: **10/10 PASS**.
  - Automated security & IDOR regression suite: **36/36 PASS**.
