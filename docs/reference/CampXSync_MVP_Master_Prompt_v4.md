# CAMPXSYNC — MVP MASTER EXECUTION PROMPT v4 (ROLE WORKSPACES + PRESERVED PREFERENCES + PROFESSIONAL UI + VERCEL)

Paste this whole file into Antigravity with GSD active. This v4 supersedes v3 for this execution. It merges the current MVP execution scope with the `product_lifecycle.md` governance baseline and the relevant AllModules design intent. Preserve prior repository/checkpoint evidence; do not restart completed work.

**How the three sources are used**
- **This file = what to execute now.** One vertical slice, built through the real architecture.
- **`product_lifecycle.md` = governance reference** (copy it to `docs/reference/product_lifecycle.md`). Consult it for decisions, gates and definitions. **Do NOT execute its roadmap phases 0–6.** This slice is its "Phase 2 — MVP vertical workflow", with the minimum Phase 0/1 foundations it needs.
- **AllModules design package = design intent**, not proof of implementation. Read ONLY the specs named in Section 3 for MVP implementation. Do not implement other modules. The role/dashboard and preference architecture must be product-wide in design and traceability, but this MVP implements only the frozen roles and workflows below.

**Authority order when sources disagree** (record every conflict in `docs/trial/DECISIONS.md`):
1. Environment-safety and production-isolation rules (Section 1)
2. Current approved architecture decision and security/data-ownership policy
3. Repository implementation, migration history, test evidence (what exists today)
4. Canonical service specification (AllModules)
5. Superseded/duplicate designs and diagrams
6. AI-generated interpretations or unverified status claims

---

## RUN MODE

After each phase print the PHASE REPORT (Section 9) and **continue automatically**. Do not wait for acknowledgement.

**STOP and ask only when:**
- (a) the verified runtime configuration or deploy target resolves to the protected production Supabase project;
- (b) the slice needs a schema change (write the proposal to `supabase/migrations_proposed/`, then wait for `GO MIGRATION-<NAME>`);
- (c) a credential or authorization is missing (report the exact manual action; continue all independent work);
- (d) a gate fails and you cannot fix it within scope after two honest attempts;
- (e) the Phase 1 checkpoint;
- (f) a staging database write needs its approval phrase (Section 1, rule 4). **While waiting, continue independent UI and code work.** A blocked write must never become a production write.

**No ritual repetition:** do not rerun completed checks, scripts or migrations without a concrete reason (changed code/config, new failure, or a gate that needs fresh evidence). Inspect recorded evidence first, then proceed to the next actionable item.

**INTERRUPTION RECOVERY AND RESUMPTION**

- After every meaningful implementation step, save working changes and update the existing execution checkpoint/report before proceeding. Preserve all user changes; never discard or overwrite unrelated work.
- Maintain a durable resume record in the existing project checkpoint files, recording the current phase and subtask, completed work, files changed, commands/tests and results, pending actions, blockers/required approvals, and the single next action. Reuse existing checkpoint files where available; do not create duplicate tracking systems.
- If execution is interrupted, fails, or approaches a session/context limit, save the latest possible checkpoint and report the exact stopping point, unfinished work and next action. Never claim the entire MVP is complete.
- On resumption, inspect the existing checkpoint and repository changes first. Reconcile the recorded state with the actual files, then continue from the first incomplete action. Do not repeat completed phases, tests, migrations, database writes or deployments unless a concrete reason requires it.
- If a step may have partially completed, verify its outcome before retrying, especially for database writes, provisioning and deployment. Follow all existing staging-target verification and explicit approval gates. Never assume a failed tool response means no changes occurred.
- Continue automatically after recovery wherever permitted. Stop only for the existing mandatory approval gates, missing credentials/authorization, protected-production safety rules, or unresolved blockers specified in this prompt.

## 0. MISSION AND DEFINITION OF "MVP READY"

Make CampXSync **trial-ready and live on Vercel by Monday 2026-10-19**: a working, role-aware, professionally designed web app on STAGING data that real users can use for ONE end-to-end institutional workflow, built through the real architecture (identity, tenant, RLS, domain ownership, audit, contracts) with no shortcuts inside the slice.

**Scope principle: architecture-complete, scope-thin.** Everything the slice touches is built the architecturally correct way. Everything else stays specified-only, tracked in the traceability matrix, and is never faked.

**MVP ready means all true:**
1. Email + password login lands each role on its own home; logout works; session persists across reload.
2. A valid login without an active institutional profile/membership is denied with a clear message.
3. Each role sees only its allowed navigation; forbidden routes and API calls are denied server-side and by RLS.
4. College A data is never visible to College B (UI, API and direct RLS queries), including forged tenant ids.
5. The full flow in Section 2 works on the deployed Vercel URL against the real staging backend.
6. Attendance follows ACD-06 rules: valid timetable slot, roster membership, duplicate rejection, lifecycle, audited corrections.
7. UI passes the professional-quality checklist in Section 5, with screenshot evidence.
8. Each MVP role has a genuinely distinct, permission-aware dashboard and navigation; shared design tokens/components keep every implemented screen visually coherent.
9. User workspace preferences persist safely according to Section 5.1; settings for one role, user or tenant cannot overwrite or expose another scope.
10. Trial pack, traceability matrix, decisions register, known limitations and defect log exist.

**Visual completion and functional completion are separate criteria.** A screen that looks finished but does not work counts as NOT done.

## 1. HARD RULES (never violate)

1. **Staging only.** Supabase staging ref `kpwdvvcschankgggitnj`.
   **The production Supabase project is protected. Never connect to it, read from it, write to it, or deploy against it. Do not copy its project ref into new repository files, environment files, scripts or logs. Resolve the protected ref from the existing approved safety checkpoint/configuration without echoing it.** Before every approved write step verify the project ref and DB host from independently checked runtime configuration (read-only), without printing credentials. Environment names alone are not proof. If only production credentials are available, stop the write path, document the blocker, continue independent work.
2. Workspace `d:\CampXSync\CampXSync_V2.0`, branch `ReviewBranch`. Windows/PowerShell commands only.
3. **Supabase Auth only** for identity. No custom users table or password handling.
4. **Staging database write approvals.** Admin-level writes (seed scripts, tenant provisioning, migrations, data repair) require the exact approval phrase from me, scoped to that step only: `GO SEED-STAGING`, `GO PROVISION-TENANT`, `GO MIGRATION-<NAME>`. (If my active execution plan defines different phrases, use those.) Read-only checks need no approval but are not write approval. Writes made by signed-in users through the app (adding faculty, marking attendance) are application writes and are allowed on the seeded demo tenants after `GO SEED-STAGING`.
5. **Reuse the existing IAM model** (iam.user_profiles, roles, permissions, grants, `grant_scopes()`, `iam.perm_all`, `iam.perm_department_ids`, `iam.has_permission`). No new RBAC system.
6. **Keep RLS intact.** Never disable it, never bypass authorization to make a feature work, never use the service-role key in browser or app runtime code.
7. **One authoritative owner per business fact.** Do not write into another domain's tables to shortcut. No shadow student, faculty, course, subject, batch or timetable masters. Use stable IDs and the owning domain's contract.
8. **No schema changes by default.** Never drop or alter existing objects.
9. **Secrets** only in `.env.local` (git-ignored) and Vercel protected settings. Never commit, print or screenshot them. Provide `.env.example` with names only.
10. Respect `deleted_at` soft delete on every read, search, report and cache.
11. **Git:** do not commit or push unless I explicitly authorize it. Keep `docs/trial/CHANGES.md` updated with files changed per phase. (If a Git-connected Vercel deploy needs a push, ask me once in Phase 10.)
12. **Evidence over claims.** Record tests as PASS, FAIL, BLOCKED or NOT RUN. A skipped test is not a pass. Never claim a live deployment without evidence. Never mark specified work as implemented.
13. Small verifiable steps. After each step run lint, typecheck and build, and fix before moving on. No fallback to shared or in-memory state that could leak data across tenants.
14. REAL DATABASE PERSISTENCE — MANDATORY FOR EVERY MVP WORKFLOW. Supabase/PostgreSQL must be the authoritative source of truth for all persisted business data. Trace every implemented create, update, delete, attendance submission, correction and dashboard query through the UI, server/API and repository layers to the actual database tables, views or functions. No mock business data, hardcoded business records, in-memory repositories, shared mutable fallback state or browser-only storage may substitute for authoritative business persistence. Caches and derived summaries are permitted only as non-authoritative optimisations. Verify that successful writes are confirmed by the backend, persisted data survives reload and a fresh session, and dashboard figures reconcile with authorised database sources. Enforce RLS, server-side permissions, tenant isolation and soft-delete rules. Preferences may use an explicitly disclosed non-persistent fallback only when durable storage is unavailable; never claim durable persistence without evidence. Record PASS, FAIL, BLOCKED or NOT RUN for each applicable workflow, citing repository files, database objects, tests and runtime evidence. Do not repeat completed checks without a concrete reason. Never connect to or modify the protected production Supabase project; follow all existing staging verification and write-approval gates.
15. If `CodeRabbit` (or the configured review tool) is available, run it at the end of Phases 4, 6 and 9; classify findings and fix or document them.

## 2. THE SLICE (scope frozen)

**Roles:** Platform Admin (CampSync tier), College Admin (tenant tier), Faculty. *Stretch only if all else is green:* Student (read-only own attendance).

**Dashboard and preference requirement (cross-cutting):** Every supported CampXSync role must ultimately have its own purpose-built dashboard and role-specific navigation. For this MVP, implement Platform Admin, College Admin and Faculty dashboards; Student is stretch only. Design the shared dashboard framework and typed preference contract so future roles/modules can use them without redesign. Each implemented role must have meaningful, real-data widgets and actions appropriate to its permissions—not just a different heading. Preserve user choices for dashboard widget order/visibility, table density/columns/sort/page size, supported default filters/date range, theme, sidebar state, locale/timezone and accessibility display options as applicable. Scope preferences by authenticated user and role, with module scope where needed; never treat preferences as permissions. Preference persistence must survive reload and re-login when backed by an approved persistence path. Do not add/alter database schema without the proposal and `GO MIGRATION-<NAME>` gate. If durable persistence is blocked by schema approval or missing backend support, implement the typed interface/defaulting and UI flow, document the exact blocker, and do not claim durable persistence.

