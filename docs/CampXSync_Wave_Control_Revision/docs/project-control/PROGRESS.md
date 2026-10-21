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
- **G1 least-privilege DB role:** NOT VERIFIED in this checkpoint. Before shared-DB persistence or DB writes, verify a dedicated app login role without `BYPASSRLS`, transaction-scoped tenant/user context and correct non-production target. If open, continue safe code/design/unit tests only. Do not claim tenant isolation verified.
- **G2 isolated PostgreSQL CI suite:** OPEN / to be assessed by end of Wave 5. No claim that CI is already configured.
- **G3 JDK/framework assessment:** Not started; due before Wave 11 exit. Java 8 unchanged unless separately approved.

## Wave 4 next actions
1. Inspect current Git status and the five control files; preserve all existing work.
2. Read current `NEXT_ANTIGRAVITY_TASK.md` and relevant Wave 3 evidence; avoid broad repeated audits.
3. Check G1 evidence and record actual status.
4. Prepare student ownership/model proposal and obtain written approval before implementing that model.
5. Reuse existing `stu.students`, `core.persons`, `stu.guardianships`, IAM and shared audit/outbox where current code/schema supports it. Avoid duplicate identity/credentials/guardian/document/workflow models.
6. If G1 is open, continue safe design/code/unit tests; no DB writes or claims of live persistence/RLS proof.
7. Implement only approved Wave 4 scope; test at strongest safe tier; update report/checkpoint from evidence; stop.

## Safeguards
Production is off-limits. Every DB write/migration/grant/RLS/seed/provisioning step requires exact non-production target verification and explicit approval. Preserve uncommitted/untracked Waves 1–3 work. No commit/push without explicit authorization. Never claim unrun DB, browser E2E, RLS or Intelligence Layer tests passed.
