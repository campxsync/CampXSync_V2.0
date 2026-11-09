# CampXSync --- Login & Authorization Hardening Scope

**Document:** `docs/Login_Hardning_Scope.md`\
**Purpose:** Focused execution scope for resolving the currently
identified CampXSync login, role-assignment, identity-mapping, and
tenant-isolation issues.\
**Status:** Scope approved; implementation and remaining account
provisioning require execution evidence and the approvals defined below.

------------------------------------------------------------------------

## 1. Purpose and Governing Context

This document establishes a narrow, controlled path to complete the
outstanding CampXSync login and authorization work.

The objective is to ensure that every required user can:

1.  Authenticate with a valid Supabase Auth account.
2.  Receive the correct application role from authoritative, trusted
    records.
3.  Be associated with the correct user profile and college tenant.
4.  Access only the pages and data permitted for that role and tenant.
5.  Reach the correct role-specific dashboard after login.

The required user types are:

-   Platform Admin
-   College Admin
-   Faculty
-   Student

The work must address the identified code vulnerabilities and incomplete
identity mappings without expanding into unrelated modules, restarting
completed phases, or repeating previously completed audits.

### Governing documents

Before execution, read and follow the existing:

-   `CampXSync_MVP_Master_Prompt_v4.md`, or the actual current path and
    filename of the v4 master execution prompt.
-   `product_lifecycle.md`, or its actual current repository path and
    filename.
-   Existing project checkpoints, staging instructions, execution logs,
    and handover documents referenced by the master prompt.

These documents remain authoritative for project-wide instructions,
workflow, production safety, staging approvals, and preservation of
work.

This scope document supplements those instructions. It does not override
them.

If a conflict exists, stop and identify the conflict before proceeding.

------------------------------------------------------------------------

## 2. Non-Negotiable Safety Rules

1.  **Production isolation:** Never connect to or modify the production
    Supabase project while carrying out staging work.
2.  **Staging verification:** Before any database write, independently
    verify the exact non-production project and database target. A
    dashboard badge or project label alone is not sufficient evidence of
    the target's identity.
3.  **Explicit database approval:** Do not execute database writes,
    migrations, role-mapping changes, account-provisioning operations,
    or seed scripts without the required explicit approval for that
    specific step under the governing master prompt.
4.  **No assumed approval:** Approval for code changes does not
    authorize database changes or account creation.
5.  **Preserve existing work:** Inspect the current Git state before
    editing. Preserve `ReviewBranch`, all existing changes, checkpoints,
    tests, and documentation. Do not overwrite or discard work.
6.  **No commit, push, or deployment:** Do not commit, push, or deploy
    unless separately and explicitly authorized.
7.  **No secrets in output:** Never print passwords, access tokens,
    private keys, or full environment files.
8.  **No unnecessary repetition:** Reuse existing evidence. Repeat a
    check only when necessary to verify a change, resolve a
    contradiction, or satisfy a specific acceptance criterion.
9.  **No unrelated work:** Do not expand this task into other CampXSync
    modules or general architectural refactoring.
10. **Stop on ambiguity:** If the target environment, account identity,
    intended role, tenant association, or required approval is unclear,
    stop before the affected operation.

------------------------------------------------------------------------

## 3. Current Situation and Findings

The following findings come from the previously discussed implementation
and identity reports. They are working evidence, not a claim that every
issue has been independently reverified against the current repository.

### 3.1 College Admin authentication

The user successfully logged in to CampXSync using the password reset
the previous day.

The local `frontend/.env.local` file was subsequently updated so that
`CAMPX_STAGING_TEST_PASSWORD` contains the current test password.

Important distinctions:

-   A successful manual login establishes that the tested account can
    authenticate in that particular environment.
-   Updating a local environment variable changes local test
    configuration; it does not change the Supabase account password or
    automatically change Vercel environment variables.
-   Do not reset this account's password again without a specific
    reason.
-   Do not print the password or ask the user to share it.

**Current disposition:** The immediate College Admin password issue is
resolved from the user's perspective. Other role and authorization
issues remain.

### 3.2 Role-escalation fallbacks

Previous code findings identified unsafe role-resolution fallbacks in:

-   `frontend/src/lib/auth/resolve-role.ts`
-   `frontend/src/middleware.ts`

The reported concerns include:

-   Inferring `PLATFORM_ADMIN` from an email prefix such as
    `platform.admin@`, without authoritative Platform Admin membership.
-   Falling back to `app_metadata.role` or `user_metadata.role` when
    authoritative role assignments are missing. User-editable metadata
    must not grant privileged application roles.

**Required outcome:** Application roles must be derived from the
project's authoritative authorization model. Missing, ambiguous, or
invalid role assignments must not silently grant privileges.