**The flow:**
1. **Platform Admin** onboards a college (institute). The onboarding follows the ADM saga: institute created in `onboarding` status → tenant-side steps (configuration seeded from platform defaults, first College Admin user created, default role granted) → status `active` when the admin is provisioned. Failures leave `onboarding` visible with a retry path.
2. **College Admin** logs in, sees only their college. Views batches/sections, subjects, and the published timetable (read-only). Adds faculty and students, assigns students to batches and faculty to timetable entries, **using existing domain tables and contracts**.
3. **Faculty** logs in, sees today's timetable slots, opens a valid slot, takes attendance for the batch roster, submits.
4. **College Admin** sees attendance dashboards (read-optimized summaries), shortage flags, missed sessions, and can **correct** a submitted record with a mandatory reason; the correction trail is visible.
5. **Platform Admin** sees platform dashboard (institutes by status, users per college, onboarding status, platform audit events) without reading tenant student records.

**Out of scope for Monday:** billing, imports/exports, document governance, approvals/workflow engine, AI/intelligence layer, exams, finance/payroll, LMS, HR processes, hostel/library/transport/placement, communications delivery, support impersonation, mobile app, notifications.

## 3. ARCHITECTURE BASELINE FOR THE SLICE

Read ONLY these specs from the AllModules package (locate the folder in the workspace; if not found, STOP (c) and ask me for the path). Prefer newer versions where they exist (e.g. `ADM-01`/`ADM-02` V2.0 documents, and `relational_constraint_index_catalog.md` as the database map), and record which version you used.

| Domain | Spec | Role in the slice |
|---|---|---|
| Platform admin | CampSync Admin Module (platform) / ADM-01 | Institute registry, tenant provisioning, onboarding saga, platform audit |
| College admin | College Admin Module (tenant) / ADM-02 | Tenant config, identity, RBAC, tenant audit |
| ACD-04 | Batches / sections | Roster context |
| ACD-05 | Timetable | Valid slots and faculty assignment |
| ACD-06 | Attendance | Sessions, records, corrections, summaries |
| ACD-07 | Academic calendar | Working days/holidays, if implemented |
| ACD-10 | Reporting | Read-optimized dashboards only |
| IAM / RBAC | Existing implementation | Identity, profile, membership, permissions |

**Implementation reality (DEC-01):** the design specs mention MongoDB, Redis and Kafka; the active implementation is Supabase/PostgreSQL with a consolidated multi-schema model. Map each spec concept to the implemented table, policy and function (or record "not implemented"). Do not copy outdated persistence assumptions and do not build brokers or databases for the slice.

**ACD-06 rules the slice must honour** (adapt to the implemented schema; never invent names):
- Attendance only for a **valid timetable slot**. Session context: batch, subject, timetable entry, attendance date, period.
- **Unique business key:** tenant + batch + subject + date + period. A duplicate is rejected by the backend/database, and the UI opens the existing session instead.
- Students must belong to the batch roster. Roster comes from the owning student/batch tables.
- Statuses come from the implemented/configured set (spec: Present / Absent / Leave, plus configured equivalents such as Late or On-Duty). Render labels from configuration. Do not hardcode.
- **Lifecycle:** `DRAFT → OPEN → SUBMITTED → LOCKED → CORRECTED → ARCHIVED` (`CANCELLED` terminal). The MVP must support at least OPEN → SUBMITTED and the correction path; implement LOCKED/ARCHIVED only if present in the implementation, otherwise record as deferred. The server enforces transitions, never the UI alone.
- **Corrections:** require permission, a mandatory reason and the expected version (optimistic concurrency); append an immutable correction record preserving old and new values; recalculate the summary.
- **Idempotent writes:** retries (double click, network retry) must not create duplicate sessions or records.
- **Summaries** are derived, rebuildable and never the source of truth. Dashboards read summaries or read-optimized views, never the operational tables via heavy ad-hoc scans.
- **Events/outbox:** `AttendanceSessionCreated`, `AttendanceMarked`, `AttendanceCorrected`, `AttendanceShortageDetected`, and `InstituteOnboarded` / `CollegeAdminProvisioned`. If an outbox or event mechanism already exists, use it with the canonical envelope (event id, type, tenant id, aggregate id/version, occurredAt, schema version, correlation id; no secrets or unnecessary personal data). If it does not exist, record it as a gap and do not build a broker.
- **Audit:** privileged and consequential actions (onboarding, user/faculty/student creation, role grants, attendance submit, correction) write to the existing audit structures with actor, time, reason (where required) and correlation id. Platform audit and tenant audit stay separate.

**Student management (DEC-11):** its specification is absent. Use the implemented student tables and record the contract you relied on. If no student master exists in staging, STOP (b).

## 4. PHASES

