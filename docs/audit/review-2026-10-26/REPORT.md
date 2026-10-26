# ReviewBranch engineering audit — 26 October 2026

## Verdict and evidence boundary

**CampXSync contains a substantial working MVP implementation, but is not ready for unrestricted multi-college production.** Authentication bypass in enabled test-session deployments, incomplete assignment/college authorization, and non-transactional academic writes are release blockers.

Baseline: `campxsync/CampXSync_V2.0`, **ReviewBranch**, `ffea85d1cf28a82ea7aa6d847808203269988bce`. This report supersedes findings made against `main` where this branch changed the implementation. No application fixes or database writes were made during this audit.

Coverage: all **933 tracked files** were inventoried; **181,203 text/extracted-document lines** were scanned, including 89 Markdown/Word documents. Every Next.js page is listed in [page-register.md](page-register.md). [file-coverage.csv](file-coverage.csv) records paths, hashes, line counts, and automated review signals. Deep manual inspection concentrated on authentication, authorization, database access, academic mutations, route/page data flow, Java trust boundaries, and existing tests. **An automated scan is not a claim that every line received exhaustive manual semantic review.** Binary images were inventoried, Word text extracted, and ZIP member lists inspected; no visual certification of all assets is claimed.

Live Supabase credentials are absent. Database policies, triggers, grants, deployed regions, credential rotation, email delivery, live browser journeys, and historical performance figures are **unverified in this run**. Earlier audit documents are context, not evidence that those conditions still hold.

## Architecture actually present

1. `frontend/`: Next.js 15, React 19, Supabase authentication, direct PostgreSQL through `pg`; the broadest current product UI and API implementation.
2. `frontend-web/`: a separate React/Vite client for the Java gateway. This branch adds real Supabase token handling, guarded mock fallback, and tests.
3. `api-gateway/`, `logger/`, `services/`: Maven reactor, 12 implemented business services plus gateway/logger. JDBC repositories exist in administration/course modules; nine academic services retain in-memory business state.
4. `database/`: incremental PostgreSQL changes and staging seeds alongside older MongoDB scripts. The checked-in migrations are not evidence of a reproducible complete live schema.
5. `infrastructure/`, `platform/`, `mobile-app/`: largely scaffolding, with MongoDB configuration and limited deployment configuration.

Choose and document the supported production path. Maintaining two clients and two backend approaches without an explicit boundary multiplies authorization and business-rule drift. Prefer completing the Next.js/PostgreSQL MVP before independently deploying the Java prototypes; retain useful Java domain tests and rules rather than assuming a wholesale rewrite is required.

## Prioritized findings

### R01 — Critical: enabled test sessions grant unauthenticated administrative identities

Evidence: `frontend/src/app/api/auth/test-session/route.ts` POST accepts a role without authenticating the caller and sets raw JSON in `campx_test_session`; `frontend/src/middleware.ts` and `frontend/src/lib/auth/resolve-role.ts` deserialize it as identity. `frontend/src/lib/auth/test-session-gate.ts:6` permits development automatically and non-Vercel-production runtimes when `ENABLE_TEST_SESSION=true`.

**Reproduced locally on this branch:** a production build started on loopback with that flag returned HTTP 200 and `SUPER_ADMIN` for an unauthenticated POST requesting `platform_admin`. Vercel production is explicitly blocked by the gate; this is not a claim that all production deployments are currently vulnerable. Signed cookies alone would not fix a publicly callable role-grant endpoint.

Action: remove impersonation from deployable builds or require authenticated, tightly scoped operators and server-stored, expiring sessions. Add negative tests for forged cookies, unauthorized issuance, expiry, logout, and preview deployment exposure.

### R02 — High: faculty authorization stops at tenant boundaries

Evidence: `frontend/src/app/api/college/attendance/records/route.ts:295` accepts faculty, loads a session by tenant, but does not require that the authenticated faculty owns/is assigned to that session. Student validation at line 379 checks tenant membership, not the section roster. `attendance/sessions/route.ts` accepts caller-provided employee/section references and falls back to a tenant employee. Faculty timetable/session pages query tenant-wide records. `frontend/src/app/(dashboard)/faculty/attendance/page.tsx:52` uses the first faculty record when user-to-employee resolution fails; the fallback is not gated by a demo flag.

Impact: a faculty user can address another instructor's session within the tenant; attendance can include students outside the section. This conclusion is from control-flow inspection, not live-database exploitation.

Action: resolve user -> profile -> person -> employee authoritatively; enforce faculty/section assignment in every API query. Reject missing linkage. Test faculty A against faculty B's session and student outside roster.

### R03 — High: college administrators are frequently scoped only by tenant

