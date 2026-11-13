> **SUPERSEDED:** This prompt file is superseded by `docs/handover/NEXT_TASKS_FOR_CURSOR.md`. Feature development is paused; execute the 6-Step Launch Track for Cursor before resuming backlog stories.

# Next Antigravity Task — STM-04-US-017: Parent / Guardian Visibility into Student Academic Standing & Leaves

**Target Repository:** `D:\CampXSync\CampXSync_V2.0`  
**Governing Documents:** `.agent/rules/campxsync-wave-control.md`, `docs/project-control/PROGRESS.md`, `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`  
**Current Branch:** `feature/java-backend-integration`  
**Development Database Ref:** `azulbaxszsqxymqmlwfe` (Supabase PostgreSQL)  

---

## 1. Session Checkpoint & State Summary

1. **Active Services & Ports (Stack Supervisor `task-6029`):**
   - **Frontend (Next.js 15):** `http://localhost:3000` (Healthy)
   - **API Gateway:** `http://localhost:8080` (Healthy)
   - **Lifecycle Service (STM-04):** `http://localhost:8096` (Healthy)
   - **Document Service (STM-06):** `http://localhost:8098` (Healthy)

2. **Completed in Last Session (`STM-04-US-003`):**
   - **Head of Department (HOD) Endorsement & Departmental Review of Student Leave Petitions**:
     - Java domain service methods: `listAdviseeRequests()` and `endorseRequest()` with minimum 5-character remarks validation.
     - Commits immutable history record to `stu.lifecycle_history` (`action = 'hod.endorsement:<RECOMMENDED|CONDITIONALLY_RECOMMENDED|NOT_RECOMMENDED>'`, `status = 'PENDING'`, `actor_role = 'FACULTY'`).
     - Appends HOD endorsement remarks summary to `stu.lifecycle_requests.reason`.
     - Gateway endpoints registered: `GET /api/v1/lifecycle/advisor/requests` and `POST /api/v1/lifecycle/requests/{id}/endorse`.
     - Frontend desks updated: Faculty Advisees Desk (`/faculty/advisees`) with endorsement modal, and Registrar Lifecycle Review Desk (`/college/lifecycle`) with green `HOD Endorsed` badge reflection and transition prerequisite validation.
     - Verified: 11/11 Java unit tests PASS, frontend `tsc --noEmit` 0 errors, 16/16 visible Playwright demo checkpoints PASS ([`docs/demos/STM-04-US-003/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-04-US-003/demo.webm)).
     - Appended to [STM_IMPLEMENTATION_TRACEABILITY.csv](file:///d:/CampXSync/CampXSync_V2.0/docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv) (185 items total).

---

## 2. Authorized Next Story for Tomorrow: `STM-04-US-017`

### Story Title:
**Parent / Guardian Visibility into Student Academic Standing and Lifecycle Leaves**

### Core Objective:
Enable parents and legal guardians (Persona: `R26 / PARENT`) authenticated in CampXSync to view the real-time academic standing (`ACTIVE`, `ON_LEAVE`, `GRADUATED`, etc.) and approved leave-of-absence history for their linked students, strictly governed by active guardianship relations in `stu.guardianships`.

### Architectural Flow:
$$\text{UI (Parent Portal)} \xrightarrow{\text{BFF}} \text{API Gateway (:8080)} \xrightarrow{\text{HMAC + X-Linked-Student-Id}} \text{Lifecycle Service (:8096)} \xrightarrow{\text{HikariCP}} \text{Supabase PostgreSQL} \xrightarrow{} \text{ControlledIntelligenceAdapter}$$

### Implementation Requirements:
1. **Security & Authorization (`LifecycleSecurityContext.java` / `LifecycleDomainService.java`):**
   - Validate `X-User-Role: PARENT` or `R26`.
   - Require `X-Linked-Student-Id` header matching an active relationship in `stu.guardianships` with `can_view_academics = true`.
   - Prevent parent access to unlinked students (negative RBAC: HTTP 403 `PARENT_FORBIDDEN` / `UNLINKED_STUDENT`).
2. **Java Controller & Domain Logic:**
   - Expose endpoint `GET /api/v1/lifecycle/parent/students/{studentId}/status` returning student standing and approved leaves.
3. **Frontend Parent Desk:**
   - Ensure the parent view renders the linked student's academic standing badge, active leave dates, and historical standing timeline.
4. **Verification & Evidence:**
   - Java unit tests for parent authorization and negative cross-student isolation.
   - Visible headed Playwright demo with video recording in `docs/demos/STM-04-US-017/demo.webm`.
   - Direct read-back on Supabase dev ref `azulbaxszsqxymqmlwfe`.