### PHASE 1 — BASELINE DISCOVERY (read-only) — CHECKPOINT
1. Inspect the repo: frontend framework and version, whether a working frontend exists and how much, API/server layer, Supabase client pattern, current auth flow, folder layout, build command, Vercel readiness, existing tests, existing execution checkpoints and migration history.
2. Verify the env targets the staging ref only (read-only, no credentials printed).
3. **Spec-to-implementation map** for every row in Section 3: spec concept → schema.table/function/policy → status (`Specified / Implemented / Tested / Staging-verified`). Include tenant column, RLS on/off, soft-delete column.
4. **Identity chain:** how an authenticated user resolves to profile → membership → role → permissions → tenant scope. Explain in 8 lines. Review `iam.perm_all`, `iam.perm_department_ids`, `iam.has_permission`, `grant_scopes()` for intended semantics: null/empty inputs, AND-vs-OR weakening, privilege escalation, missing profile, inactive membership. List any defects found (do not fix without approval).
5. **Gap list** with the smallest proposed fix per gap: missing table/policy/seed path, no onboarding path, no timetable data, no outbox, no student master, etc. Separate "blocks the slice" from "record only".
6. **Framework decision:** reuse existing frontend vs Next.js (App Router, TypeScript, Tailwind, shadcn/ui, lucide-react, Recharts, react-hook-form + zod, `@supabase/ssr`), with the reason. Reuse unless absent or unsuitable for Vercel.
7. Start `docs/trial/DECISIONS.md` with the relevant DEC items (DEC-01, 03, 04, 08, 11 at minimum) and how the slice resolves each, citing repository evidence.
**Checkpoint:** print the report and STOP once for my review.

### PHASE 2 — UI FOUNDATION, AUTH, ROLE ROUTING, SHELL, EARLY DEPLOY
1. Set up design tokens and the component kit from Section 5 first, so every screen reuses them.
2. **Login** (`/login`): email + password through Supabase Auth, loading state, inline errors, password visibility toggle, "forgot password" (clear stub if not wired). Professional split layout (Section 5).
3. **Session and role resolution server-side** via the existing identity chain, normalised to `platform_admin | college_admin | faculty | student`. Valid login with no profile, inactive membership or suspended tenant → friendly "access not available" page (not a crash, not a blank screen).
4. **Role redirect:** `/platform`, `/college`, `/faculty`, `/student`. `/` redirects to `/login` or the role home.
5. **Route guards** server-side on every protected route and every server action/API route. Hiding a sidebar item is not authorization. Tenant comes from the security context only, never from a client value.
6. **App shell:** collapsible sidebar from one `navConfig` per role, top bar (tenant name, user menu, role badge, logout), breadcrumbs. Keep shared design tokens and components as the only visual source of truth.
7. **Preference foundation:** define a typed, versioned preference model and persistence adapter boundary. Resolve effective settings in this order: security/accessibility constraints → tenant-enforced defaults → authenticated user + role settings → user + module settings (role-scoped when needed) → product defaults. Tenant/role/user scope comes from trusted server context, not client input. Preference payloads are allowlisted and cannot grant permissions or contain secrets. Persist through an existing approved backend mechanism if available; otherwise document the schema/API proposal and wait for `GO MIGRATION-<NAME>` before DB writes/schema changes. Do not rely on browser-only storage while claiming account-synced persistence.
7. **Early Vercel pipeline check (do not wait until Phase 10):** deploy this skeleton to Vercel with staging Supabase values only, give me the exact Supabase Auth URL settings to apply, and confirm login works on the live URL. Rules in Phase 10 apply.
**Gate:** builds clean; login, shell, guards and no-profile denial work. Role routing for seeded roles is verified after Phase 3. Preference defaults/fallbacks work without saved data. **Seeding must not block UI work.**

### PHASE 3 — SEED DATA THROUGH THE OWNING DOMAINS (needs `GO SEED-STAGING`)
Create `scripts/seed-staging.*`. **Print the plan and the verified target, then wait for `GO SEED-STAGING`** (continue UI work meanwhile).
- Idempotent; refuses unless the verified ref is `kpwdvvcschankgggitnj`; aborts loudly on the production ref. No duplicate roles, tenants or users on rerun.
- Creates users via the Supabase Auth admin API (service-role key from env, used only inside this script) and creates profiles/memberships/role grants through the existing IAM model.
- **Seeds through the owning tables in dependency order**, respecting constraints and RLS, not shortcuts: tenant/institute → config → academic term/calendar (if implemented) → course/programme → subjects → batches/sections → students with batch membership → faculty → **a published timetable with entries assigning faculty to batch+subject+period** → attendance history.
- **College A "Demo Institute of Technology":** 2 departments, 3 batches, 4 subjects, 3 faculty, 40 students, published timetable covering the current week, ~2 weeks of past submitted attendance with realistic Indian names, mixed percentages, a few students below the shortage threshold, and one past session left unsubmitted (for the "missed session" widget).
- **College B "Test College of Science":** 1 department, 1 batch, 1 subject, 1 faculty, 10 students, a published timetable (for isolation tests).
- Accounts (document in `TRIAL_GUIDE.md`; shared temp password from env, never hardcoded): `platform.admin@demo.campxsync.test`, `admin.a@…`, `faculty.a1@…`, `admin.b@…`, `faculty.b1@…`, `student.a1@…` (stretch). Also one auth user with **no profile** and one with an **inactive membership** for negative tests.
- Print a summary table. **Gate:** run twice, no duplicates; complete the deferred role-routing check.

### PHASE 4 — PLATFORM → COLLEGE ONBOARDING (ADM)
- `/platform`: dashboard (institutes by status, total users, users per college, recent onboardings, recent platform audit events).
- Institutes list with status badges (onboarding / active / suspended / offboarded) and an **Onboard institute** wizard (name, code, subdomain if used, plan if used, admin name, admin email) with review step.
- Submitting follows the implemented onboarding path and shows saga progress: institute created → tenant config seeded → admin provisioned → active. Failure shows which step failed and offers retry. Provisioning is idempotent and audited.
- **Creating a new real tenant in staging requires `GO PROVISION-TENANT`.** Until then, build and test the screens against the seeded tenants and mark the live-onboarding test NOT RUN or BLOCKED.
- If an institute is `suspended`, its tenant users are denied with a clear message (test it if the status transition exists).
- Platform Admin never sees tenant student/faculty rows, only aggregates.
**Gate:** onboarding happy path demonstrated, idempotent retry verified, audit entries present.