Evidence: `frontend/src/lib/auth/resolve-role.ts` resolves a college ID, but college pages and mutations commonly use only tenant ID. `frontend/src/app/api/college/students/assign-batch/route.ts` checks both objects belong to the tenant, without checking both belong to the administrator's assigned college. College roster pages similarly enumerate tenant-wide entities.

Impact: if multiple colleges share one tenant, a college administrator may cross their assigned college boundary. If the intended policy explicitly grants tenant-wide administration, document that distinction instead of calling it college isolation.

Action: define platform/tenant/college/department scopes and apply them consistently, including joins and foreign references. Add tests with two colleges in the same tenant, not only two tenants.

### R04 — High: attendance submission and correction lack atomic persistence

Evidence: `frontend/src/lib/db.ts:45` executes independent pool queries. Attendance records are written in a loop, then session closure and audit are separate queries. Corrections use a good compare-and-swap version predicate at `attendance/corrections/route.ts:117`, but correction history, audit and outbox inserts occur afterward, with exceptions swallowed.

Impact: partial attendance sheets, a changed record without mandatory correction history, inconsistent audit/outbox, and races between session closure and another submission. Batch assignment and other multi-step mutations also use separate queries.

Action: one checked-out connection and transaction per command; lock/check session state inside the transaction, bulk validate and write the roster, require audit/outbox to commit together. Add fault injection and concurrent submission/correction tests. Preserve the existing optimistic version check.

### R05 — High: PostgreSQL certificate verification is disabled

Evidence: `frontend/src/lib/db.ts:34`: `ssl: { rejectUnauthorized: false }` and removal of `sslmode`.

Impact: encryption does not authenticate the database server. Action: configure the provider CA and hostname validation; fail closed on certificate errors. Do not carry the current override into production.

### R06 — High: database row security is not established by the application pool

Evidence: `frontend/src/lib/db.ts` uses configured credentials and direct `pool.query`; it does not establish a per-request restricted role or tenant claims. Existing documents claim privileged live credentials and RLS bypass, but the actual role and grants could not be inspected here.

Impact is conditional on configured database privileges: using an owner/BYPASSRLS role makes application predicates the only tenant boundary. Action: dedicated least-privileged role, verified RLS policies and tenant-scoped transactions, explicit narrowly scoped platform administration. Test using the actual runtime role against parent and partition tables. Do not assume FORCE RLS alone constrains a superuser/BYPASSRLS role.

### R07 — High: student attendance denominator includes unrelated tenant classes

Evidence: `frontend/src/app/(dashboard)/student/page.tsx:62` starts from all tenant subjects and timetable entries, counts closed sessions, and left joins attendance for the current student. It does not restrict the counted classes to the student's enrollments/sections. It also counts PRESENT/ON_DUTY while other attendance endpoints count LATE as attended.

Impact: incorrect shortages and inconsistent percentages across student/admin dashboards. Action: define the attendance policy once; filter eligible/enrolled sessions and effective enrollment dates, handle cancelled sessions and excused statuses consistently, then test against independently calculated fixtures.

### R08 — High: timetable mutation validates only part of the ownership graph

Evidence: `frontend/src/app/api/college/timetable/entries/route.ts` checks subject tenant membership, inserts `section_faculty`, then inserts a timetable entry. Version, section, employee, and college ownership are not explicitly established in this handler. It relies on unverified database constraints/triggers. These separate writes can leave an assignment behind when timetable insertion fails.

Action: validate the full reference graph and assigned college, enforce date/time and weekday conventions, then perform assignment + entry + audit in one transaction. Verify conflict constraints on the actual database.

### R09 — Critical if exposed: Java subject endpoints permit unauthenticated access and cross-tenant reads

`services/subject-management-service/.../controller/SubjectController.java:65` defaults missing tenant/role, including `ACADEMIC_ADMIN`; server contexts do not attach a gateway verifier. `SubjectDomainService.java:255` gets by ID without tenant; search/export paths also lack tenant scope. These files are unchanged from the earlier inspected commit. Earlier local reproductions created a subject without identity headers and returned it through GET/list/export with a different tenant header. This is a retained Java finding, not a claim about the Next.js route stack.

Action: keep these services inaccessible to external clients until shared authentication and tenant-scoped repositories are enforced; remove privileged defaults. Test every read/mutation/export with tenant A/B and anonymous callers.

### R10 — High for Java deployment: durable persistence/integrations remain simulated

Curriculum, subject, batch, timetable, attendance, calendar, resources, assessment, and analytics domain state uses in-memory collections. Subject outbox relay acknowledges publication when no broker is configured. Assessment subject references accept ID prefixes without checking a real source. Resource object storage verifies nonempty keys and constructs synthetic retrieval URLs.

Action: either explicitly classify these modules as prototypes, or implement durable repositories, transactions, broker delivery/consumer deduplication, real object storage and reference checks. Startup, restart survival, event delivery, and rollback are separate acceptance criteria.

