# Follow-up diagnosis after Student portal preferences batch (2026-11-17)

Dev project confirmed: Supabase `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev). No RLS, gateway, account, password, or test-data changes in this task.

## 1. Missing test runs — Cross-module E2E and cross-tenant security

| Harness | Command | Result |
|---|---|---|
| Cross-module E2E (CROSS-EN-010) | `node scripts/demo/run_stm_cross_en_010_e2e.mjs` | **32/32 PASS**, failed=0 |
| Cross-tenant pen test | `node scripts/demo/run_cross_tenant_pen_test.mjs` | **34/34 PASS**, vulns=0 |

Evidence:

- `docs/reports/STM_CROSS_EN_010_E2E_2026-11-16.json`
- `docs/reports/CROSS_TENANT_PEN_2026-11-16.json`
- `docs/reports/_verify_followup_cross_en_010.txt`
- `docs/reports/_verify_followup_cross_tenant.txt`

Both passed. No pre-preferences worktree compare required. Preferences backlog CSV was **not** marked done in this task (demo browser proof still open).

**Verdict:** PASS (both harnesses green).

## 2. Preferences in real Dev — login links and endpoint without a link

Active login-link table `stu.student_iam_links`: **0 rows** (active or otherwise).

| Test student | Email / identity | Active login link |
|---|---|---|
| Student One | `student1.campx@gmail.com` (`STU-DEV-2026-0001`) | **No** |
| Student Two | `student2.campx@gmail.com` (`STU-DEV-2026-0002`) | **No** |
| Student Three | display name only (`STU-DEV-2026-0003`) | **No** |
| E2E applicant | `e2e-applicant@campx.com` | **No** |

Live probe (student test-session on `http://localhost:3000`, Student One):

- `/api/student/me` → **200**, `iamUserId: null` (no link).
- `GET /api/student/portal-preferences` → **200** with Java **defaults** (empty shortcuts, default widget order, display defaults). Not denied, not an error.

Portal-preferences row RLS still keys off an ACTIVE `stu.student_iam_links` row; with no link the store read is empty and the service returns defaults. No links or accounts were created.

Evidence: `docs/reports/_verify_followup_prefs_nolink.json`

**Verdict:** No Dev test student has an active login link; preferences GET returns empty/default payload (200), not denied/error.

## 3. Student sign-in regression (diagnosis only)

### Observed

| Base URL | Verdict | After session | Cookie domain |
|---|---|---|---|
| `http://localhost:3000` | **PASS** | `/student` + dashboard | `localhost` |
| `http://127.0.0.1:3000` | **FAIL** | bounce to `http://localhost:3000/login?returnTo=%2Fstudent` | cookies remain on `127.0.0.1` |

Cause: demo/test-session cookies are bound to the request host, but a follow-on redirect lands on the **localhost** hostname. Middleware on localhost does not see `127.0.0.1` cookies → session gate sends the browser to login.

Batch 3 runner (`c51fc6e`) already documents and uses localhost:

```text
// Prefer localhost over 127.0.0.1 so test-session Set-Cookie host matches redirects.
const BASE = ... || 'http://localhost:3000';
```

Batch 5 runner defaults to `http://127.0.0.1:3000` (commit `a8601fe`), which reproduces the bounce.

### Worktree at batch-3 demo commit `c51fc6e`

Student sign-in step against live UI:

- `localhost` → **PASS**
- `127.0.0.1` → **FAIL** (same host mismatch)

Current HEAD shows the same split. This is **not** a preferences-domain code break and does **not** require a bisect of app logic: both commits pass on localhost and fail on `127.0.0.1`. The batch-5 demo failure is the demo BASE host choice relative to the session redirect host.

Evidence:

- `docs/reports/_verify_followup_signin_host.txt`
- `docs/reports/_verify_followup_batch3_signin_localhost.json`
- `docs/reports/_verify_followup_batch3_signin_ip.json`
- `docs/reports/_verify_followup_worktree_c51fc6e_signin.json` (localhost PASS)
- `docs/reports/_verify_followup_worktree_c51fc6e_signin.txt`
- `docs/reports/_verify_followup_worktree_c51fc6e_signin_ip.json` (127.0.0.1 FAIL)
- `docs/reports/_verify_followup_worktree_c51fc6e_signin_ip.txt`