### PHASE 5 — COLLEGE ADMIN: SETUP VIEWS, PEOPLE, ASSIGNMENTS
- `/college` dashboard shell (full dashboards in Phase 7).
- **Setup (read-only):** departments, batches/sections, subjects, published timetable (week grid + list).
- **Faculty:** list/search, add faculty (profile + role via the existing IAM path), assign to timetable entries where the owning contract allows. **Students:** list/search/filter by batch, add student, assign to batch.
- Forms use react-hook-form + zod, show loading and confirmed success only after the backend confirms, readable errors, duplicate detection (email, student code), confirmation for destructive actions (soft delete).
- Every create/update/delete is audited and tenant-scoped; no client-supplied tenant id.
**Gate:** create faculty and student through the UI, verify persisted rows, RLS, audit entries and cross-tenant invisibility.

### PHASE 6 — FACULTY ATTENDANCE (ACD-06) + CORRECTIONS
- `/faculty` home: today's timetable slots as cards with state (Not started / Open / Submitted), "Start attendance" action; upcoming slots; missed sessions.
- **Attendance sheet** (Section 5 pattern): validates timetable slot and roster, duplicate session opens the existing one, statuses from config, running counts, unsaved-changes guard, submit with confirmation showing counts. Backend enforces: valid slot, roster membership, unique key, idempotency key, lifecycle transition, permission.
- After submit the sheet is read-only for faculty. Faculty history: My sessions.
- **Corrections (College Admin or permitted role):** drawer with student, new status, mandatory reason, expected version; version conflict shows a friendly "this record changed, reload" message; append-only correction history visible per session; summary recalculated; `AttendanceCorrected` emitted if outbox exists.
- Shortage detection updates the summary flag using the configured threshold. If none is configured, use 75% and record it in DECISIONS.md as an assumption.
**Gate:** mark → submit → reload shows persisted data; duplicate rejected; correction without reason rejected; correction trail visible; summary matches records.

### PHASE 7 — ROLE-SPECIFIC DASHBOARDS, PREFERENCES AND REPORTS (ACD-10 read-optimized)
- **Platform Admin dashboard:** institutes by lifecycle status, onboarding progress/failures, aggregate users per college and recent platform audit events. No tenant student/faculty row access.
- **College Admin dashboard:** KPI cards (students, faculty, overall attendance %, students below threshold), 14-day attendance trend, attendance by batch/subject, shortage list, missed sessions, recent sessions with status badges.
- **Faculty dashboard:** today’s valid timetable slots, sessions needing attendance, submitted sessions, upcoming classes and missed-session cues; all links/actions obey faculty permissions.
- **Student dashboard (stretch only):** own attendance summary/history only; never cohort-wide private data.
- Dashboards must be genuinely role-specific in tasks, data, quick actions and navigation. Use only real authorised data and read-optimised queries; do not duplicate business rules in UI.
- **Attendance report:** filters (batch, subject, date range, status), paginated table, per-student %, CSV export only if trivial and tenant-safe (otherwise defer and record).
- **Audit log** page for College Admin (read-only, filterable) if time allows (cut-order item).
- All figures derive from real, authorised data via summaries/read-optimised queries. No hardcoded numbers.
- **Preferences:** provide a Preferences surface or contextual controls for the implemented settings. Persist supported settings by authenticated user + role, with module scope where relevant, using the approved persistence adapter. Include safe defaults, schema/version handling, reset-to-defaults, confirmation only after persistence success, and graceful fallback if storage is unavailable. Settings should include supported theme, sidebar state, dashboard widget order/visibility, table density/columns/page size, and default date range/filter; locale/timezone/accessibility settings should be included where the existing framework supports them. Mandatory warnings/actions and security restrictions cannot be hidden.
- Verify role preference isolation: changing a faculty dashboard must not change College Admin or another user’s settings. Verify tenant boundaries and tampered preference payloads.
**Gate:** dashboard numbers reconcile with a direct count from the source tables for College A (record the reconciliation); each role has a distinct dashboard; preference persistence/reset/fallback is evidenced or explicitly BLOCKED pending approved storage.

### PHASE 8 — PROFESSIONAL UI POLISH AND VISUAL QA
Apply Section 5 to every screen, not just dashboards. Use one coherent design system across all implemented modules and roles; no page may look like an unrelated template. Capture screenshots at 1440, 1024 and 390 widths for: login, each role home, institutes list + wizard, faculty list, attendance sheet, correction drawer, dashboard, 404/error/no-access pages. Run an accessibility scan (`@axe-core/playwright`) on login, dashboards and the attendance sheet; no serious or critical violations. Fix inconsistencies (spacing, type scale, colour, copy). Save screenshots to `docs/trial/screenshots/`.
**Gate:** Section 5 checklist table with PASS/FAIL per item.

