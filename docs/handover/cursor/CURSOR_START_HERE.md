# CampXSync — Cursor Onboarding & Engineering Guide
**Document:** `docs/handover/cursor/CURSOR_START_HERE.md`  
**Effective Date:** November 7, 2026  
**Audience:** Cursor AI (Official Coding Partner for CampXSync)  
**Governing Branch:** `feature/java-backend-integration`  
**Baseline Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  

---

## 1. Welcome & Operational Mandate

Welcome, Cursor! As of **November 7, 2026**, you are the official AI coding partner for the **CampXSync College ERP** project.

Your mission is to continue development seamlessly without repeated architectural discovery, without re-investigating completed work, and without discarding established decisions.

### Core Tenet
> **Preserve the Architecture. Deliver Functionally. Never Replace Working Code with Mocks.**

---

## 2. Immediate Pre-Flight Checklist

Before you write any code or execute any commands, verify your local environment against these rules:

1. **Verify Git Branch:**
   ```bash
   git branch --show-current
   ```
   **Must be strictly:** `feature/java-backend-integration`. Do not switch branches, create feature branches, commit, push, stash, or reset without explicit operator authorization.
2. **Verify Working Tree:**
   ```bash
   git status
   ```
   Keep the working tree clean and preserve all developer modifications.
3. **Target Database Safety Rules (Non-Negotiable):**
   - **PROHIBITED:** Production Supabase project `ekspahbsbwdpraaqfqui` (NEVER touch).
   - **PROHIBITED:** Legacy Staging Supabase project `kpwdvvcschankgggitnj` (RETIRED & OFF-LIMITS).
   - **AUTHORIZED DEV ONLY:** `azulbaxszsqxymqmlwfe` (`CampXSync-Architecture-Dev`).
   - **NEVER** read `.env.local` or `frontend/.env.local`.
   - **NEVER** execute live database writes, schema migrations, role grants, or RLS changes without explicit user authorization for that specific command.
4. **Mandatory Canonical Applications:**
   - **Canonical Frontend:** `frontend/` (Next.js 15, React 19 App Router).
   - **RETIRED Frontend:** `frontend-web/` (Legacy Vite prototype — DO NOT USE OR DEPLOY).
   - **Backend:** Java 8 microservices organized by domain under `services/{domain}-services/{microservice}/`.
   - **Gateway:** Unified Java HTTP reverse proxy under `api-gateway/` (Port 8080).

---

## 3. Mandatory Reading Order

Cursor must read these documents in order before planning or executing tasks:

