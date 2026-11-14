# Next Task

**STATUS: PROPOSED – awaiting owner approval; do not implement.**

## Selected story

| Field | Value |
|---|---|
| Issue ID | `STM-03-US-002` |
| Summary | View enrolment details |
| Service | STM-03 — Student Enrolment & Registration Service |
| Issue Type | Story |
| Priority | P0 |
| Audit/CSV implementation status | partially completed |
| RBAC Alignment Verdict | MATRIX-DEFINED; ROLE-CATALOGUE GAP |

## Mode

**IMPLEMENT** (only after owner approval of this proposal).

## Scope

Confirm this story's **Acceptance Criteria (Draft Given/When/Then)** and **Required Negative Tests** against the actual repository code and close **only** the gaps found. Do not expand into adjacent stories.

### In scope
- Gaps between the story's acceptance criteria / required negative tests and current enrollment-service (and related gateway/BFF/UI) behavior for `STM-03-US-002` (view enrolment details).
- Exception handling, documentation and logging for the functionality changed (Agreement §3.2).
- Evidence per Agreement §7A for tests actually run.

### Out of scope
- Any other Issue ID; RBAC FLAGS; inventing role grants; architectural redesign; Docker/VM/cloud/deployment; new tools/dependencies; production DB; unapproved migrations; commit/push unless separately authorized.

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
| RBAC Permission | V (scoped); mutation requires separately authorized action \| Service posture: Student=V own; X request/select courses ; Registrar / Department Staff=C/U/RV; A when delegated ; Faculty / Advisor=V assigned roster and relevant status |
| Authorization Scope / Constraints | Tenant + institution/campus + service-defined scope; Student: Eligibility, prerequisites, capacity and duplicate checks required ; Registrar / Department Staff: Approval scope and term/programme constraints enforced ; Faculty / Advisor: Assigned course/section scope |
| Canonical RBAC Role Code(s) | R10; R25 |
| Required Negative Tests | unauthenticated; wrong role; wrong tenant; wrong institution/campus/department/programme/course/section/resource; missing delegation; self-approval; stale/revoked guardian consent where applicable; sensitive-field overread; IDOR/nested-resource/export |

## Do not start adjacent stories

Implement only `STM-03-US-002` after explicit owner approval of this PROPOSED task.

## Prior story

`STM-03-US-001` — **APPROVED** (owner 2026-11-14).
