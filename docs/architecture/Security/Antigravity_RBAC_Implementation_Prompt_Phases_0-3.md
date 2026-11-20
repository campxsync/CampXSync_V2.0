MODE: IMPLEMENTATION IN DEV ONLY (Phases 0–3). Owner approval recorded: Niranjan V.G., 2026-11-20, for the work described below and nothing beyond it.

AUTHORITY AND INPUTS
- Governing document: docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md (FINAL baseline). Read it fully first, especially §3 (roles R01–R39), §8 (IADM services × admin roles), §9 (CADM services × college roles), §21 (owner-reviewed additions), §22 (final decisions and implementation defaults).
- Evidence: docs/audits/rbac-coverage/ (00_INDEX_AND_SUMMARY.md, Parts A–M, Part-B_permission-map.csv). Treat the CSV as the starting point, not as gospel: it has known inconsistencies listed in §22.2 of the matrix.
- Also follow AGENTS.md, PROGRESS.md and NEXT_TASK.md conventions (branching, commit style, progress logging). If they conflict with this prompt on Git handling, follow them.

PLAIN-LANGUAGE RULE
Write a plain-language meaning next to every role code, permission key family, service ID, flag and abbreviation in every report (for example "R03 Institution Admin", "RLS = row-level security in Supabase", "IADM-07 Security & Compliance Governance").

WHAT YOU MAY DO
- Create NEW numbered migration files (follow the existing numbering convention in database/migrations/), each with a matching rollback script. NEVER edit existing migrations, including database/migrations/Scripts_V3.0/test_rbac.sql.
- Create new test/verification SQL files and report files under docs/audits/rbac-implementation/.
- Edit only the code files named in Phase 3 (or directly referenced by a TENANT_ADMIN removal) and their tests.
- Apply migrations to the DEV Supabase project only, through the project's existing migration process, one phase at a time.
- Commit work on a new branch per AGENTS.md. Never push or merge to main.

WHAT YOU MUST NOT DO
- No production database or production configuration. No new infrastructure, tools, containers or services. No schema redesign beyond what is listed. No changes to the frontend database connection (that is a separate findings lane). No printing of secrets, keys, tokens, password hashes or personal data; use counts and masked IDs.
- Do not fix unrelated defects. Record them in docs/audits/rbac-implementation/FINDINGS_REGISTER_OPEN.md and move on.

OPERATING RULES (so we do not stall)
1. Keep going through all phases without asking for small approvals.
2. When uncertain whether a key can be removed: KEEP it and add it to the exceptions list (docs/audits/rbac-implementation/EXCEPTIONS.md with key, plain-language meaning, who still needs it, evidence). Uncertainty is never a reason to stop.
3. Add before remove: a new owner role receives a key before R03 or R04 loses it.
4. Before every migration: write a before-snapshot of the affected role, role-template, role-permission and grant-rule tables to docs/audits/rbac-implementation/snapshots/ (counts and codes only). After every migration: run its verification SQL and write an after-snapshot.
5. If a migration fails, run its rollback script, record why, fix, retry. Stop ONLY for a real blocker: a rollback that cannot restore the before-snapshot, any step that would touch production, a need for a new tool or infrastructure, or a design change larger than this prompt. Say which, and what you already finished.
6. Tag every statement in reports CONFIRMED (file:line or SQL run), INFERRED (say from what) or NOT FOUND (say where you looked). No guessing.
7. Your chat reply after each phase must be SHORT (10 lines). All detail goes in the report files.

==============================
PHASE 0 — VERIFY (read-only; report file: PHASE0_REPORT.md)
==============================
0.1 Key families. For each permission family adm01 … adm09 (and any key used by institute-admin-service or college-admin-service), find every Java endpoint, RLS policy and frontend screen that uses it. Decide for each family: IADM only, CADM only, or shared by both (separated by scope: institute-wide versus college). Write the table. Default if you cannot prove otherwise: shared (matrix §22.1 rule 5).
0.2 Java versus RLS. For batch-management-service and attendance-service (they check COLLEGE_ADMIN by role name), determine from code and policies: which database role do they connect as, do the tables they write have RLS policies keyed to permission keys, and therefore what would happen if a permission key were removed from R04 (request blocked at the controller, blocked by RLS, or both)? Static analysis only; no live mutation.
0.3 Gateway role header. In services/api-gateway (JdbcIdentityResolver.java and nearby), state exactly which string is placed in X-User-Role for each role (a role code such as COLLEGE_ADMIN, a catalogue ID such as R04, or something else) and which roles are in the "administrative tier".
0.4 Recount. Using the matrix and the CSV, recount how many keys R04 and R03 would lose under matrix v3.0 §22.1 and §22.3 rules, and list the keys you will KEEP because of the "retain when uncertain" rule.
0.5 Keys used by IADM-04 Subscription & Billing and IADM-05 Data Governance & Privacy today (if any code exists), and which permission keys it would need. Do not create keys; just list.
Output: PHASE0_REPORT.md with the four tables and a one-paragraph "what this means for Phases 1–3". Then continue automatically.

==============================
PHASE 1 — DATABASE FOUNDATION (DEV; report: PHASE1_REPORT.md)
==============================
New migration(s) with rollback, in this order:
1.1 Fix swapped catalogue IDs in iam.role_templates: APPLICANT = R32, ADMISSIONS_OFFICER = R33.
1.2 Create role templates for R34 Department Staff, R35 Academic Records Officer, R36 Document Verifier, R37 External Verification Agency, R38 Provisioning / IAM Operator, R39 Faculty Advisor, with NO permissions yet, using the existing mechanism that copies templates into every tenant (49 tenants in DEV). Set catalogue_id, kind and scope_note consistently with the existing 33 templates.
1.3 Grant rules (iam.role_grant_rules):
   a) Delete COLLEGE_ADMIN → COLLEGE_ADMIN, PRINCIPAL, VICE_PRINCIPAL, DEAN.
   b) Add INSTITUTION_ADMIN → REGISTRAR, ADMISSIONS_OFFICER and R34–R39 (not APPLICANT: applicants self-register).
   c) Leave all STUDENT and PARENT grant rows for now (Phase 2 decides).
1.4 AUDITOR: remove audit.admin, audit.write, wf.write, core.comment and the five HR "write own" keys (hrm02, hrm03, hrm04, hrm07, hrm09). Scan AUDITOR for any other write, manage, admin, approve or review key and remove those too; list what you removed. Keep read keys.
1.5 Tenant Admin collapse (matrix IADM-D7):
   a) Verify INSTITUTION_ADMIN already holds every key Tenant Admin's wildcard was needed for within IADM scope; list any gap, and only add keys that the matrix §8 gives R03.
   b) Give the single DEV Tenant Admin holder an INSTITUTION_ADMIN assignment (keep their COLLEGE_ADMIN). Confirm after this that they can still do everything they need for IADM and CADM work (list endpoints tested).
   c) Then remove: the wildcard permission rows (iam.role_template_permissions and iam.role_permissions, 49 tenant rows), the TENANT_ADMIN assignment, TENANT_ADMIN role rows, its role template, and its grant rules. Keep the function support for wildcard matching in iam.has_permission (no key will hold it).
1.6 iam.api_clients: add the 90-day expiry rule (expires_at required and at most 90 days after creation) ONLY IF PostgresApiClientRepository always sets expires_at; otherwise skip and list it as a Phase 3 item.
1.7 Verification SQL (new file, run it, record pass/fail): wildcard `*` held by zero roles; TENANT_ADMIN absent; all 49 tenants have identical role sets; R04 cannot grant R04, R05, R06, R07 (use an existing non-privileged test method; if none exists, assert on the grant-rule rows); R03 can grant R33–R39 except APPLICANT; AUDITOR has no write/manage/admin keys; swapped IDs fixed; every user still has at least one role.
Exit: PHASE1_REPORT.md. Continue automatically to Phase 2 if every verification passes; otherwise fix or roll back, then continue.