| Priority | Document | Repository Path | Why It Is Critical |
| :---: | :--- | :--- | :--- |
| **1** | **Root Agent Rules** | [`AGENTS.md`](file:///d:/CampXSync/CampXSync_V2.0/AGENTS.md) | Architectural rules, transactional path, safety boundaries. |
| **2** | **Wave Control Rules** | [`.agent/rules/campxsync-wave-control.md`](file:///d:/CampXSync/CampXSync_V2.0/.agent/rules/campxsync-wave-control.md) | Single bounded task discipline, no mock fallbacks in prod. |
| **3** | **Master Handover** | [`docs/handover/cursor/MASTER_HANDOVER.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/handover/cursor/MASTER_HANDOVER.md) | Comprehensive system architecture, audit, and status summary. |
| **4** | **Documentation Index** | [`docs/handover/cursor/COMPLETE_DOCUMENTATION_INDEX.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/handover/cursor/COMPLETE_DOCUMENTATION_INDEX.md) | Inventory of all 185 repository Markdown files and authority. |
| **5** | **Living Progress Log** | [`docs/project-control/PROGRESS.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/project-control/PROGRESS.md) | Detailed verification log across Waves 0–10, restructuring, & STM-02. |
| **6** | **Next Task Spec** | [`prompts/NEXT_ANTIGRAVITY_TASK.md`](file:///d:/CampXSync/CampXSync_V2.0/prompts/NEXT_ANTIGRAVITY_TASK.md) | Handover state halted at Wave 10 completion. |
| **7** | **Admissions Verified** | [`docs/project-control/STM-02_TARGETED_COMPLETION_REPORT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/project-control/STM-02_TARGETED_COMPLETION_REPORT.md) | 55-item verified implementation of STM-02 admissions service. |
| **8** | **Student Role Verified**| [`docs/student-dashboard/evidence/PHASE_3B_AND_4_VERIFICATION_REPORT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/PHASE_3B_AND_4_VERIFICATION_REPORT.md) | Live evidence of Student role, document RLS, and role routing. |

---

## 4. Architectural Rules & Invariants

```
                                      [ Browser Client / Next.js 15 ]
                                                    │
                                                    ▼
                                      [ API Gateway (Port 8080) ]
                                    (JWT Validation & HMAC Signing)
                                                    │
                      ┌─────────────────────────────┼─────────────────────────────┐
                      ▼                             ▼                             ▼
           [ Platform & College ]              [ Academic ]                   [ Student ]
             ADM-01: 8081 (Inst)          ACD-01: 8083 (Courses)         STU-01: 8093 (Student)
             ADM-02: 8082 (College)       ACD-02: 8084 (Curricula)       STM-02: 8094 (Admissions)
                                          ACD-03: 8085 (Subjects)
                                          ACD-04: 8086 (Batches)
                                          ACD-05: 8087 (Timetable)
                                          ACD-06: 8088 (Attendance)
                                          ACD-07: 8089 (Calendar)
                                          ACD-08: 8090 (Resources)
                                          ACD-09: 8091 (Assessments)
                                          ACD-10: 8092 (Analytics)
                      │                             │                             │
                      └─────────────────────────────┼─────────────────────────────┘
                                                    ▼
                                    [ Supabase / PostgreSQL 17 ]
                                      (Development: azulbaxszsqxymqmlwfe)
                                    (App Role: campx_app_server / RLS)
```

1. **Transactional Ingress Path:**
   - **UI** (`frontend/`) $\rightarrow$ **API Gateway** (`api-gateway/`, Port 8080) $\rightarrow$ **Java Domain Services** (`services/*`) $\rightarrow$ **Supabase / PostgreSQL** (`azulbaxszsqxymqmlwfe`).
   - Direct database access from frontend (`@/lib/db`) is retired for business operations. All data mutations must flow through Java microservices via the Gateway.
2. **Security & Cryptographic Boundary:**
   - Gateway verifies Supabase JWTs (`sub`, `tenant_id`, `user_role`, `college_id`).
   - Gateway strips untrusted client headers and signs downstream requests using HMAC-SHA256 (`X-Gateway-Timestamp`, `X-Gateway-Signature`).
   - Downstream Java services verify HMAC signature (`GatewayHmacVerifier`) with replay protection (60s to 300s window).
3. **Database Access Role:**
   - Dedicated unprivileged role: `campx_app_server` (`NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`).
   - Every transaction sets tenant claims: `SET LOCAL ROLE authenticated; SELECT set_config('request.jwt.claims', ..., true);`.
   - `FORCE ROW LEVEL SECURITY` is enabled on core tables.
4. **Intelligence Layer Integration:**
   - Java domain outputs publish to `sys.outbox_events` and emit structured versioned contracts via `ControlledIntelligenceAdapter` (e.g. `AdmissionsIntelligenceService` with 5 governed streams).
   - Real Intelligence Layer endpoint may be pending; contract testing with adapters is mandatory.

---

## 5. Immediate Next Steps & Decision Gate

The project is currently stopped at **Wave 10 Completion**. Two clear execution tracks are ready for operator authorization:

### Option A: Student Domain Track — STM-03 Academic Enrollment & Course Registration
- **Prerequisite:** STM-02 Admissions and STM-01 Student Master records are verified and operational.
- **Scope:** Build `enrollment-service` (or integrate into `student-service`), consuming the `admissions.enrolment.handover_requested.v1` event contract.
- **Features:** Degree course registration, batch section enrollment (ACD-04 integration), prerequisite validation (ACD-01 DAGs), credit limits, and add/drop workflows.

### Option B: Platform Go-Live Track — Wave 11 Production Readiness & Final Cutover
- **Scope:** Formal Gate G3 JDK/framework assessment, production database migration verification (applying verified migrations from dev to staging/prod), dependency vulnerability audit, zero-unmigrated schema check, and final production cutover runbook.

---

## 6. Development Discipline (Do's and Don'ts)

### What Cursor MUST DO:
- Keep all Java microservices on **Java 8** (`<source>1.8</source>`).
- Test every code change locally before claiming success:
  - Frontend typecheck: `cd frontend && npx tsc --noEmit`
  - Frontend build: `cd frontend && npm run build`
  - Java module compilation: `mvn test-compile -DskipTests`
  - Java unit tests: `mvn test -pl services/{domain}/{service} "-Dtest=!Live*"`
- Maintain strict multi-tenant isolation with positive path and negative cross-tenant assertions.
- Write paired reversible rollback scripts for every database DDL modification.
- Update `docs/project-control/PROGRESS.md` after completing any wave or bounded task.

### What Cursor MUST NEVER DO:
- **NEVER** switch to `main` or another branch.
- **NEVER** run `git push` or `git commit` without explicit operator instruction.
- **NEVER** connect to production Supabase project `ekspahbsbwdpraaqfqui`.
- **NEVER** execute live database writes without explicit operator authorization.
- **NEVER** replace working PostgreSQL JDBC repositories with in-memory test doubles or mock maps in production runtimes.
- **NEVER** introduce unapproved framework upgrades (e.g. forcing Spring Boot 3 / Java 17).
- **NEVER** claim a test passed without citing actual execution output or verified historical evidence.

