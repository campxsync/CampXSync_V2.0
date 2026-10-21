# Wave {{N}} Completion Report — {{Wave Name}}

**Date:** {{YYYY-MM-DD}}  
**Branch / HEAD:** {{branch and commit}}  
**Status:** {{Complete / Accepted with limitations / Blocked}}  
**Evidence basis:** {{commands, outputs, report/log paths; distinguish reported from independently verified}}

## 1. Scope and outcome
- Approved scope:
- Implemented:
- Deferred/out of scope:
- Deviations/reasons:

## 2. Files changed
List exact modified/new/deleted paths and purpose. Confirm unrelated work was preserved.

## 3. Commands and tests
| Command/suite | Result | Pass/fail/error/skipped counts | Test tier | Notes |
|---|---|---|---|---|
| | | | mock / in-memory / isolated PostgreSQL / approved staging / browser | |

Record actual output. Explain skipped tests; do not infer counts.

## 4. End-to-end evidence
For each workflow: frontend → gateway → Java → PostgreSQL path attempted; request/response; persisted read-back; exact test tier/environment; missing tiers and why. Unit tests/compilation alone are not E2E evidence.

## 5. Tenant isolation and authorization
- G1 status/evidence:
- DB login role and `BYPASSRLS` verification (no secrets):
- Transaction-scoped tenant/user context:
- Cross-tenant denial tests and tier:
- Role/department denial tests:
- Any reason isolation cannot be claimed verified:

Do not claim RLS verified if tests used a privileged bypass role or mocks only.

## 6. Events/outbox/Intelligence Layer
| Event type | Owner | Schema version | Privacy class | Outbox/publication test | Duplicate/failure/retry | Real endpoint or test double | Status |
|---|---|---|---|---|---|---|---|
| | | | | | | | complete / pending |

State whether correlation ID, tenant context, event time and entity IDs are preserved. Test doubles do not prove live integration.

## 7. Frontend migration
- Consumers migrated:
- Remaining direct-DB consumers (exact paths):
- Why they remain/prerequisite API:
- Dev impersonation/test-session safeguards:

## 8. Gates and non-functional evidence
- G1 least-privilege role: {{Open/Closed/Not applicable to code-only work}}; evidence:
- G2 isolated PostgreSQL CI: {{Open/Closed/Not yet due}}; evidence:
- G3 JDK/framework assessment: {{Open/Closed/Not yet due}}; evidence:
- Latency p50/p95 and budget, where relevant:
- Observability/rollback/performance findings:

## 9. Database and safety record
- Non-production target verified? How (no credentials):
- Writes/migrations/grants/RLS/seeding/provisioning performed? List exact approved steps, or state none.
- Production access: **none**.
- Working tree preserved?:
- Commit/push: **no**, unless explicitly authorized.
- Secrets/sensitive data exposed: **no**.

## 10. Blockers, risks and pending evidence
List each item, next action and whether it blocks persistence, isolation claims, wave exit or production readiness.

## 11. Exit criteria
For each plan criterion: met / not met / not verifiable, with evidence.

## 12. Checkpoint and next task
- `PROGRESS.md` updated from evidence:
- `NEXT_ANTIGRAVITY_TASK.md` updated from evidence:
- `docs/reports/wave-{{N}}/COMPLETION_REPORT.md` updated:
- Next task (do not start it in this run):
- Explicit stop confirmation:
