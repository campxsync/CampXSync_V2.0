# CampXSync — Complete Validation Register & Testing History

**Document Identifier**: `docs/audit/CAMPXSYNC_VALIDATION_REGISTER.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary & Validation Overview

This register inventories all automated test suites, Playwright end-to-end browser tests, security compliance scans, accessibility audits, database catalog verifications, and performance benchmarks executed throughout the project's history.

Per Section 8 and Section 15.F of the audit specification:
- **Zero new live tests, builds, or benchmark scripts were executed during this audit**.
- All reported metrics and pass counts are derived directly from existing committed test logs, execution logs (`docs/staging/05-execution-log.md`), and recorded verification scripts.
- Tests that executed against local in-memory stubs are strictly distinguished from tests executed against Supabase Staging or live Vercel deployments.
- Tests that were skipped offline due to environment guards are recorded as **`SKIPPED`** and are **never counted as passed**.

```
Consolidated Active Next.js Verification Baseline:
├─ Automated Test Suites Executed: 11 distinct test scripts
├─ Total Automated Checks Executed: 283 tests
├─ Total Checks Passed: 283 tests (100% pass rate across executed suites)
├─ Total Failures / Errors: 0
└─ Production Build: 34/34 routes cleanly compiled (npx tsc: 0 errors)
```

---

## 2. Comprehensive Validation Activity Inventory

### 2.1 Automated Next.js & Playwright Test Suites (Active Cloud Baseline)

| Suite ID | Test Suite Name & Objective | Execution Date | Execution Command / Procedure | Environment Tested | Test Double / Target | Tests Run | Pass | Fail | Skip | What It Actually Proves | What Remains Untested | Source Log / Script Path |
| :---: | :--- | :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| **VAL-01** | Phase 4 Platform Onboarding Saga | 2026-10-16 | `node scripts/verify_phase4_onboarding.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 19 | 19 | 0 | 0 | Validates 5-step onboarding wizard, duplicate detection, and transactional rollback. | Live production tenant provisioning with real DNS subdomains. | `scripts/verify_phase4_onboarding.mjs` |
| **VAL-02** | Phase 5 College Setup & Rosters | 2026-10-16 | `node scripts/verify_phase5_college_setup.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 31 | 31 | 0 | 0 | Validates department creation, faculty appointing, student admissions, and timetable grid entries. | High-volume student imports (>1,000 students via CSV). | `scripts/verify_phase5_college_setup.mjs` |
| **VAL-03** | Phase 6 Faculty Attendance & ACD-06 | 2026-10-16 | `node scripts/verify_phase6_attendance.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 37 | 37 | 0 | 0 | Proves slot deduplication, keyboard marking, ledger locking, optimistic concurrency locking, and version increments. | Biometric punch-clock integration (HRM-03). | `scripts/verify_phase6_attendance.mjs` |
| **VAL-04** | Phase 7 Dashboards, Preferences & ACD-10 | 2026-10-16 | `node scripts/verify_phase7_dashboards_preferences.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 26 | 26 | 0 | 0 | Proves statutory 75% shortage threshold detection, user preferences sync to PostgreSQL, and CSV export sanitization. | Dynamic custom institutional shortage threshold overrides. | `scripts/verify_phase7_dashboards_preferences.mjs` |
| **VAL-05** | Phase 8 Visual QA & WCAG AA Accessibility | 2026-10-16 | `node scripts/phase8_visual_qa.mjs` | Chromium Playwright | Headless browser + DOM | 46 | 46 | 0 | 0 | Proves 0 critical/serious Axe accessibility violations, WCAG AA contrast (4.5:1), and zero horizontal overflow across 3 viewports. | Screen reader user testing (NVDA / JAWS). | `scripts/phase8_visual_qa.mjs` |
| **VAL-06** | Phase 9 Security Acceptance & Browser | 2026-10-16 | `node scripts/verify_phase9_security_browser.mjs` | Chromium Playwright + Staging DB | Staging DB (`kpwdvvcschankgggitnj`) | 36 | 36 | 0 | 0 | Proves fast-fail guards (inactive, no-profile, suspended), cross-tenant IDOR protection, Platform Admin isolation, and audit logging. | Server direct SQL RLS bypass vulnerability (tested only at application layer). | `scripts/verify_phase9_security_browser.mjs` |
| **VAL-07** | Phase 10 Live Vercel Deployed Acceptance | 2026-10-16 | `node scripts/verify_phase10_deployed_browser.mjs` | Live Vercel Alias | `https://frontend-murex-one-58.vercel.app` | 10 | 10 | 0 | 0 | Proves real password login, college metrics reconciliation, faculty marking, and correction drawer live on Vercel cloud. | Production multi-tenant custom domain routing. | `scripts/verify_phase10_deployed_browser.mjs` |
| **VAL-08** | Phase 3 Gate B Role Routing Matrix | 2026-10-16 | `node scripts/verify_role_routing.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 20 | 20 | 0 | 0 | Proves Edge Middleware routing rules: `/platform`, `/college`, `/faculty`, `/student` prefix guards. | Dynamic multi-role switching within a single session. | `scripts/verify_role_routing.mjs` |
| **VAL-09** | Login Hardening & Server Role Resolution | 2026-10-19 | `node scripts/verify_login_hardening.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 58 | 58 | 0 | 0 | Proves authoritative role resolution against PostgreSQL, removal of email-prefix fallbacks, and fail-closed tenant checks. | Expired token refresh lifecycle under poor network connectivity. | `scripts/verify_login_hardening.mjs` |
| **VAL-10** | Independent Credentials Verification | 2026-10-19 | `node scripts/verify_independent_accounts.mjs` | Local server + Supabase Staging | Staging DB (`kpwdvvcschankgggitnj`) | 7 | 7 | 0 | 0 | Proves separate passwords and independent account authentication for College Admin, Platform Admin, and Faculty. | Student self-registration and credential onboarding. | `scripts/verify_independent_accounts.mjs` |
| **VAL-11** | Faculty Staging Linkage & Deployed Guards | 2026-10-19 | `node scripts/verify_faculty_auth_and_routing.mjs` | Vercel Preview | `https://frontend-n34dbtn83-camp-xs-ync.vercel.app` | 12 | 12 | 0 | 0 | Proves faculty authentication and timetable landing page access on deployed preview environment. | High concurrency multi-faculty simultaneous marking. | `scripts/verify_faculty_auth_and_routing.mjs` |

