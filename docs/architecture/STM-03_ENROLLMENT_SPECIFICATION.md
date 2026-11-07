# STM-03 Enrolment & Registration

Status: domain rules, gateway routes, student UI (view/register), OpenAPI, PostgreSQL persistence (dev), and Intelligence contract adapter are implemented. Live Intelligence remote delivery remains pending (`liveEndpoint=false` in controlled adapter).

## Path

UI `frontend/src/app/(dashboard)/student/enrollment` → `frontend/src/app/api/student/enrollment` → API Gateway `/api/v1/enrollment` → enrollment-service `:8095` → Supabase PostgreSQL (`stu.*`) when JDBC is configured.

## RBAC (approved 2026-11-07)

| Role | Allowed action |
| :--- | :--- |
| R25 / STUDENT | View own enrolment; list courses/sections; register; drop in window; join waitlist; view own waitlist position/history |
| R10 / FACULTY | View assigned section roster only (no Approve) |
| R31 / REGISTRAR | Term/window CFG; handover create; status approve; waitlist promote; staff history/view |
| R07 / DEAN, R08 / HOD | Scoped section allocation |

Unlisted permissions remain fail-closed.

## Intelligence contract

`student.enrolled.v1`, schema `1.0.0`, emitted via `EnrollmentIntelligenceService` → `ControlledIntelligenceAdapter`. Payload: `tenantId`, `enrolmentId`, `studentId`, `applicationId`, `termId`, `courseIds`, `totalCredits`, `correlationId`.

## Database (dev `azulbaxszsqxymqmlwfe`)

Applied:

- `20261107000100_create_stu_enrollment_tables.sql`
- `20261107000200_rbac_stu_catalogue_addendum.sql`
- `20261107000300_create_stu_enrollment_catalogue_tables.sql` (courses, sections, waitlist, history, cleared prerequisites)

All listed tables use FORCE RLS with tenant claims binding.

## OpenAPI

`services/student-services/enrollment-service/openapi.yaml`