### PHASE 9 — SECURITY AND REAL-BROWSER ACCEPTANCE (local build against staging)
Use Playwright (or an equivalent real-browser workflow). Record PASS/FAIL/BLOCKED/NOT RUN with screenshot evidence for critical journeys:
1. Unauthenticated access redirects to login.
2. Each seeded role logs in and lands on its home.
3. Each role sees only its authorized navigation.
4. Direct navigation to a forbidden route is denied.
5. **No-profile user and inactive-membership user are denied** with a clear message.
6. **Forged tenant id** (query/body/header) is ignored or rejected; no cross-tenant data returned.
7. College A ↔ College B cannot read or write each other's records (UI and direct RLS/API queries), including IDOR attempts on session, student and faculty ids.
8. College Admin creates faculty and student through the real UI.
9. Faculty marks attendance on a valid slot, submits, reloads, data persisted.
10. **Duplicate session rejected; idempotent retry creates no duplicates; invalid slot and non-roster student rejected.**
11. Submitted session cannot be edited by faculty; correction needs permission and reason; version conflict handled; history appended.
12. Dashboard totals match persisted records.
13. Platform Admin cannot read tenant student/faculty rows.
14. Onboarding saga happy path and idempotent retry (BLOCKED until `GO PROVISION-TENANT`).
15. Privilege escalation attempt (faculty calling admin endpoints) is denied.
16. Invalid input, network error and expired session give understandable outcomes. *(Best effort: first cut if time is short.)*
17. Logout clears the session.
18. Each implemented role lands on its own dashboard and cannot access another role’s routes/widgets/data.
19. User preferences survive reload/re-login when durable persistence is claimed; settings are isolated by user/role/tenant, reset safely, and malformed or old-version settings fall back without breaking the workflow.
20. No preference payload or dashboard customisation can bypass server authorization or reveal hidden tenant data.
Also: remove debug logs; check runtime/deploy configuration against the protected production target without copying the ref into output; scan app code for `service_role`; lint, typecheck, production build all pass; run the configured review tool.
**Gate:** honest results table including failures you could not fix (Gate G4/G5 evidence).

### PHASE 10 — DEPLOY TO VERCEL AND DEPLOYED ACCEPTANCE
1. Confirm framework and build command; pin Node in `engines`.
2. **Vercel "Production" is NOT the protected Supabase production.** Every Vercel environment (Production, Preview, Development) uses the STAGING Supabase URL and keys only. Use a dedicated staging Vercel project or an isolated environment.
3. `docs/trial/VERCEL_ENV.md`: variable names only. Only `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` may be public. No service-role key as a public variable or in runtime app code. Values go through Vercel's protected settings.
4. Give me the exact Supabase Auth URL configuration to apply: Site URL = the Vercel URL; Redirect URLs = Vercel production URL, preview pattern `https://*-<team>.vercel.app/**`, `http://localhost:3000/**`. Confirm allowed origins and cookie/session settings.
5. No route depends on local paths, Windows paths or localhost URLs. Custom 404 and error pages in the app design.
6. Run lint, typecheck, production build. Deploy (Vercel CLI if authenticated; if a Git-connected deploy needs a push, ask me once). Wait for completion. **A successful build is not proof the app works.**
7. If credentials or authorization are missing, prepare everything, report the exact manual action, never claim a live deployment without evidence.
8. Re-run the Phase 9 suite against the **deployed URL** and report results. Add the live URL to `TRIAL_GUIDE.md`.

**Gate passes only when:** the URL is accessible; build succeeds; critical journeys pass in a real browser on the deployed URL; forms and attendance use the real staging backend; authorization and tenant isolation have evidence; no unresolved blocker stops the trial flow; limitations and defects are documented. Fix failures within scope. Do not expand into other modules to improve visual completeness.

### PHASE 11 — TRIAL PACK AND TRACEABILITY (`docs/trial/`)
1. `TRIAL_GUIDE.md`: what CampXSync is in 3 lines, live URL, demo accounts per role, the flow step by step with expected results, what to ignore, how to report a defect.
2. `ARCHITECTURE_TRACEABILITY.md`: **every AllModules service (ACD-01..10, ADM, EXM-01..09, FIN-01..10, HRM-01..09, LMS-01..10, CAM)** with status `Specified / Implemented / Tested / Staging-verified / Production-released` and a one-line note. Slice modules show real evidence; all others show "Specified, not in MVP slice" so **no architecture is lost or silently skipped**. Source-of-truth register: canonical filename, version, superseded files.
3. `DECISIONS.md`: DEC items touched, how resolved, evidence.
4. `KNOWN_LIMITATIONS.md`, `POST_TRIAL_BACKLOG.md` (grouped by module with the suggested next module sequence from the lifecycle roadmap), `DEMO_SCRIPT.md` (5-minute walkthrough), `CHANGES.md`.
5. `DEFECT_LOG.csv`: columns `ID, Date, Reporter, Role Used, Page/URL, Steps To Reproduce, Expected, Actual, Severity (Blocker/Major/Minor/Cosmetic), Screenshot Link, Status, Owner, Notes`, plus 3 example rows marked as examples.
**Gate:** someone with no context can follow `TRIAL_GUIDE.md` and finish the flow.

## 5. PROFESSIONAL UI STANDARD

**Principle:** calm, trustworthy, fast, and obvious. An institution administrator should feel they are using enterprise software, not a prototype.