Inspect the current implementation before changing it. Preserve valid
existing role-resolution behavior that does not conflict with this
requirement.

### 3.3 Default-tenant fallback

Previous findings identified dashboard pages that use a fallback tenant
UUID:

`11111111-1111-1111-1111-111111111111`

The reported pattern is equivalent to:

`resolution.session.tenantId || '<default-tenant-UUID>'`

The fallback was reported across multiple College Admin and Faculty
dashboard pages.

**Required outcome:** A missing or unresolved tenant must not cause the
application to use a default tenant. Protected tenant-scoped operations
must fail closed when the required tenant context is missing.

Do not assume the same fix can be applied blindly to every page. Inspect
the relevant code and tests, then make the smallest safe changes needed
to eliminate the insecure behavior.

### 3.4 Student-related code and dashboard status

A previous report referenced a possible Student page/query at:

`frontend/src/app/(dashboard)/student/page.tsx`

However, we have not established that a complete, usable Student
Dashboard exists. A source file or route alone is not proof that the
feature is implemented.

**Required outcome for this scope:**

-   Inspect the current repository only as needed to establish whether
    this page or route exists and what it actually does.
-   Do not describe a Student Dashboard as implemented unless existing
    evidence supports that conclusion.
-   Do not build, expand, or implement a Student Dashboard as part of
    this task.
-   If the reported unsafe student-record lookup exists in an existing
    code path, document it as a finding and make only a narrowly scoped
    security correction if it can be done without building or expanding
    a Student Dashboard.
-   Defer Student Dashboard runtime acceptance criteria until that
    dashboard is separately implemented under an explicitly approved
    scope.

Never silently select another student's record in any existing code
path.

### 3.5 Auth accounts and role mappings

Previous identity reports stated:

-   The Auth account `campxsync@gmail.com` has a College Admin
    association.
-   No authoritative Platform Admin mapping was found for that account.
-   A Platform Admin Auth account and authoritative role mapping were
    not found in the inspected evidence.
-   Faculty and Student profiles or role assignments were reported as
    existing in the database without corresponding Auth users.
-   An older synthetic profile was reported without a matching Auth
    user.

These are reported findings. Before creating or linking accounts,
inspect the current staging records and verify the exact identities. Do
not infer that a profile should become an Auth account solely because it
exists in the database.

**Required outcome:** Each required role has a valid authentication
identity and the correct authoritative role, profile, and tenant
associations, without creating duplicate or incorrectly linked
identities.

### 3.6 Database access and tenant isolation

The previous audit reported that the frontend server database pool uses
a privileged database role and that ordinary Row-Level Security
enforcement may therefore not protect queries made through that
connection.

This issue must be treated carefully. Do not assume that enabling RLS
alone protects every server-side query.

**Required outcome for this scope:** The login and dashboard paths being
changed must enforce trusted role resolution, tenant-scoped data access,
and safe denial when identity or tenant context is missing. Do not
undertake a broad database-access redesign as part of this task. If a
critical isolation defect in the affected paths cannot be safely
resolved within scope, stop and document the precise blocker.

------------------------------------------------------------------------

## 4. Scope Boundaries

### In scope

-   Secure role resolution for authenticated users.
-   Removal of email-prefix and user-editable metadata privilege
    fallbacks.
-   Removal of unsafe default-tenant fallbacks.
-   Regression tests for the identified defects in existing code paths.
-   Determine and document the current state of Student-related
    routes/pages without treating them as a completed Student Dashboard.
-   Staging-only verification of existing Auth users, role assignments,
    profiles, and tenant associations.
-   Creation of missing required Auth identities and role mappings,
    after identity verification and explicit approval.
-   Verification of role-specific login and dashboard access.
-   Verification that missing or incorrect identity/tenant mappings fail
    safely.

### Explicitly out of scope

Unless separately authorized, do not:

-   Redesign CampXSync authentication architecture.
-   Rewrite all authorization or database infrastructure.
-   Migrate unrelated academic, LMS, exam, finance, HRM, CAM, or admin
    modules.
-   Modify production data or production configuration.
-   Create accounts before the staging identity preflight and required
    approval.
-   Run broad seed scripts or reset existing accounts.
-   Change the working College Admin password.
-   Commit, push, deploy, or change Vercel project settings.
-   Repeat completed tests merely to generate another report.
-   Treat a build passing as proof that role security or tenant
    isolation works.

------------------------------------------------------------------------

## 5. Execution Plan

Follow the stages in order. Complete each stage with concrete evidence
before moving to the next.

### Stage 0 --- Establish the execution context

1.  Read the governing v4 master prompt and product lifecycle document.
2.  Inspect the current Git branch and working-tree state.
3.  Locate the latest login and authorization findings and relevant
    checkpoints.
