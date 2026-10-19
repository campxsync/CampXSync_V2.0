# DEC-TBD: Java Backend Integration Baseline and Architecture Direction

- **Status:** Proposed (pending owner approval)
- **Date:** 2026-10-19
- **Branch:** `feature/java-backend-integration`, created from `ReviewBranch` at commit `ffea85d`
- **Suggested location:** `docs/architecture/` (assign the next free DEC number before committing)

## 1. Context

CampXSync was designed with Java microservices as the authoritative business backend behind one shared API Gateway. During MVP delivery, the Next.js application came to query PostgreSQL directly (`frontend/src/lib/db.ts` and the route handlers under `frontend/src/app/api/**`), bypassing the Java services and the gateway. This record sets the direction for bringing the architecture back on track without losing the MVP work.

A read-only baseline audit (2026-10-19) and a baseline test verification run established the facts in section 4.

## 2. Decisions

1. **Java owns business logic.** Core business rules live in Java services, not in individual clients.
2. **One shared API Gateway** exposes versioned, reusable APIs. Next.js web, Android, iOS, partners and AI agents consume the same APIs.
3. **Supabase PostgreSQL is the database.** It replaces MongoDB (owner-confirmed). Java services persist to Supabase.
4. **Kafka, Redis and the rest of the Data & Integration layer are deferred.** They are not approved or required at this stage and are not to be assumed.
5. **Java services provide inputs to the Shared Services & Intelligence layer** (Audit & Compliance, Notification, Context, Workflow and the other engines). The mechanism is to be designed.
6. **Student is a separate core module.** It owns student identity, admission and enrolment, and lifecycle. Other modules reference students by ID and keep their own student-related transactions. Existing `stu`, `core` and `iam` structures are mapped before any new tables or services are created.
7. **UI integration proceeds in parallel**, calling Java-exposed APIs. The existing Next.js MVP stays usable while migration is incremental, one domain at a time.
8. **Tenant isolation, authorization and production safety are mandatory.** The production Supabase project is never accessed from development or test work.
9. **Baseline:** `ffea85d`, chosen because it holds Java, security, login-hardening and UI work that exists on no other branch. `origin/main` (`9207066`) and `Dev1.0Branch` (`2eec86b`) have identical source trees and are older.

## 3. Non-goals for this branch (for now)

- No Kafka, Redis or other Data & Integration layer work.
- No wholesale merge of other branches.
- No changes to the production Supabase project.

## 4. Verified baseline facts (at `ffea85d`)

Verified by a local offline run; no database or remote service was contacted.

- Java: 924 tests passed, 0 failed, 3 skipped, across 14 Maven modules (927 executed, using selected in-memory test classes).
- Node: `verify_login_hardening.mjs` 66/66, `verify_password_recovery.mjs` 44/44, `verify_test_session_gate.mjs` 7/7.
- Frontend: `tsc --noEmit` reported 0 errors; `npm run build` completed.

**Not verified:** 244 of the 310 historically reported frontend checks (they need a running server, Supabase staging, browsers or deployed URLs); the live `Postgres*` repository integration tests; all database-level RLS behavior.

## 5. Known gaps carried forward from the audit

- Nine Java academic services use in-memory storage and lose state on restart.
- Several services default to an admin role when unauthenticated; this must be removed as each service is migrated.
- Only two services have OpenAPI contracts; the gateway has none.
- The gateway has no rate limiting or load balancing, and is not running on the deployed (Vercel) environment.
- The Next.js layer connects with a database role that bypasses RLS; tenant isolation there relies on application-level `tenant_id` predicates.
- Java runs on Java 1.8 with the JDK built-in HTTP server (not Spring Boot).
- Shared Services have no deployable implementation yet.

## 6. Open decisions

1. Java stack: remain on Java 1.8 and the built-in server, or move to a current Java version and framework.
2. Hosting for the gateway and services, in the same region as Supabase to avoid added latency.
3. Authorization to create a dedicated non-bypass application database role (`NOBYPASSRLS`) on staging, with the script reviewed first.
4. Authorization for any staging-connected test runs.
5. Order of migration after the first slice (proposed: ACD-01 Course Management, then ACD-06 Attendance, then ACD-05 Timetable).

## 7. First slice

Contract-first: publish an OpenAPI specification for ACD-01 Course Management and prove the path UI, gateway, Java service, Supabase end to end with tenant and role enforcement. The Next.js change is additive (an API client layer switched per domain), to avoid conflicts with MVP work continuing on `ReviewBranch`.