**Verdict:** Host/cookie mismatch (demo BASE `127.0.0.1` vs redirect `localhost`); not a preferences commit regression. No fix applied.

## 4. Registrar student-row visibility (investigation only)

Shared runtime pattern across Enrolment / Lifecycle / Guardian / Documents:

1. Java binds tenant JWT claims and runs `SET LOCAL ROLE authenticated` inside `executeInTransaction`.
2. Domain tables are subject to FORCE RLS.
3. Registrar (R31) actions also use a shared **campus + Path-C delegation** Java gate (`requireRegistrarCampus` / `DelegationPort` active-check) for approve/reject-class operations.

How student rows are reached today:

| Area | How student context is read | Relies on `stu.students` SELECT under RLS? |
|---|---|---|
| Enrolment (STM-03) | Works mainly on enrolment / waitlist / cleared-course rows keyed by `student_id`; Registrar gates are Java campus/delegation | **No direct** `stu.students` read in persistence |
| Lifecycle (STM-04) | Works on `stu.lifecycle_*` rows keyed by `student_id`; same Registrar campus/delegation pattern | **No direct** `stu.students` read in persistence |
| Guardian (STM-05) | Several flows `SELECT college_id FROM stu.students ...` under the authenticated session before document/college work | **Yes** — student row must be visible to the actor |
| Documents (STM-06) | Staff lists/filters `doc.documents` by `college_id` / `owner_id` (joins IAM profiles), not canonical `stu.students` | **Indirect** — document-table RLS/college filters, not student-table read |
| Staff account-link desk (STM-07, related) | `findById` on `stu.students` **before** Java ACCOUNT.A delegation assert | **Yes** — RLS miss surfaces as “Student not found” (observed for pilot Registrar) |

`stu.students` SELECT policy (`students_select` in `database/migrations/Scripts_V3.0/60_rls.sql`) allows:

- `acd06.view.all` (all / college / program scope), or
- `acd06.view.own` + faculty advisee set, or
- `core.my_student_ids()` (own person / active guardianship).

Delegated Registrar is **not** expressed in that policy. Java may accept Path-C delegation, but PostgreSQL can still hide the student row → empty result / “Student not found” before or instead of a clean Java deny.

Options for letting delegated Registrar read student rows on staff screens (options only; no recommendation; no rule change):

| Option | Idea | Security trade-off |
|---|---|---|
| A | Grant Registrar college-scoped `acd06.view.all` (or equivalent view permission) for their campus | Broadest staff read of student master data at that college; excess if Registrar only needs a subset of fields/screens |
| B | Add an RLS SELECT branch limited to the Registrar’s assigned `college_id` (and optionally only when an ACTIVE Path-C delegation exists) | Narrower than tenant-wide view; still expands DB-visible PII beyond today’s deny-by-default; delegation-in-SQL needs a trusted helper and careful fail-closed design |
| C | Keep RLS as-is; have staff screens call a Java-owned projection that elevates only after Path-C check (service role / controlled bypass for that query) | Centralises auth in Java (good for audit) but weakens “RLS always binds the actor” if the bypass is too wide or reused |
| D | Avoid reading `stu.students` on Registrar desks; join only module tables the Registrar already can see (enrolment/lifecycle/documents patterns) | Least new student-master exposure; may leave desks that truly need master fields incomplete |
| E | Dual-control / break-glass: temporary college-scoped view grant with audit and expiry | Operationally heavy; residual risk during the grant window |

**Verdict:** Shared authenticated+RLS+delegation pattern; Enrolment/Lifecycle largely avoid `stu.students`, Guardian/staff-link hit it and fail closed for Registrar under current `students_select`. Options listed without a policy pick.

## 5. Evidence commit scope (exact paths)

See git commit message for the exact path list (no `git add -A`).