4.  Identify which findings already have adequate evidence and which
    require targeted verification.
5.  Do not repeat broad audits or modify files during this
    context-establishment step unless the scope document itself needs to
    be added or corrected.

**Deliverable:** A concise confirmation of the current branch, existing
work to preserve, applicable safety instructions, and the exact next
unfinished step.

### Stage 1 --- Fix the identified code defects

Implement the following focused changes:

1.  Remove the email-prefix fallback for `PLATFORM_ADMIN`.
2.  Remove any fallback that allows user-editable metadata to establish
    a privileged application role.
3.  Ensure missing or invalid authoritative role assignments fail
    closed.
4.  Remove the hardcoded default-tenant fallback from the affected
    dashboard paths.
5.  Ensure protected tenant-scoped requests fail safely when tenant
    context cannot be resolved.
6.  Inspect the reported Student-related code path and confirm what
    exists. Do not build or expand a Student Dashboard. If the reported
    unsafe lookup exists in an existing path, correct it narrowly
    without adding dashboard functionality.
7.  Preserve the working College Admin authentication flow and valid
    existing authorization behavior.

Add or update focused regression tests covering:

-   An email prefix alone cannot grant Platform Admin access.
-   User-editable metadata cannot grant a privileged role.
-   Missing authoritative role assignments do not grant access.
-   Missing tenant context does not fall back to another tenant.
-   Any existing Student-related code path in scope does not silently
    return another student's record when identity mapping is missing or
    incorrect.
-   Valid role and tenant associations continue to work.
-   No Student Dashboard is created or expanded as part of this task.

Run relevant existing tests and typechecking. Run the build if practical
and report any failures accurately.

Do not modify the database or create Auth accounts during this stage.

**Gate:** Show the actual code changes and test results. Do not claim
the security fixes are complete if the relevant tests have not passed or
the implementation remains incomplete.

### Stage 2 --- Verify the staging identity and account state

After Stage 1, perform a read-only staging preflight under the governing
safety rules.

Before querying, establish that the target is the intended
non-production environment.

Inspect only the information required to determine:

-   Which Auth users currently exist.
-   Which authoritative role assignments exist.
-   Which user profiles exist and how they map to Auth identities.
-   Which tenant each relevant identity belongs to.
-   Whether required Platform Admin, College Admin, Faculty, and Student
    identities already exist.
-   Whether any proposed operation would create a duplicate or alter an
    existing valid association.

Do not expose passwords, tokens, or unnecessary personal information.

Produce a compact mapping table with the relevant account identifier,
intended role, profile association, tenant association, and status.
Clearly distinguish verified facts from unresolved items.

**Gate:** If the environment or identity mapping is ambiguous, stop. Do
not create or modify anything.

### Stage 3 --- Present proposed account and role-mapping changes for approval

Based on Stage 2, identify only the missing or incorrect records that
need action.

Before any write, present:

1.  The verified non-production project identity.
2.  The exact account or profile to be created or linked.
3.  The intended role and tenant association.
4.  The exact proposed SQL or approved provisioning operation.
5.  The expected effect and rollback approach.
6.  Any Auth Dashboard action that must be performed manually by the
    user.
7.  Confirmation that production will not be accessed or modified.

Follow the exact per-step approval phrase and process required by the
governing v4 master prompt. Do not invent a substitute approval
mechanism.

**No write may execute until the user explicitly approves that specific
step.**

Do not create accounts merely to satisfy a checklist. Reuse valid
existing identities and mappings wherever appropriate.

### Stage 4 --- Establish the required user identities

After the relevant approval, establish the required accounts and
authoritative associations in staging.

The target roles are:

-   **Platform Admin:** Valid Auth identity, authoritative Platform
    Admin membership, and only the intended platform-level access.
-   **College Admin:** Preserve the existing working account and confirm
    its authoritative role and tenant association.
-   **Faculty:** Valid Auth identity linked to the correct faculty
    profile, role, and college tenant.
-   **Student:** Valid Auth identity linked to the correct student
    profile, role, and college tenant.

For each identity:

-   Avoid duplicate Auth accounts.
-   Do not assign a role from email naming conventions.
-   Do not trust user-editable metadata as authorization.
-   Do not assign an arbitrary default tenant.
-   Preserve existing valid data and relationships.
-   Record what was changed and what remains unresolved.

Where account creation requires an Auth Dashboard action, provide exact
instructions for the user. Never request that passwords be pasted into
chat.

### Stage 5 --- Verify the actual login outcomes

Once the code changes and approved identity mappings are ready, verify
the required login flows in the intended staging environment.

For each role and dashboard flow that actually exists, confirm:

