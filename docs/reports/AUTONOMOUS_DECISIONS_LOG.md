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

**Commit:** `93e9fba`

---

## C — Registrar (R31) student-row visibility

**Decision:** Make **NO** new database access rule. Mark the Registrar staff-screen student-row / demo case **BLOCKED**.

**Why (quoted matrix rows):**
1. **Table 16 (codes):** `V | View/read permitted data` and `RV | Review/verify` are distinct — the matrix does not treat RV as V.
2. **Table 17 STM-01 Identity/Profile | Registrar / Admissions:** `C, U, RV; A verification if delegated` — grants create/update/review-verify and delegated approval of verification; **does not list `V` (view/read)**. Silent on a standalone read of student rows without workflow context.
3. **Table 17 STM-07 Account Provisioning | College Admin / Registrar:** `Request or trigger only when delegated` — staff account-link actions require delegation (`STM-07.ACCOUNT.A` per owner decision STM-07-B3-D5).
4. Autonomy rule C: if the matrix requires delegation to view, **or is silent**, make no rule change.

**Files:** docs only (`AUTONOMOUS_DECISIONS_LOG.md`, `COMPLETION_REPORT.md`, `NEXT_TASK.md`, `PROGRESS.md`). No SQL, no RLS.

**Undo:** N/A (no code/DB change). Owner can later quote an explicit Registrar `V` (college-scoped) row or authorize a delegated-read design, then revisit.

**Commit:** `c71cc71`

---

## D — View recent portal activities (STM-10-US-015)

**Decision:** Aggregate recent activities inside the existing portal dashboard aggregator from existing student-owned history APIs only: `GET /api/v1/students/{id}/history` (profile / `audit.change_log`) and `GET /api/v1/lifecycle/students/{id}/history`. Student id comes only from `/students/me`. Drop any history row whose `studentId` does not match. Surface as section `activities` + UI list on the portal aggregate panel. No new table or service.

**Why:** Buildable-now note already pointed at audit/activity aggregation; sources exist and enforce own-profile reads in Java.

**Files:** `frontend/src/lib/portal/dashboard-aggregator.ts`, `dashboard-aggregator.test.ts`, `frontend/src/components/portal/portal-dashboard-aggregate.tsx`, docs.

**Undo:** Revert the item commit.

**Commit:** `dfd628b`

---

## E — View permitted linked views for my student (STM-10-US-021)

**Decision:** Keep existing consent windows; allow the linked **guardian person** (actor userId = guardianPersonId) to call `viewAuthorizedInformation` read-only. Add `listPermittedLinkedViews` + `GET /api/v1/guardians/persons/{id}/permitted-views`. BFF uses session userId only. Browser demo marked **no test account** (frontend has no guardian role / no Dev guardian login).

**Why:** Matrix STM-10 Parent/Guardian: “V permitted linked views”; existing APIs already encode categories; guardian was previously blocked by `requirePeriodActor` (student/registrar only).

**Files:** `GuardianDomainService.java`, `GuardianController.java`, `GuardianDomainServiceTest.java`, `frontend/src/app/api/guardian/linked-views/route.ts`, `frontend/src/app/(dashboard)/guardian/linked-views/page.tsx`

**Undo:** Revert the item commit.

**Commit:** `58eeb5e`

---

## F — Existing IAM / Gateway / tenant context (STM-10-EN-003)

**Decision:** Treat EN-003 as closed on existing Channel-A + portal access gates. Add negative tests that Platform Admin (R01) and College Admin cannot load the student portal aggregate (D9). Document evidence in `docs/reports/STM10_EN003_IAM_GATEWAY_TENANT_EVIDENCE.md`. Do **not** build a college selector or weaken tenant rules.

**Why:** Buildable-now note said “mostly present”; remaining Platform Admin staff-desk tenant gap is explicitly forbidden by D9.

**Undo:** Revert the item commit.

**Commit:** `e987165`

---

## G — STM-07-EN-005 admission auto “request login” after handover

**Decision:** Wire best-effort `AccountProvisioningPort` / `HttpAccountProvisioningPort` after successful handover student create, copying the lifecycle `HttpStudentAccountAccessPort` env (`STM01_SERVICE_URL` / `STUDENT_SERVICE_URL`) and header pattern. Fail-soft so handover success is not rolled back. Kafka fan-out remains HARD STOP / UNCOVERED.

**Why:** Autonomy G allows wiring when an existing S2S client pattern exists; DefaultStudentServiceAdapter + lifecycle port already call student-service over HTTP.

**Files:** `admissions-service/.../integration/AccountProvisioningPort.java`, `HttpAccountProvisioningPort.java`, `NoOpAccountProvisioningPort.java`, `HandoverOrchestrationService.java`, `HandoverOrchestrationServiceTest.java`

**Undo:** Revert the item commit.

**Commit:** `2ab091c`

---
