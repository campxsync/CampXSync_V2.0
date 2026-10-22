# Next Antigravity Task — Wave 5: Timetable Domain & Relational Persistence

**Target Repository:** `D:\CampXSync\CampXSync_V2.0`  
**Governing Documents:** `.agent/rules/campxsync-wave-control.md`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, `docs/project-control/PROGRESS.md`, `docs/reports/wave-4/COMPLETION_REPORT.md`  
**Current Branch:** `feature/java-backend-integration`

---

## 1. Wave 4 Completion Status

Wave 4 (Student Domain Foundation & Roster) has reached complete, verified closeout on development database `azulbaxszsqxymqmlwfe`:
- **G1 Least-Privilege Role:** Verified live with `campx_app_server` (`rolsuper=false`, `rolbypassrls=false`, `rolcanlogin=true`).
- **Student & Person Domain Persistence:** `core.persons`, `stu.students`, and `doc.documents` live persistence and joined read-back verified.
- **Transactional Outbox Persistence:** Decision Gate DEC-W4-OUTBOX applied and verified live via `LivePostgresStudentPersistenceTest` (2/2 PASS).
- **Tenant Isolation & Security:** Fail-closed tenant boundaries, cross-tenant read denial (0 rows), cross-tenant write denial (RLS violation), atomic rollback on outbox failure, and unconditional rollback leaving zero residual rows verified live.
- **Suite Verification:** All 39 unit tests in `student-service`, gateway routes in `api-gateway`, 58 tests in `logger`, and full 16-module reactor test-compilation pass cleanly.

---

## 2. Next Task: Wave 5 — Timetable Domain & Relational Persistence

In accordance with Section 6 of `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, proceed to Wave 5:

### Step 1: Preflight & Schema Audit
- Inspect the existing timetable schema in PostgreSQL (`acd.timetables`, `acd.timetable_entries`, `acd.course_sections`, rooms, and periods) on development ref `azulbaxszsqxymqmlwfe`.
- Inspect existing RLS policies and role grants on timetable tables for `authenticated` and `campx_app_server`.

### Step 2: Timetable Conflict Rules & Architecture Specification
- Document conflict rules before implementation:
  1. Faculty double-booking (same faculty cannot teach two sections simultaneously).
  2. Batch / Section double-booking (same student cohort cannot have two classes at the same slot).
  3. Room double-booking (if physical rooms are in scope).
- Define effective dates, term versioning, and Indian Standard Time (IST) conventions.
- Prepare conflict enforcement strategy (service-level locking vs. exclusion constraints).

### Step 3: Implement Timetable Java Domain Service (`services/timetable-service` or ACD-05)
- Model timetable slot entities with aggregate ownership and foreign keys to tenant, college, program, batch, faculty, and course.
- Implement API Gateway routing for timetable endpoints (`/api/v1/timetables`).
- Implement transactional outbox event emission for slot creations, mutations, and cancellations (`timetable.slot_changed.v1`).

### Step 4: Verification & Test Suite
- Comprehensive unit tests for conflict rules, concurrency, invalid references, and department scoping.
- Live integration tests against `azulbaxszsqxymqmlwfe` validating RLS isolation, conflict rejection, and outbox persistence with rollback.

---

## 3. Mandatory Safeguards

- Development Supabase ref `azulbaxszsqxymqmlwfe` exclusively.
- Strictly prohibit production `ekspahbsbwdpraaqfqui` and legacy staging `kpwdvvcschankgggitnj`.
- Preserve current branch `feature/java-backend-integration`, working tree, and untracked files.
- Zero unapproved database changes, resets, stashes, or commits.