1.  Authentication succeeds with the intended account.
2.  The application resolves the correct authoritative role.
3.  The correct existing dashboard opens.
4.  The account is associated with the intended tenant.
5.  Missing or invalid role assignments fail safely.
6.  Missing tenant context does not reveal another tenant's data.
7.  Any existing Student-related code path under test cannot silently
    return another student's record.

Do not require Student Dashboard login or dashboard acceptance testing
in this scope because a completed Student Dashboard has not been
established. Do not build one as part of this task. Student Dashboard
runtime verification must wait until the feature is separately
implemented and explicitly scoped.

Reuse valid existing test evidence where it is still applicable. Run
only the targeted tests necessary to establish the applicable acceptance
criteria.

A successful build or successful password check alone does not establish
these outcomes.

------------------------------------------------------------------------

## 6. Acceptance Criteria

This scope is complete only when the following conditions have been
satisfied or any remaining blocker has been explicitly accepted by the
user.

### Code security

-   [ ] Email-prefix role escalation is removed.
-   [ ] User-editable metadata cannot grant privileged application
    roles.
-   [ ] Missing or invalid authoritative role assignments fail closed.
-   [ ] Missing tenant context never triggers a default-tenant fallback.
-   [ ] Any existing Student-related code path in scope cannot silently
    select another student's record.
-   [ ] The current state of Student-related routes/pages is accurately
    documented; no Student Dashboard is built or assumed to exist.
-   [ ] Focused regression tests cover the identified defects.
-   [ ] Relevant tests and typechecking have passed; build results are
    recorded.
-   [ ] The existing College Admin login flow remains functional.

### Account and role configuration

-   [ ] Staging identity state has been verified.
-   [ ] Duplicate Auth users have been avoided.
-   [ ] The required Platform Admin identity and authoritative mapping
    exist.
-   [ ] The existing College Admin account remains correctly mapped.
-   [ ] Required Faculty identities and mappings exist.
-   [ ] Required Student identities and mappings exist.
-   [ ] Each account has the correct profile and tenant association.
-   [ ] No production database or configuration has been modified.

### Runtime verification

-   [ ] Each available role can authenticate.
-   [ ] Each role reaches the correct dashboard where that
    dashboard/flow actually exists.
-   [ ] Tenant isolation is verified for the affected login/dashboard
    paths.
-   [ ] Invalid or missing identity associations fail safely.
-   [ ] Student Dashboard runtime criteria are deferred until a Student
    Dashboard is separately implemented.
-   [ ] Remaining unverified behavior is clearly identified.

A checkbox must not be marked complete based solely on a plan, a
source-code inspection, or an unsupported claim in an earlier report.

------------------------------------------------------------------------

## 7. Required Completion Report

At each stage boundary, report only the information necessary to
proceed:

1.  **Stage completed:** The exact stage and objective.
2.  **Files changed:** Actual file paths and a concise description of
    each change.
3.  **Tests:** Commands run and actual results, including failures and
    skipped tests.
4.  **Database/account operations:** State explicitly whether any were
    performed. For stages where writes are prohibited, confirm that none
    occurred.
5.  **Acceptance criteria:** Which criteria are verified, which remain
    incomplete, and the evidence for each.
6.  **Blockers:** Only genuine blockers that require a decision or
    action.
7.  **Next action:** One concrete next step.

Do not produce a new broad audit or restate the entire project status at
every stage. Do not repeat previous evidence unless a change makes it
necessary to revalidate.

------------------------------------------------------------------------

## 8. Stop Conditions

Stop before the affected operation if:

-   The staging project cannot be unambiguously identified.
-   The configured target appears to be production or its status is
    contradictory.
-   The current Git state reveals work that could be overwritten or
    lost.
-   The intended account, role, profile, or tenant association is
    uncertain.
-   A database write lacks the required explicit approval.
-   A proposed fix risks breaking the working College Admin login and
    the risk has not been resolved.
-   The affected code path cannot be secured without expanding beyond
    this scope.
-   A test fails in a way that undermines the required security
    guarantees.

When stopping, explain the exact reason and the smallest action needed
to unblock the work. Do not silently guess, bypass the guard, or broaden
the task.

------------------------------------------------------------------------

## 9. Final Objective

Deliver a secure CampXSync login and authorization foundation in which
every required user has a valid authentication identity, the correct
authoritative role, and the correct college/profile association. Verify
role-specific dashboards only where those dashboards actually exist. Do
not treat a Student Dashboard as implemented or build one under this
scope.

The order is deliberate:

**Establish context → fix code → verify staging identities → obtain
approval → create or correct missing mappings → verify actual
role-specific login.**

Do not reverse this order, create accounts prematurely, or expand the
task into unrelated work.
