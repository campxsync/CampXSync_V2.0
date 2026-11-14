# Next Tasks for Cursor: Pilot Launch Track

> **IMPORTANT NOTICE:**
> The backlog in `prompts/NEXT_ANTIGRAVITY_TASK.md` is **SUPERSEDED**.
> Feature stories `STM-04-US-017` (Batch Degree Audits), `STM-04-US-015` (Multi-Track Branch Transfers), and `STM-04-US-018` (Alumni Transition Workflow) are **DEFERRED**.
> Do **NOT** pick up new user stories or add speculative features.
> The primary mission for Cursor is executing the **6-Step Pilot Launch Track** to take CampXSync into production readiness for its first pilot college deployment.

---

## The 6-Step Launch Track

### Step 1: Containerization & Deployment Orchestration
* **Goal:** Package the entire stack into reproducible, single-command deployable containers.
* **Scope:**
  * Author multi-stage `Dockerfile` definitions for:
    * `frontend` (Next.js 15 standalone output mode)
    * `api-gateway` (Spring Cloud Gateway on JDK 8/17)
    * `services/student-services/lifecycle-service`
    * `services/student-services/document-service`
    * Shared Java support services (`auth-service`, `audit-service`)
  * Create `docker-compose.prod.yml` with health checks, memory limits, and log driver configuration.
  * Provide systemd unit templates for bare-metal / VM host deployments.
* **Verification:** `docker compose -f docker-compose.prod.yml up -d` boots the entire stack with all health probes passing.

---

### Step 2: Enable Production Internal Authentication & Zero-Trust Headers
* **Goal:** Eliminate spoofable client identity headers and enforce cryptographically signed JWT propagation across all microservices.
* **Current State:** Microservices run with internal auth relaxed (`auth.internal.enabled=false` or permissive development mock headers) to facilitate local test harnesses.
* **Scope:**
  * Configure API Gateway to strictly validate inbound Supabase Bearer JWTs.
  * In the Gateway filter, generate an internal asymmetric or HMAC-signed service-to-service token containing verified tenant, user, and role claims.
  * Update all Java services (`LifecycleController`, `DocumentController`, etc.) to reject any request lacking a valid gateway signature (`X-CampX-Signature` or internal JWT).
  * Deprecate and remove all unauthenticated test bypass headers in production profiles.
* **Verification:** Direct `curl` requests with forged `X-User-Id` or `X-Tenant-Id` headers sent directly to Java service ports are rejected with `401 Unauthorized`.

---

### Step 3: Implement Real PostgreSQL Repositories for Academic Services (ACD)
* **Goal:** Transition Academic domain modules from in-memory test mocks to real PostgreSQL persistence with tenant isolation.
* **Scope:**
  * Modules requiring PostgreSQL repository implementations:
    * `ACD-01` (Program & Curriculum Structure)
    * `ACD-02` (Course Catalog & Prerequisites)
    * `ACD-03` (Section Scheduling & Capacity)
    * `ACD-06` (Faculty Workload & Assignments)
    * `ACD-10` (Grading Schema & GPA Computation)
  * Implement standard Spring Data JDBC / JPA or jOOQ repositories mapping to `acd.*` schemas in Supabase.
  * Add multi-tenant RLS context setter in JDBC transaction interceptors (`SET LOCAL app.current_tenant_id = ?`).
* **Verification:** Academic test suites execute against live Supabase PostgreSQL database `azulbaxszsqxymqmlwfe` without in-memory repository fallbacks.

---

### Step 4: Staging & Production Supabase Setup and RLS Audit
* **Goal:** Prepare isolated staging and production environments and ensure zero data leakage across tenants.
* **Scope:**
  * Run schema migrations (`docs/engineering/schema_snapshot.sql`) against clean staging and production Supabase projects.
  * Perform an exhaustive Row Level Security (RLS) penetration audit:
    * Verify every table in `stu`, `doc`, `acd`, and `audit` has `ROW LEVEL SECURITY ENABLED`.
    * Verify `FORCE ROW LEVEL SECURITY` is applied to table owners.
    * Execute cross-tenant read/write injection tests to ensure Tenant A cannot read Tenant B's data under any role.
* **Verification:** Automated RLS penetration script returns 100% tenant-isolated results across all tables.

---

### Step 5: Pilot College Onboarding Runbook & Seed Utilities
* **Goal:** Provide a seamless, automated onboarding workflow for the first pilot educational institution.
* **Scope:**
  * Build an onboarding CLI / script:
    1. Provisions a new tenant record in `auth.tenants`.
    2. Seeds college organizational hierarchy (Colleges, Departments, Degrees, Programs).
    3. Provisions initial College Admin and Registrar credentials.
    4. Ingests student and faculty roster via standard CSV import with validation and rollback.
  * Document end-to-end admin runbook in `docs/runbooks/PILOT_ONBOARDING_RUNBOOK.md`.
* **Verification:** Running the onboarding script with a sample 500-student CSV provisions the college in < 60 seconds with verified login for admins and students.

---

### Step 6: Smoke Testing, Load Verification & Rollback Procedures
* **Goal:** Prove system stability under expected pilot concurrency and establish disaster recovery runbooks.
* **Scope:**
  * Run k6 or Artillery load tests simulating 200 concurrent student logins and 50 simultaneous petition submissions.
  * Establish baseline latency metrics (< 200ms p95 on read, < 500ms p95 on petition submission).
  * Configure uptime health checks (`/actuator/health`, `/api/health`) and alerting webhooks.
  * Document and test rollback scripts for application releases and database migrations.
* **Verification:** Zero error rate under 250 concurrent users; documented rollback verified in staging.