---

### 2.2 Login Performance Investigation Stream (October 18–19, 2026)

- **Objective**: Diagnose and resolve an observed ~6-second delay during user login.
- **Investigation Command**: `node scripts/measure_login_performance.mjs` and `node scripts/probe_resolve_role_latency.mjs`
- **Environment Tested**: Vercel Preview URL (`https://frontend-murex-one-58.vercel.app`) using Vercel Automation Bypass Header, backed by Supabase Staging (`kpwdvvcschankgggitnj`).
- **Target Accounts**:
  - College Admin: `campxsync@gmail.com`
  - Platform Admin: `platformadmin.campx@gmail.com`
  - Faculty: `faculty.campx@gmail.com`
- **Recorded Measurements & Comparisons**:

| Role Tested | Pre-Fix Cold Load | Pre-Fix Repeat Median | Post-Fix Cold Load | Post-Fix Repeat Median | Latency Speedup |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **College Admin** | 17,037 ms | 12,544 ms | 5,371 ms | 2,880 ms | **77% (9.7s improvement)** |
| **Platform Admin** | 9,583 ms | 9,137 ms | 2,262 ms | 2,145 ms | **76% (7.0s improvement)** |
| **Faculty Member** | 11,284 ms | 10,757 ms | 2,380 ms | 2,675 ms | **75% (8.1s improvement)** |

