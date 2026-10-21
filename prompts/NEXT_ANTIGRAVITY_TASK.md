# Next Antigravity Task — Wave 4 Student Domain Foundation & Roster

Execute the next implementation task directly; do not return only a plan, audit or another prompt. Follow `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` and `.agent/campxsync-wave-control.md`.

## 1. Preserve current work
- Inspect `git status --short --branch` and HEAD.
- Preserve all modified/untracked files from Waves 1–3. Do not reset, clean, stash, switch branches, overwrite unrelated files, commit or push.
- Use the current Wave 3 report and control files as checkpoint; avoid repeating broad audits without a concrete blocker.

## 2. G1 gate
Check evidence for a dedicated application DB login role without `BYPASSRLS`, transaction-scoped tenant/user context, and verified non-production DB target.
- If closed, record exact evidence and source.
- If open/unclear, no migrations, grants, RLS changes, seeds, provisioning, shared-DB persistence tests or DB writes.
- Safe design, code not connecting to a DB, unit tests and isolated tests that cannot touch shared/production DB may continue.
- Every DB write/role/grant change requires explicit approval for that exact step. Never connect to production.

## 3. Student model approval checkpoint
Before implementing the canonical model, document a concise proposal for approval covering student/person linkage; institution-scoped admission/enrolment/roll identifiers; lifecycle states/transitions; college/program/academic-year/term/batch relationships; ownership across Student, IAM, Academics, Exams, Finance, LMS, Documents and Workflow; API endpoints and role/department authorization; and any schema changes separately from code-only work. Do not treat the proposal as approved without written user approval. Continue only safe work that does not pre-empt the decision.

## 4. Inspect and reuse existing ownership
Verify current source and migrations:
- `stu.students` is the existing student-record candidate.
- `core.persons` owns canonical person/demographic identity.
- `stu.guardianships` is the existing guardian relationship candidate.
- Reuse IAM profiles/role assignments; do not create duplicate credentials.
- ACD owns batch/section enrolments and attendance-related academic structures where existing schema says so.
- EXM remains authoritative for official exam results.
- Reuse shared audit/outbox, document and workflow capabilities.
- Do not assume admission applications or term-registration tables exist.
Do not make schema changes merely to fit an imagined architecture. Propose the minimal change and wait for explicit approval before executing it.

## 5. Implement only approved Wave 4 scope
Student domain/roster, stable student/batch references, cursor-paginated list/search, lifecycle transitions and enrol/transfer/withdraw paths where approved and supported, optional idempotent bulk import with row-level validation, trusted gateway identity, tenant/role/department enforcement, data minimization and versioned events through Wave 1 outbox. Do not implement all ten logical student microservices in this wave.

## 6. Test and record evidence
Run focused tests, then relevant module tests and broader compilation/tests if practical. Classify each test as mock, in-memory, isolated PostgreSQL, approved staging DB or browser. If safe and authorized, verify frontend → gateway → Java → PostgreSQL, persisted read-back and cross-tenant denial. Otherwise record the strongest tier actually tested and leave missing evidence pending. Do not claim tenant isolation verified while G1 is open.

## 7. Completion and stop
Update from actual evidence:
- `docs/project-control/PROGRESS.md`
- `docs/reports/wave-4/COMPLETION_REPORT.md`
- `prompts/NEXT_ANTIGRAVITY_TASK.md`

Record exact files, commands, test counts/skips, test tier, G1–G3, frontend consumers, event integration status, blockers and guardrail confirmation. Stop after Wave 4. No commit/push.
