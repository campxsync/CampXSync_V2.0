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

## H — Student One login link identity verification & active link (Part 1)

**Decision:** Verified why identity verification was previously blocked (College Admin carries Tenant 1, but direct null-document verification inserts failed RLS `iam.perm_all('doc.write')`, and `doc.verifications` constraint requires `result in ('VALID', 'INVALID', 'EXPIRED', 'REVOKED')`). Resolved via normal operational flow: approved document verifications for Student One's person ID already exist with `result = 'VALID'`. Added `loadLatestVerificationStatus` to check `person_id` and map `'VALID'` to `'VERIFIED'`. Created ONE active login link for Student One using Delegated Registrar (`REGISTRAR_PILOT_EMAIL` holding active delegation `STM-07.ACCOUNT.A`), calling existing `POST /api/v1/students/{id}/iam-link` with `linkIamAccount` in `PostgresStudentRepository` writing to `stu.student_iam_links`.

**Files:**
- `services/student-services/student-service/.../PostgresStudentRepository.java`
- `services/student-services/student-service/.../StudentRepository.java`
- `services/student-services/student-service/.../InMemoryStudentRepository.java`
- `scripts/dev/ensure_student_one_login_link.mjs`
- `docs/reports/STUDENT_ONE_LOGIN_LINK_BEFORE_AFTER_2026-11-18.json`

**Undo:** Revert `linkIamAccount` and `loadLatestVerificationStatus` updates; delete link row from `stu.student_iam_links`.

---

## I — Owner Decision D13 Delegated Registrar student account link service path (Part 2)

**Decision:** Built the Java service path for Delegated Registrar (R31) to reach staff account-link status and eligibility desks under approved Owner Decision D13. Service verifies delegation key `STM-07.ACCOUNT.A` through `DelegationPort` and queries minimal student fields (`id`, `status`, `iamUserId`, `verificationStatus`) via `findStudentForAccountLink`, enforcing tenant and college scoping without requiring general student read or altering any PostgreSQL RLS rules. Re-ran browser demo with Playwright: `02_registrar_delegated_success.spec.mjs` passed with status ACTIVE; `03_registrar_without_delegation_deny.spec.mjs` stopped as designed (covered by 4 Java unit tests in `Stm07Us003StaffLinkStatusTest`).

**Files:**
- `services/student-services/student-service/.../StudentDomainService.java`
- `services/student-services/student-service/.../Stm07Us003StaffLinkStatusTest.java`
- `docs/reports/playwright-stm07-batch4/REGISTRAR_DEMO_RUN_REPORT.json`
- `docs/reports/playwright-stm07-batch4/screenshots/02_registrar_delegated_success.png`

**Undo:** Revert D13 methods in `StudentDomainService` and `Stm07Us003StaffLinkStatusTest`.

---

## J — Autonomous decisions audit and security hardening (Part 3)

**Decision:** Audited all earlier autonomous decisions:
(a) Guardian reads: Compared against canonical matrix. Verified that person-based linked views respect consent categories (`can_view_academics`, `can_view_fees`, etc.) within valid consent time windows for linked students only, without exceeding matrix posture.
(b) Recent portal activities: Audited `dashboard-aggregator.ts` and excluded internal reason fields and staff notes from student-facing summaries. Verified with unit test `stm10Us015_excludesInternalReasonsAndStaffNotesFromSummary` (15/15 passed).
(c) Admissions auto-provision: Verified that `HttpAccountProvisioningPort` defaults to OFF (`CAMPX_ADMISSIONS_AUTO_PROVISION_ENABLED=true` required to activate), requires verified identity at the domain layer, and is idempotent via `findOpenProvisioningRequest`.
(d) Timetable batch route: Verified that blocking students from `GET /batch/{batchId}` broke no other consumer; students exclusively use `GET /my`.

**Files:**
- `frontend/src/lib/portal/dashboard-aggregator.ts`
- `frontend/src/lib/portal/dashboard-aggregator.test.ts`
- `services/student-services/admissions-service/.../HttpAccountProvisioningPort.java`
- `docs/reports/AUTONOMOUS_DECISIONS_LOG.md`

**Undo:** Revert the aggregator sanitization and admissions default-off flag.