==============================
PHASE 2 — NARROW R04 AND R03 (DEV; report: PHASE2_REPORT.md)
==============================
2.1 Coverage for new owners (add before remove). For every key that R04 or R03 will lose, make sure the matrix's new holder already holds it. Add missing keys to: R05 Principal and R07 Dean (Academic/LMS domain admin; D-RBAC-M09), R31 Registrar, R12 Controller of Examinations / R13 Exam Cell, R15 Finance Manager / R16 Accountant, R17 HR Manager / R18 HR Officer, R19 Communication Manager. Use the "matrix_new_holder" column of the CSV as the guide; log every key you add.
2.2 R04 College Admin: remove keys flagged REMOVE or "REMOVE M/W" in the CSV, convert "REMOVE M -> KEEP V" to view-only, and always remove the owner-decision D-RBAC-M12 keys (fin04, fin06, hrm04, hrm05 families that R04 holds). Apply the shared-key defaults in matrix §22.3. Skip (and list in EXCEPTIONS.md) any key that Phase 0 shows is still used by an IADM or CADM service, or about which you are uncertain.
2.3 R03 Institution Admin: remove CADM-only, domain-service keys according to the Phase 0 key-family result. R03 keeps every key an IADM service uses, `adm01`, `adm02`, dormant `adm03` and the shared keys in §22.3. Remove doc.admin / doc.manage / doc.write only after R31 Registrar holds them.
2.4 STM-07 check. Verify that StudentRoleAssignmentService (institute-admin-service) assigns STUDENT without depending on the STUDENT and PARENT rows in iam.role_grant_rules. If it does not depend on them, delete COLLEGE_ADMIN → STUDENT/PARENT and INSTITUTION_ADMIN → STUDENT/PARENT rows (matrix §21.1: R25/R26 only through STM-07). If it does depend on them, leave them and log it.
2.5 DEV personas. Using the existing seed approach, add test assignments (no credentials in files) for PRINCIPAL (R05) and DEAN (R07) so the academic desks have owners in DEV. Do not add more than these two.
2.6 Known-breakage list. After the migration, test the R04 and R03 DEV personas against the IADM and CADM screens/endpoints. List what still works and what is now denied. For endpoints in other domain services (academic, attendance, batch, finance, HR, communication) that are now denied or error out, do NOT fix them: put them in FINDINGS_REGISTER_OPEN.md as "temporary mismatch accepted".
2.7 Verification SQL: before/after key counts per role (R03, R04, R05, R07, R28, R31); R04 holds none of the D-RBAC-M12 keys; the exceptions list is complete; no user has zero effective roles.
Exit: PHASE2_REPORT.md with before/after counts, exceptions, known breakages.

==============================
PHASE 3 — ALIGN CODE FOR IADM AND CADM ONLY (report: PHASE3_REPORT.md)
==============================
3.1 institute-admin-service and college-admin-service: for every controller endpoint, replace role-name checks with permission-key checks following matrix §8 (IADM) and §9 (CADM). Keep behaviour for permitted users identical. Add or update unit/integration tests per endpoint group. Do not touch other services.
3.2 api-gateway (JdbcIdentityResolver.java and related): remove TENANT_ADMIN from the administrative tier and any related mapping; keep everything else.
3.3 frontend/src/lib/auth/resolve-role.ts and related tests: remove TENANT_ADMIN mapping only. Do not add routing for the other roles.
3.4 First R03 appointment: create a minimal database function (SECURITY DEFINER, callable only by members of plat.platform_admins) that lets the platform Super Admin (R01) assign INSTITUTION_ADMIN in a tenant, and writes an audit entry. No two-person rule yet (deferred; list it as an accepted risk).
3.5 If Phase 1.6 was skipped, make the API client repository always set expires_at (max 90 days), then add the CHECK rule.
3.6 Run all existing test suites for the services and files you touched, plus the Phase 1 verification SQL. Record results.
Exit: PHASE3_REPORT.md.

==============================
FINAL DELIVERABLES (docs/audits/rbac-implementation/)
==============================
00_INDEX.md (index, one-paragraph outcome per phase, test results), PHASE0_REPORT.md … PHASE3_REPORT.md, EXCEPTIONS.md, FINDINGS_REGISTER_OPEN.md (frontend superuser access, Java role-name checks in other services, test_rbac.sql naming, two-person rule, IADM-04/05 keys, unrouted roles, sys tables without RLS, accepted temporary mismatches, anything else you noticed), snapshots/ folder, migration and rollback file list, and a confirmation of exactly which files were created or changed.
After all phases: update PROGRESS.md and NEXT_TASK.md per AGENTS.md.
