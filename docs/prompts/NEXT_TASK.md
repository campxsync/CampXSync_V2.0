# Next Task

**STATUS: PROPOSED – awaiting owner approval; do not implement.**

## Selected story

| Field | Value |
|---|---|
| Issue ID | `STM-03-US-004` |
| Summary | Update enrolment status |
| Service | STM-03 — Student Enrolment & Registration Service |
| Issue Type | Story |
| Priority | P1 |
| Audit/CSV implementation status | partially completed |
| RBAC Alignment Verdict | MATRIX-DEFINED; ROLE-CATALOGUE GAP |

## Mode

**IMPLEMENT** (only after owner approval of this proposal).

## Scope

Confirm this story's **Acceptance Criteria (Draft Given/When/Then)** and **Required Negative Tests** against the actual repository code and close **only** the gaps found. Do not expand into adjacent stories.

### In scope
- Gaps between the story's acceptance criteria / required negative tests and current enrollment-service (and related gateway/BFF/UI) behavior for `STM-03-US-004` (update enrolment status).
- Exception handling, documentation and logging for the functionality changed (Agreement §3.2).
- Evidence per Agreement §7A for tests actually run.

### Out of scope
- Any other Issue ID; RBAC FLAGS; inventing role grants (including DEPARTMENT_STAFF); architectural redesign; Docker/VM/cloud/deployment; new tools/dependencies; production DB; unapproved migrations; commit/push unless separately authorized.

## Dependencies

Shared API Gateway/IAM; STM-01 identity; STM-02 admission; ACD programme/course context; EXM-01 prerequisites where applicable; existing calendar, notification and event infrastructure

## Stop conditions

Stop and report (Agreement §5.2) if a boundary would be crossed, requirements conflict, a dependency is missing, RBAC would require inventing grants, or a test failure cannot be fixed inside this story's scope.

## Evidence required (Section 7A)

For every test run: exact command; commit/working-tree state; environment; exit code; results; skipped tests. Do not claim live-DB or Playwright passes without running them.

## RBAC rules (from this row only — no invented grants)

| Column | Value |
|---|---|
| RBAC Matrix Role(s) | Student: V own; X request/select courses; Registrar / Department Staff: C/U/RV; A when delegated; Faculty / Advisor: V assigned roster and relevant status |
| Canonical RBAC Role Code(s) | R10; R25 |
| Required Negative Tests | unauthenticated; wrong role; wrong tenant; wrong institution/campus/department/programme/course/section/resource; missing delegation; self-approval; stale/revoked guardian consent where applicable; sensitive-field overread; IDOR/nested-resource/export |

## Do not start adjacent stories

Implement only `STM-03-US-004` after explicit owner approval of this PROPOSED task.

## Prior story

`STM-03-US-003` — **APPROVED** + COMMIT (owner 2026-11-14; completed under owner “start / test / commit” directive).