**Brand and tokens** (CSS variables, one source of truth)
- Navy/blue: `--navy-950 #081629`, `--navy-900 #0B1F3A`, `--navy-700 #12315C`, `--blue-600 #1F6FEB`, `--blue-500 #3B82F6`, `--blue-100 #E8F0FE`.
- Neutrals: `--ink #1B2433`, `--muted #6B7688`, `--line #E3E8EF`, `--surface #FFFFFF`, `--bg #F6F8FB`.
- Semantic: `--success #1E9E6A`, `--warn #E39B16`, `--danger #D64545`, `--info #1F6FEB`, each with a tinted background for badges and alerts. Text/background combinations meet WCAG AA contrast.
- Type: Inter via the framework's self-hosted font loader (system-ui fallback). Scale 12/14/16/20/24/32; body 14–16; tabular numerals for figures. Weights 400/500/600.
- Spacing on an 8px grid; radius 8 (inputs/buttons) and 12 (cards); two elevation levels only; 1px borders preferred to heavy shadows.
- Motion: 150–200ms ease for hover, drawer and toast; respect `prefers-reduced-motion`.

**Layout**
- Login: split layout, navy brand panel (logo or "CampXSync" wordmark, tagline, three short value points) and a clean form card. No stock-photo clutter.
- App: navy collapsible sidebar (icons + labels, active state, tooltips when collapsed), sticky top bar with tenant name, global search only if it works, user menu; breadcrumbs; page header (title, description, primary action); content max width ~1280px.
- Responsive: laptop and tablet fully; faculty attendance and login fully usable at 390px.

**Component kit (build once, reuse everywhere):** Button (primary/secondary/ghost/danger, loading state), Input/Select/Combobox/DatePicker/Textarea with label, hint and error, Form field wrapper, Card, StatCard (value, label, delta), DataTable (sticky header, sortable columns, pagination, filter chips, row actions, density, empty/loading/error states, column truncation with tooltip), Badge (lifecycle states with consistent colours: Draft grey, Open blue, Submitted green, Corrected amber, Locked navy, Cancelled red, Onboarding amber, Active green, Suspended red), Tabs, Drawer, Modal, ConfirmDialog, Toast, Skeleton, EmptyState (with icon and next action), ErrorState (with retry), Stepper (wizard), Timeline (audit/correction history), Avatar with initials, Tooltip, Breadcrumbs, PageHeader.

**Patterns**
- **Forms:** inline validation on blur, summary on submit failure, disabled-while-submitting, success toast only after backend confirmation, preserve input on error.
- **Tables:** search + filters above, row count, pagination, never an unbounded list.
- **Attendance sheet:** large rows (min 48px touch target); status segmented control per student coloured green/red/amber; keyboard support (arrow keys to move, `P`/`A`/`L` to mark and advance, `Space` to toggle); "Mark all present"; search student; filter "unmarked"; sticky bar with running counts "32 present · 5 absent · 3 leave · 0 unmarked" and Submit; unsaved-changes guard; confirmation dialog on submit; read-only after submit with a "Request/Make correction" action for permitted roles.
- **Correction drawer + history timeline:** old value → new value, reason, actor, time.
- **Dashboards:** KPI row, then charts (Recharts: attendance trend line, attendance-by-batch bar, accessible colours and labelled axes), then actionable lists (shortage, missed sessions). Every widget has loading, empty and error states.
- **Copy:** plain, specific, kind. Errors say what happened and what to do. Dates `15 Oct 2026`, 24h or 12h consistently, IST. Currency/number formats Indian where relevant. No raw IDs or stack traces shown to users.
- **Role cues:** badge colours Platform = navy, College = blue, Faculty = teal, Student = slate.

**Navigation and workspace per role (`navConfig`)**
- `platform_admin`: Dashboard, Institutes, Platform Audit; tenant data shown only as authorised aggregates.
- `college_admin`: Dashboard, Timetable, Batches & Subjects, Faculty, Students, Attendance, Audit Log.
- `faculty`: Today, Take Attendance, My Sessions, My Timetable.
- `student` (stretch): My Attendance only.
- Future module/role dashboards must be specified in the product-wide traceability/design catalogue but must not be implemented in this MVP slice.

### 5.1 Dashboard and preference contract
- Each role dashboard specifies its audience, authorised widgets/KPI definitions, source/read model, filters, quick actions, drill-down permissions, refresh/freshness, responsive layout and empty/loading/error/partial states. No fake metrics, dead controls, or data leakage through aggregates, caches, charts, exports or errors.
- Use a typed and versioned preference schema. Scope settings to authenticated user + role and, where needed, module + role. Trusted server context defines identity/tenant/role. Preference values are allowlisted, size-limited, validated and never determine authorisation.
- Precedence: mandatory security/accessibility constraints → tenant-enforced defaults → user-role settings → user-module settings (role-specific where needed) → product defaults. Make conflicts visible where users can reasonably change the setting.
- Preserve supported settings across reload and re-login. Claim cross-device/account sync only after verifying backend persistence on a second browser/device. Browser local storage may be used only for explicitly device-local, non-sensitive convenience settings and must be labelled accordingly.
- Preferences can include dashboard widget order/visibility, table density/columns/sort/page size, supported default date range/filter, theme, sidebar state, locale/timezone and accessibility display options. Never allow preferences to hide mandatory alerts, required fields or critical safety actions. Provide reset-to-defaults and safe fallback for missing/malformed/old-version settings.
- Do not add or alter schema without proposing the migration under `supabase/migrations_proposed/` and waiting for `GO MIGRATION-<NAME>`. If durable preference persistence needs schema work, complete independent UI/interface work and report it BLOCKED; do not silently use local storage as a substitute for a promised server-side feature.
- Test preference persistence, reset, version fallback, per-role/per-user/per-tenant isolation, tampered payloads, inaccessible widgets and responsive reflow. Confirm no preference grants access or overrides RLS/server guards.