### R11 — High: dependency advisories require triage

Fresh `npm audit` on `frontend/` reports **6 affected packages: 2 high, 4 moderate**: zod and postcss (high), next, tailwindcss, postcss-nested and postcss-selector-parser (moderate). These counts are registry advisory results, not proof of exploitability in this application. Suggested upgrades include major versions; do not run blind force-fix. Review advisory reachability, upgrade deliberately, and repeat build/type checks/auth/attendance tests. No Maven vulnerability database scan was performed.

### R12 — Medium: role resolution and login performance need measured consolidation

`resolveUserRole` makes multiple database calls and swallows several database failures, causing infrastructure failures to appear as missing profile/role. Middleware, layouts, and pages can repeat authentication/role work. Faculty/college screens issue multiple queries and load broad datasets.

Action: request-scoped memoization, typed unavailable-vs-denied outcomes, safe structured timings, pagination, connection budgets and indexes measured using query plans. Do not cache authorization across users or indefinitely after revocation. Historical latency reports are not current measurements.

### R13 — Medium: onboarding reports simulated progress as completed steps

`frontend/src/app/api/platform/onboard-tenant/route.ts:110` returns `success:true` and several `COMPLETED` saga steps when writes were deliberately gated. The live path uses separate SQL operations; a literal approval header is not a durable authorization or workflow state.

Action: expose explicit validated/planned/persisted statuses; require authoritative platform authorization, idempotency and resumable transaction/saga state. Validate that invitation, identity creation, role assignment and tenant activation truly complete before showing success.

### R14 — Medium: delivery and validation are not reproducible end to end

No tracked GitHub Actions workflows were found. Next.js has no standard test script, though useful verification scripts exist. Many scripts depend on external staging systems and may mutate data. Full Maven verification still encountered local PostgreSQL connection failures; the successful run excluded live targets. Full schema creation, migration application, backups/restores, deployment secrets and live RLS were not independently reproduced.

Action: CI for both clients and Java, ephemeral database integration tests, complete ordered schema migration baseline, secure fixture creation, migration/rollback rehearsal, authenticated browser journeys and backup restoration. Never reuse production credentials in test suites.

### R15 — Medium: audit documents contain stale or contradictory claims

Earlier reports call the student page static, but this branch now has database queries. Claims of all gaps resolved conflict with the current source. Existing documentation also describes live secrets/rotation and deployment state that cannot be verified from source alone.

Action: use a single dated evidence register tied to commit, command, environment and result; mark historical evidence clearly. Check previously reported credential exposure through the service's secure settings and rotate if needed; no secret values are reproduced in this report.

## What is done, folder by folder

| Folder | Implemented | Pending / release evidence needed |
|---|---|---|
| `frontend/src/app/(auth)` | Password login, recovery request/reset UI, callback handling | Real email/recovery journey, account abuse controls, browser/accessibility regression |
| `frontend/src/lib/auth`, middleware | Database-backed role resolution, inactive/deleted/suspended guards, routing | Remove unsafe test identity path; consistent scope and DB-error handling |
| `frontend/src/app/(dashboard)/platform` | Overview, institute registry, onboarding wizard, audit | Atomic real provisioning, reliable invitation/activation, live authorization tests |
| `frontend/src/app/(dashboard)/college` | Departments/programs, faculty/student rosters, batch views, timetable, attendance/corrections, audit | College boundaries, complete transactional mutations, pagination and live acceptance |
| `frontend/src/app/(dashboard)/faculty` | Timetable, sessions, attendance interface | Authoritative employee linkage and per-assignment authorization |
| `frontend/src/app/(dashboard)/student` | Database-backed profile and attendance summary/history | Correct enrollment denominator; remaining student workflows |
| `frontend/src/components` | Shared shell/navigation, preferences, UI controls | Consistent error/empty/loading states; keyboard/screen-reader/mobile testing |
| `frontend/src/app/api` | 13 route-handler files for auth, preferences, onboarding and college workflows | Transactions, scope checks, limits, negative API tests |
| `frontend-web` | Token-aware Java client, explicit mock mode, role-template UI/tests | Separate product/support decision; authenticated Java integration |
| `api-gateway` | Routing, correlation, JWT/JWKS, corrected structured claim parsing/regressions | Business-role contract, complete downstream trust boundary; load testing |
| `logger` | Async logging, masking, trace/audit APIs and tests | Secure/disable unauthenticated management API; per-process ports/paths and durable audit policy |
| `services/institute-admin-service` | Broad repositories, role templates, provisioning state machine, validation | Live PostgreSQL/RLS, transaction/recovery acceptance, schema reproducibility |
| `services/college-admin-service` | Department/program/governance repositories and domain tests | Cross-college authorization and durable full workflow coverage |
| `services/course-management-service` | JDBC course persistence and tenant isolation regressions | Live restricted-role/RLS and restart tests |
| `services/curriculum-service` | Versioning/domain rules/tests | Durable repositories and production integration |
| `services/subject-management-service` | Subject lifecycle, versions, outcomes/prerequisites, tests | Authentication, tenant boundaries, persistence, real events |
| `services/batch-management-service` | Batch/domain operations and tests | Persistence and real roster/student references |
| `services/timetable-management-service` | Timetable/domain validation and tests | Durable schedules, real resource/faculty integrations |
| `services/attendance-service` | Attendance rules and HTTP/domain tests | Durable records, identity/roster integration, production authorization |
| `services/academic-calendar-service` | Calendar rules and tests | Persistence and institutional policy integration |
| `services/learning-resource-service` | Metadata/access-policy/domain tests | Real file storage, verified downloads, durable metadata |
| `services/assessment-mapping-service` | Mapping/domain/reference abstractions | Real references, durable mappings and assessment integration |
| `services/analytics-service` | Projections, rules, export implementation/tests | Reliable source events and valid production export formats |
| student/faculty/identity/examination/notification/AI Java folders | Placeholder directories | Do not count these as implemented Java services; Next.js implements some student/faculty features separately |
| `database` | PostgreSQL incremental/rollback scripts, staging seeds, MongoDB artifacts | Full baseline, actual grants/RLS verification, migration runner and restore test |
| `infrastructure` | MongoDB Compose and scaffold folders | Supported deployment, monitoring, secrets, backups, incident procedures |
| `platform`, `mobile-app` | Scaffolding | No implemented mobile app or shared platform service stack |
| `scripts` | Local regression and staging/browser/provisioning helpers | Separate read-only/local tests from external mutations; CI orchestration |
| `docs`, root reports, `.planning` | Extensive architecture, backlog, handover and audit material | Resolve stale claims and duplicate status sources |