- **Empirical Bottleneck Discovered**:
  - Supabase GoTrue Auth token issuance was fast (~307ms–344ms).
  - Wasted latency was caused by `resolveUserRole()` sequentially executing 4 to 5 Supabase PostgREST calls against private schemas (`plat`, `iam`, `core`). PostgREST rejected each call with HTTP 404 (`PGRST106`), wasting 1,500ms–2,500ms per invocation.
  - A double-hop navigation (`/` -> HTTP 307 -> `/college`) caused `resolveUserRole()` to execute twice, compounding the delay into 6–17 seconds.
- **Implemented Fix**: Replaced failed PostgREST calls with direct parameterized SQL queries via `@/lib/db`.
- **Statistical Limitations & Caveats**:
  1. Exactly 10 runs per role were conducted. 10 observations provide limited statistical confidence for p95/p99 SLA guarantees.
  2. The initial "cold" measurement represents the first browser navigation in a Playwright session and does not necessarily isolate a true Vercel Lambda serverless cold start.
  3. Measurements were conducted using a bypass token; end-user performance over varied institutional cellular/broadband networks remains uncharacterized.

---

### 2.3 Legacy Java Microservices Test Baseline (Historical Reference)

As documented in `docs/testing/TEST_RESULTS.md` and `docs/handover/HANDOVER.md`:

| Java Module | Test Suite Name | Environment | Tests Run | Pass | Fail | Skip | Evidence Notes |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `logger` | `campx-logger` Unit Suite | JVM Local | 15 | 15 | 0 | 0 | Async logger, flow tracker, HMAC signing. |
| `institute-admin` | ADM-01 Unit/Validation Suite | JVM Local | 293 | 293 | 0 | 0 | In-memory validation tests. |
| `institute-admin` | Role Template Controller HTTP Test | JVM Local | 12 | 12 | 0 | 0 | Real HTTP server socket tests. |
| `institute-admin` | Role Template Repository Validation | JVM Local | 33 | 33 | 0 | 0 | Offline schema validation. |
| `institute-admin` | `PostgresCollegeRepositoryIntegrationTest` | Staging DB | 1 | 0 | 0 | 1 | **SKIPPED OFFLINE** (TestEnvironmentGuard). |
| `college-admin` | ADM-02 Full Unit/Regression Suite | JVM Local | 82 | 82 | 0 | 0 | Core domain logic tests. |
| `college-admin` | ADM-02 Live Postgres Repositories | Staging DB | 31 | 0 | 0 | 31 | **SKIPPED OFFLINE** (TestEnvironmentGuard). |
| `course-mgmt` | ACD-01 Full Unit/Regression Suite | JVM Local | 68 | 68 | 0 | 0 | Course domain tests. |
| `course-mgmt` | ACD-01 Live Postgres Repositories | Staging DB | 22 | 0 | 0 | 22 | **SKIPPED OFFLINE** (TestEnvironmentGuard). |
| `curriculum` | Curriculum Domain Service Test | JVM Local | 21 | 21 | 0 | 0 | DAG rules (In-Memory). |
| `subject-mgmt` | Subject Management Service Test | JVM Local | 32 | 32 | 0 | 0 | In-memory ConcurrentHashMap tests. |
| `api-gateway` | SupabaseJwtClaimParsingRegressionTest | JVM Local | 29 | 29 | 0 | 0 | AST claim parsing regression. |
| **Total Java Suite** | **Historical Legacy Baseline** | **Mixed** | **649** | **595** | **0** | **54** | **54 live database tests cleanly skipped**. |

---

## 3. Quality & Security Gates Summary

- **TypeScript Compilation**: `npx tsc --noEmit` executed with **0 errors**.
- **Next.js Production Build**: `npm run build` compiled all 34 routes cleanly in 11.1s (Phase 10) and 23.6s (Login Hardening).
- **Rule 11 Staging Compliance**: `Rule11_overall: True` (`ContainsProdRef = False` across all inspected variables and code).
- **Service Role Audit**: Verified zero `service_role` keys or tokens present in frontend client or server source files.
- **Axe-Core Accessibility Violations**: Exactly **0 serious or critical accessibility violations** across all audited screens.
