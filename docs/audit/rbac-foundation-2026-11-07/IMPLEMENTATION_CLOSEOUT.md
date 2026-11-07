# RBAC Foundation Implementation Closeout — 2026-11-07

**Approval:** CampXSync Product Manager — “go with your proposal, go ahead and do the rest.”  
**Decisions:** `APPROVED_DECISIONS.md` (D-RBAC-01…07)  
**Dev project only:** `azulbaxszsqxymqmlwfe`

## Completed in this closeout

| Item | Evidence |
| :--- | :--- |
| Catalogue roles R31/R32/R33 + `stu*` permissions seeded | Migration `20261107000200_rbac_stu_catalogue_addendum` applied via Supabase MCP; live `iam.role_templates` shows REGISTRAR=R31, ADMISSIONS_OFFICER=R32, APPLICANT=R33; Registrar holds `stu03.*` CFG/approve/promote grants |
| STM-03 enrolment DDL + tenant RLS | Migration `20261107000100_create_stu_enrollment_tables` applied; `academic_terms`, `registration_windows`, `enrolments`, `enrolment_selections` all FORCE RLS |
| Java admin unlock | `EnrollmentDomainService`: R31/REGISTRAR CFG+approve+promote; R07/R08 allocate; R10 deny approve; R25 student paths |
| Unit tests | `EnrollmentDomainServiceTest` — **18 tests PASS** (Maven surefire) |
| JDBC persistence scaffold | HikariCP + `PostgresEnrollmentPersistence` + claims-bound `EnrollmentSecurityContext`; deployed start refuses missing JDBC |
| API surface expansion | status, eligibility, drop, allocations, waitlist join/promote, roster routes on enrollment-service |
| Live tenant RLS | MCP DO-block on `stu.enrolments`: tenant B count=0 for tenant A row; tenant A count=1; result `rls_ok` |
| Freeze | Administrative STM-03 POLICY_UNASSIGNED freeze **lifted** for approved grants only; unlisted permissions remain fail-closed |

## Explicit non-claims

- No production Supabase writes.
- No Wave 11 cutover.
- Course/section catalogue tables remain in-memory until a later STM-03 DDL addendum (current DDL covers terms/windows/enrolments/selections only).
- Full JDBC integration suite and browser E2E not run in this closeout (DB evidence is MCP SQL + unit tests).

## Safety compliance

- Branch/working tree preserved (no reset/stash/push).
- Only approved development ref touched.
- No root `.env.local` reads.
