# CampXSync Java Backend Integration — Progress

**Updated:** 2026-10-21  
**Branch:** `feature/java-backend-integration`

> Based on user-provided execution reports and recorded context; not an independent re-verification of current repository or live database.

## Current checkpoint
- Last recorded committed Wave 0 baseline: `105122b6b5b17d3eadc70e7e8094008facb4285a`.
- Waves 1–3 reported complete/accepted; their uncommitted changes must be preserved.
- **Next: Wave 4 — Student Domain Foundation & Roster.**
- Revised sequence: W4 Student → W5 Timetable → W6 Attendance → W7 Corrections → W8 Dashboards → W9 Onboarding → W10 Frontend DB retirement/E2E → W11 Production readiness.
- No reset, clean, stash, branch switch, commit or push without explicit user authorization.

## Wave 1 — Shared Audit & Outbox Foundation
**Reported status:** Accepted with explicit limitations. Logger tests 58/58; `mvn clean install -pl logger` reported successful; downstream test compilation for `api-gateway` and `services/attendance-service` reported successful. Six correctness fixes were reported. Live PostgreSQL/RLS integration was not verified; background outbox relay remains deferred.

## Wave 2 — Preferences
**Reported status:** Complete with limitations. Preferences 26/26; gateway routing 6/6; logger 58/58; institute-admin tests 79/79; full `mvn test-compile` reported successful across 15 modules. Live DB integration and real Intelligence Layer endpoint remain pending/unverified.

## Wave 3 — Academic Departments and Programs
**Reported status:** Complete with live integration unverified. Reported: shared services 11 tests, controller 14, gateway 7, CollegeAdminService 39, tenant isolation regression 15, downstream security 7, API gateway 155; `mvn test-compile` reported successful across 15 modules. No production or legacy staging DB connection/writes were reported. Consult the Wave 3 completion report for details.

## Gates
- **G1 least-privilege DB role:** PASSED. Implemented and supported by live non-production test evidence on development ref `azulbaxszsqxymqmlwfe`:
  1. Dedicated unprivileged application role `campx_app_server` active (`LOGIN`, `NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`, membership in `authenticated`).
  2. `FORCE ROW LEVEL SECURITY` verified live on all Tier 1 Core tables (`stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`).
  3. Least-privilege grant matrix applied: schema `USAGE` to `campx_app_server`, table DML to `authenticated`, `SELECT, INSERT` on `audit.events` to `authenticated`.
  4. Append-only ledger enforced: `UPDATE` and `DELETE` on `audit.events` denied (`SQLSTATE 42501`).
  5. RLS policy `events_insert` on `audit.events` active and verified.
  6. Live multi-tenant cross-boundary isolation verified: positive path succeeds, cross-tenant audit & student inserts denied, missing context fails closed.
  Student-domain persistence writes are now unblocked.
- **G2 isolated PostgreSQL CI suite:** OPEN / to be assessed by end of Wave 5. No claim that CI is already configured.
- **G3 JDK/framework assessment:** Not started; due before Wave 11 exit. Java 8 unchanged unless separately approved.

## Wave 4 — Student Domain Foundation & Roster
**Current status:** Stage A Complete (Gate G1 PASSED). Ready for Phase 2: Student Domain Model & Architecture Proposal.
- Audit report: `docs/reports/wave-4/STUDENT_DATABASE_RLS_AUDIT.md` (Phase 1).
- Remediation proposal: `docs/reports/wave-4/G1_SECURITY_REMEDIATION_PROPOSAL.md` (Stage A).
- Remediations executed and verified live:
  * `02_application_role_proposal.sql` (`DEC-G1-01` under `APPROVE-G1-ROLE-CREATION`).
  * `04_rls_hardening_proposal.sql` Block 1 (`DEC-G1-02A` under `APPROVE-G1-FORCE-RLS-TIER1`).
  * `03_minimum_grants_proposal.sql` (`DEC-G1-03` under `APPROVE-G1-GRANTS`).
- All 4 live test suites passed (`LiveG1DatabasePreflightTest`, `LiveG1RoleCreationExecutionTest`, `LiveG1Tier1RlsExecutionTest`, `LiveG1GrantsExecutionTest`).
- Zero Java backend persistence code implemented yet for Student module.
- Next Phase: Wave 4 Phase 2 — Student Domain Model, Architecture, and Contract Proposal.

## Wave 4 next actions
1. Prepare Wave 4 Phase 2 Student Domain Model & Architecture Proposal for owner review.
2. Define API contracts for Student Service (UI -> Gateway -> Java Student Service -> Supabase).
3. Define versioned Intelligence Layer events/contracts for Student Module.
4. Implement Java domain entities, repositories, and services with `UserSecurityContext` tenant-isolation.

## Safeguards
Production is off-limits. Every DB write/migration/grant/RLS/seed/provisioning step requires exact non-production target verification and explicit approval. Preserve uncommitted/untracked Waves 1–3 work. No commit/push without explicit authorization. Never claim unrun DB, browser E2E, RLS or Intelligence Layer tests passed.