**Quality checklist (PASS/FAIL each in Phase 8)**
1. Consistent tokens; no hardcoded colours outside the token file.
2. Every list/table/chart has loading, empty and error states.
3. Every form has validation, loading and readable errors.
4. Keyboard navigable with visible focus; labels on all inputs; AA contrast.
5. No layout break at 1440, 1024 and 390 widths; no horizontal page scroll.
6. Touch targets at least 44px on mobile; attendance rows at least 48px.
7. No dead buttons or links; every control does what it says.
8. Consistent badge colours and wording for lifecycle states across pages.
9. No flash of unauthorized content; no blank screens on error.
10. Custom 404, no-access and error pages in the app design.
11. Axe: no serious or critical violations on login, dashboard, attendance sheet.
12. First meaningful paint feels fast on the deployed URL: skeletons within 200ms for slow queries; no long spinners without text.
13. Each implemented role has a genuinely distinct dashboard and authorised navigation; widgets and KPI values derive from real data.
14. Preferences persist according to the documented scope and storage promise; role/user/tenant isolation, reset, invalid-version fallback and unavailable-storage behaviour pass.
15. The shared design system is coherent across all implemented pages and responsive layouts; screenshots and accessibility evidence are recorded.

## 6. CUT ORDER (if time runs short, cut in this order)
1. Student view  2. College Admin Audit Log page  3. Phase 9 test 16  4. CSV export  5. Global search, tooltips-on-collapsed-sidebar and other polish extras  6. Charts beyond the trend line and bar chart  7. Setup creation (keep read-only)  8. Correction drawer visual polish (keep function, reason, history, audit).

**Never cut:** login and role guards; no-profile denial; tenant isolation and RLS; timetable-validated attendance with duplicate protection and idempotency; submit lifecycle; audited corrections; the onboarding saga happy path; College Admin dashboard; audit writes for privileged actions; the Vercel deployment with evidence; the traceability matrix.

## 7. WORKING STYLE
- Smallest change that satisfies the step; reuse existing code and tables. Do not refactor unrelated code or touch other modules.
- Never invent table, column, relationship or RPC names. Read them from staging and the catalog first.
- If ambiguous, choose the most conservative option, note the assumption in the PHASE REPORT and DECISIONS.md, continue.
- Prefer the strongest available model for Phases 1, 2 (auth), 9 (security) and 10 (deploy); the faster model is fine for screens in Phases 4–8.

## 8. DEFINITION OF DONE (apply to every phase deliverable)
Acceptance criteria demonstrated; lint/typecheck/build clean; tenant-isolation and authorization tests pass for the affected path; no duplicate seed/role creation; audit events sufficient; UI states verified; contracts documented; evidence recorded in the report; the next action named.

## 9. PHASE REPORT FORMAT
```
PHASE <n> REPORT
Scope:        <exact scope attempted>
Changed:      <files, routes, policies, migrations proposed, events>
Commands/tests run: <against which build and environment, with exact results>
Tests:        <list with PASS / FAIL / BLOCKED / NOT RUN, and why for skipped>
DB writes:    <none | approved step phrase, verified target, evidence, postconditions>
Review:       <findings from review tool, fixed / accepted / blocking>
Status:       <what is demonstrably complete vs merely implemented vs specified>
Risks:        <security, data, compatibility, operational>
Assumptions / Decisions: <list; recorded in DECISIONS.md>
Blockers:     <none | list with the exact approval or manual action needed>
Next:         <single next action>
```

## 10. FINAL REPORT (after Phase 11)
Live Vercel URL, deployed commit/revision if available, build result, browser-test evidence table, screenshots path, demo accounts, traceability summary (counts by status), known defects, known limitations, manual setup still required, and how to hand this to trial users.

BEGIN WITH PHASE 1.


## 11. ROLE DASHBOARD AND PREFERENCE NON-NEGOTIABLES

- The product must support distinct dashboards for every role actually enabled in CampXSync. The MVP scope is only the roles listed in Section 2; the broader role catalogue is design/traceability work, not permission to expand the slice.
- Every dashboard and every module screen must use the shared design system, accessible responsive patterns, and real authorised data. Professional appearance is a release requirement, not a substitute for functionality/security.
- Preserve user preferences at the promised scope. Explicitly label each setting as account-synced or device-local; never imply persistence before confirmation. Never put authorization decisions, secrets or sensitive records in preferences.
- If a persistence schema/API change is needed, prepare the proposal and continue independent work while awaiting the exact approval phrase. No unauthorized database writes.
- Report dashboard and preference acceptance tests separately from visual polish and functional acceptance. Mark unavailable evidence BLOCKED or NOT RUN, never PASS by assumption.