## Validation performed on this branch

- Next.js: frozen `npm ci`; **production build passed**, all routes compiled; `npx tsc --noEmit` passed. Login HTTP 200. This does not verify database-backed page results.
- Vite: frozen `npm ci`; **11/11 tests passed**; production build passed; lint completed with warnings.
- Auth regression: **66/66 checks passed** using the repository's mocked DB loader. These include source assertions and mocked behavior, not live Supabase authentication.
- Test-session gate: **7/7 passed**; explicit local exploit demonstration returned unauthenticated SUPER_ADMIN in enabled mode.
- Java: unrestricted `clean verify` hit absent PostgreSQL and was stopped. Scoped `verify` built the reactor/JARs: **932 tests reported, 926 passed, 6 skipped, 0 failures/errors**, excluding `Postgres*` and `StagingPrerequisitesExecutionTest`. Java 21 required `--add-opens java.base/java.net=ALL-UNNAMED` for existing reflective HTTP tests.
- Dependencies: Next.js app npm audit reported 2 high/4 moderate affected packages.
- No production/staging writes, live database verification, load testing, full accessibility audit, or all-page authenticated browser testing were performed.

Reproduce Java scoped build:

```bash
mvn -s /workspace/.campx-setup/maven-settings.xml -B -ntp \
  -Dmaven.repo.local=/workspace/.cache/m2 \
  '-Dtest=*,!Postgres*,!StagingPrerequisitesExecutionTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '--define=argLine=--add-opens java.base/java.net=ALL-UNNAMED' verify
```

## Recommended delivery order and acceptance gates

1. **Security gate:** remove public impersonation; enforce tenant/college/assignment scopes; fix TLS trust; verify least-privileged database role. Demonstrate anonymous, forged-cookie, wrong-tenant, wrong-college and wrong-faculty denials.
2. **Academic integrity gate:** transactional attendance/corrections/audit/outbox; real roster validation; consistent enrolled-session denominator and status rules. Demonstrate rollback, concurrent writes, restart survival and correct reports.
3. **Complete one supported MVP:** platform onboarding -> college setup -> real faculty/student identities -> roster -> timetable -> attendance -> correction -> student report. A successful journey must use real persistence and no silent demo fallbacks.
4. **Operational gate:** full migrations on a clean test database, actual RLS/partition tests, CI, dependency remediation, backup restoration and monitored deployment.
5. **Expand the product after those gates:** student admissions/profile lifecycle, examinations/results, fees/payments/refunds, guardian portal, notifications, document workflows, and only then additional modules such as hostel/transport/library/mobile as agreed product scope. Presence of schema names or empty directories is not feature completion.

Do not use a single percentage as a readiness score. Track each feature as source implemented, locally tested, database verified, browser verified, and release approved. The current evidence supports local builds and a partial MVP; it does not support unrestricted production readiness.
