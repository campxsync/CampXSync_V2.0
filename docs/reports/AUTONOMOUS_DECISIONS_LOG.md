# Autonomous decisions log — STM-07 / STM-10 completion run

**Branch:** `feature/java-backend-integration`  
**Started:** 2026-11-18  
**Dev Supabase project (confirmed ACTIVE_HEALTHY):** `azulbaxszsqxymqmlwfe`  
**Workhorse:** Cursor  

Each entry: what was decided, why, which files, how to undo.

---

## A — Startup and Student One login link

**Decision:** Start stack with `scripts/dev/start_stack.ps1` (succeeded). Attempted ONE active login link for Student One (`81dd67d3-1d5f-4d8e-8061-a67648610002` ↔ auth `81dd67d3-1d5f-4d8e-8061-a6764861f924`) via existing verify + `POST .../iam-link` path. **Link NOT created — BLOCKED by existing database access rules on `doc.verifications`.**

**Why blocked:**
1. College Admin can carry a college tenant, but `doc.verifications` INSERT policy requires `iam.perm_all('doc.write')` or a `document_id` in the writer's document scope. Identity verifications insert with `document_id` null; the policy arm for null documents is `(NOT true) AND ...` (always false). Result: RLS reject.
2. Platform Admin (R01) JWT has no college tenant; student-service returns `Missing required X-Tenant-Id header`.
3. Autonomy hard-stop: do not weaken/relax database access rules to force the insert.

**Also decided:** Hydrate student `verificationStatus` on `findById` from existing `doc.verifications.result` so that once a VERIFIED row exists, IAM-link eligibility can see it (students table has no verification column).

**Files:**
- `services/student-services/student-service/.../PostgresStudentRepository.java` (hydrate)
- `scripts/dev/ensure_student_one_login_link.mjs`
- `docs/reports/STUDENT_ONE_LOGIN_LINK_BEFORE_AFTER_2026-11-18.json`

**Undo:** Revert the repository hydrate commit; delete the script/evidence if undesired. No DB rows were written for the link.

---

## B — View timetable (STM-10-US-004)

**Decision:** Add student-owned `GET /api/v1/academics/timetables/my` on the existing Timetable Management Service (Academic timetable service). Batch id is resolved only from authenticated actor membership (`stu.students.person_id` or active `stu.student_iam_links` → `acd.batch_students`), never from a client-supplied batch or student id. STUDENT role may call `/my` only; `/batch/{batchId}` is forbidden for students. BFF `GET /api/student/timetable` and page `/student/timetable` proxy the gateway without forwarding query batch ids. Endpoint/response shape: `{ membershipFound, batchId, entries, clientBatchIdIgnored }`.

**Why:** Unblocks View timetable without inventing a new service; closes the prior IDOR risk of client batch ids (Agreement D11).

**Files:**
- `services/academic-services/timetable-management-service/.../StudentBatchMembershipPort.java`
- `PostgresStudentBatchMembershipPort.java`, `FakeStudentBatchMembershipPort.java`
- `TimetableDomainService.java`, `TimetableController.java`
- `MyTimetableSecurityTest.java`
- `frontend/src/app/api/student/timetable/route.ts`
- `frontend/src/app/(dashboard)/student/timetable/page.tsx`
- `frontend/src/lib/nav-config.ts`

**Undo:** Revert the item commit; students again lack a safe my-timetable read.

**Demo note:** Browser demo may show “no data in Dev” if membership or published slots are empty; service tests use fixtures.

---
