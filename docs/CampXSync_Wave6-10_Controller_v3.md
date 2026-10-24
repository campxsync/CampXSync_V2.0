# CampXSync — Autonomous Wave Execution Controller v3
## Waves 6–10 | Aligned to WAVE_IMPLEMENTATION_PLAN.md | Persistent | Manual-Blocker-Only Stops

**Branch:** `feature/java-backend-integration`
**Flow every wave must prove (at the strongest safe test tier):** UI/Frontend → versioned API Gateway → Java domain service → PostgreSQL → JSON events for the Shared Services & Intelligence layer
**Final stopping boundary:** Wave 10. Wave 11 (production readiness) and Gate G3 are out of scope.

---

## 0. Control parameters (the user may edit these between runs)

```
WAVE_PLAN_FILE      = WAVE_IMPLEMENTATION_PLAN.md   # confirm the repo path; rev 2026-10-21
WAVE_GATES          = every-wave                    # every-wave | after-waves:7,9 | none
COMMITS_AUTHORIZED  = no                            # plan principle 2; see section 10
MAX_FIX_ATTEMPTS    = 3                             # genuinely different approaches per problem
DEV_PROJECT_REF     = azulbaxszsqxymqmlwfe          # the ONLY database ever written to
```

### Precedence

1. `WAVE_PLAN_FILE` governs scope, gates (G1–G3), cross-cutting requirements and wave exit criteria. This controller governs how the run is executed, persisted and stopped.
2. Where the two differ, **the stricter safety rule wins**, with exactly one explicit override: for Waves 6–10 the plan's "stop after the current task / current wave" is replaced by `WAVE_GATES`.
3. The plan's "Immediate next task: Wave 4" is superseded by the recorded checkpoint. Start from the wave the verified checkpoint shows.
4. If `WAVE_PLAN_FILE` is missing or unreadable, create Manual Blocker **MB-PLAN** and stop. Never invent wave scope.

---

## 1. Mission

Continue CampXSync from the repository's actual current checkpoint and complete:

- Wave 6 — Attendance
- Wave 7 — Attendance corrections
- Wave 8 — Dashboards
- Wave 9 — College onboarding
- Wave 10 — Frontend DB-access retirement and integrated E2E

Work autonomously across inspection, implementation, testing, integration, defect correction, documentation and checkpoint maintenance. Do not ask for routine confirmation, status reports or ordinary coding decisions.

All earlier results (Waves 1–5) are **reported results, not independent verification**. Before starting Wave 6, spot-check the key exit evidence of Waves 4 and 5 (student references and roster; slot IDs, conflict rules, slot-changed event) once. If a real gap is found, record it and create a Manual Blocker. Do not silently redo earlier waves or hide the gap.

---

## 2. Architecture invariants (apply to every task)

1. **Request path:** UI → versioned API Gateway (`/api/v1`) → Java service → PostgreSQL. Features delivered in these waves must not read or write business data directly from the frontend (Supabase client or `lib/db.ts`).
2. **Identity:** Supabase Auth is the identity provider; the frontend sends its Supabase JWT to the gateway, which validates it. Role, tenant and permissions come only from verified identity plus authoritative database records. Never trust frontend-supplied role or tenant headers.
3. **Gateway → service trust:** the HMAC-signed protocol (user, tenant, role, college, department, request id; signature, audience, timestamp and replay checks). Services are not reachable except through the gateway.
4. **Database access (Gate G1):** business DB access uses a dedicated application login role without `BYPASSRLS`, with transaction-scoped tenant/user context. A privileged role must never be used to claim tenant isolation.
5. **Ownership:** each domain service is the sole writer of its canonical data. Reuse existing shared audit/outbox, IAM, student, academics and document/workflow structures. Create no duplicate source of truth. Verify existing migrations before proposing any new table.
6. **Transactions and events:** PostgreSQL domain state is authoritative. Events use the transaction-aware outbox, written in the same transaction as the business change. Command success is distinct from downstream delivery.
7. **Persistence before routing:** no in-memory service is routed through the gateway until persistence, authn/authz and tenant isolation are verified for it.
8. **Legacy and platform:** keep `/api/v1`; legacy `/v1/*` stays frozen; keep `/actuator/health`. Java 8 and the current build toolchain stay as they are (no upgrade without separate approval). Cursor pagination for large datasets, page/size for catalogues.
9. **Secrets:** never print, log, commit or report credentials. `.env.dev` stays untracked; `frontend/.env.local` stays untouched.

### 2.1 Test tiers (state the tier for every claim)

`T1 mock` · `T2 in-memory` · `T3 isolated PostgreSQL bootstrapped from existing migrations` · `T4 approved dev database (azulbaxszsqxymqmlwfe)` · `T5 browser`

- Run each workflow at the strongest safe tier available, and record the exact tier.
- If the full path is unavailable, test the strongest safe tier and mark the missing evidence **PENDING**. Never present a lower tier as a higher one.
- RLS and tenant-isolation claims require T3 or T4 **and** Gate G1 verified closed. Otherwise write "tenant isolation not verified (G1 open)".
- A test double for the Intelligence layer is not live integration; mark real integration PENDING.

### 2.2 Gate G1 handling

At the start of the run, record the evidence for G1 and confirm it applies to `DEV_PROJECT_REF`.

- **Closed:** record the evidence and continue.
- **Open:** database persistence and DB writes are blocked (Manual Blocker **MB-G1**). Role/grant/RLS scripts are prepared for review, never executed unapproved. Safe design, code without DB connections, unit tests and T3 isolated tests continue.

Gate G2 (isolated-PostgreSQL tests in CI) needs a push to run, and pushing is prohibited. Keep the CI configuration ready, and report G2 as "configured, not executed" unless the user runs it.

---

## 3. Intelligence-layer JSON contract

The Shared Services & Intelligence layer consumes **JSON only**; it never reads service tables directly.

- **Reuse the Wave 1 event/outbox envelope** (correlation ID, tenant context, event time, entity IDs, schema version). Do not create a second format.
- Register each event type in the event registry with: owner service, schema version, privacy classification, and the JSON Schema stored in the repo.
- Events are tenant-scoped, minimal (no personal data the layer does not need), and idempotent by event id.
- Tests per event type: tenant context present, idempotent redelivery/duplicate, failure and retry, and schema validation (contract test).
- Report delivery tier for each event: outbox row written, relay delivered to test double, or delivered to the real Intelligence endpoint. The background relay was deferred in Wave 1; do not claim delivery the relay does not provide.
- Schema changes to an accepted event are additive only; a breaking change is a Manual Blocker.

Events expected in this run: session-opened and attendance-marked (Wave 6), attendance-correction events (Wave 7), dashboard read models/exports only if the approved metric definitions need them (Wave 8), tenant-provisioned (Wave 9).

---

## 4. Mandatory first action: recover state

On the first run and after every interruption or `CONTINUE`:

1. Read the **Resume Brief** at the top of `docs/project-control/PROGRESS.md` first. Open other files only when it or the current task points to them (avoids re-reading everything on every resume).
2. On the first run only, also read: `WAVE_PLAN_FILE`, `.agent/campxsync-wave-control.md`, `prompts/NEXT_ANTIGRAVITY_TASK.md`, `docs/reports/WAVE_COMPLETION_REPORT_TEMPLATE.md`, the completion reports of Waves 1–5, `MASTER_EXECUTION_PROMPT.md`, `.planning/STATE.md` (if present), `docs/staging/00-checkpoint.md`, `docs/staging/04-completion-criteria.md`, `docs/staging/05-execution-log.md` and `docs/handover/HANDOVER.md`, where present.
3. Inspect branch, HEAD, working tree, modified and untracked files. The recorded baseline commit is `105122b6b5b17d3eadc70e7e8094008facb4285a`, and earlier waves' work is uncommitted. Do not reset, clean, stash, switch branches, rebase, commit or push. Preserve everything.
4. Verify the database target (section 7.1). Never print credentials.
5. Reconcile the checkpoint against real files and Git state; determine whether the last operation completed, failed or is uncertain.

If state is ambiguous or repository safety cannot be established, create a Manual Blocker and stop.

---

## 5. Phase 0: planning pass (first run only, read-only, then stop)

Do this before any implementation or database write for Waves 6–10.

### 5.1 Plan outputs

1. For each wave: tasks, dependencies, services and gateway routes touched, frontend screens and their current direct-DB consumers, tables used, and events/JSON outputs.
2. Every proposed database change (migration, DDL, grant, RLS change, seed, provisioning, matview, index) written as a **script file only, not executed**, under `docs/project-control/wave-6-10-db-plan/`.
3. `docs/project-control/APPROVALS.md` with one row per script: `ID | file | sha256 | target ref | purpose | risk | rollback | verification | status` (all `PENDING`).
4. Event registry entries and JSON Schemas planned per wave.

### 5.2 Decision Register (so the run does not stall mid-wave)

Create `docs/project-control/DECISIONS.md`. For each item below, propose a recommended default with reasons and risks. The user marks each row `ACCEPTED`, `EDITED` or `REJECTED`. Only the user changes status.

- **Wave 6:** marking window; attendance statuses; who may mark (assigned faculty, substitute authority); roster snapshot timing; behaviour when a student's lifecycle changes mid-term; bulk-marking limits; HoD/admin oversight scope.
- **Wave 7:** correction time limit; approver authority matrix; requester/approver separation (no self-approval).
- **Wave 8:** metric definitions (present counts, denominators, corrections, withdrawn students, date ranges); aggregates per role (platform admin, college admin, HoD, faculty; student view in or out); latency budget for p50/p95; any materialized view/index (each needs its own approval row).
- **Wave 9:** provisioning contract; initial admin assignment; how privileged Supabase Auth credentials are supplied (server-side only, narrowly scoped); compensation/rollback approach; the test environment for a third tenant.
- **Wave 10:** list of remaining direct-DB consumers to migrate or retire; Intelligence-layer endpoint (reachable or not); approach for removing dev impersonation/test sessions from deployable builds.

Rule: a wave may use only `ACCEPTED` or `EDITED` rows. If a wave reaches an unresolved row with security or data-integrity consequences, create a Manual Blocker. Rows for lower-risk items may proceed with their recorded default only if the user marked them `ACCEPTED`.

### 5.3 Stop

Update the checkpoint and **stop** with a short summary. This is the one planned review stop. The user approves scripts and decisions, then says `CONTINUE`.

---

## 6. Execution loop

For each wave 6 through 10, in order:

1. Read the plan's wave section, acceptance criteria and dependencies; reuse existing implementations.
2. Document the wave's design rules first (as the plan requires), consistent with the Decision Register, before implementing.
3. Break the work into small, independently verifiable tasks; implement without asking about ordinary coding decisions.
4. Use the Supabase MCP for inspection and supported operations. Discover its real capabilities and permissions and prefer read-only. If MCP cannot do an operation, use only an approved, repository-supported alternative. Never bypass read-only limits, target guards or approvals.
5. Run targeted tests, fix ordinary failures within scope, rerun.
6. Verify persisted read-back, authorization, tenant isolation (tier-qualified), rollback behaviour and event/outbox output.
7. Migrate frontend consumers to the gateway as each API arrives, or record the exact remaining consumers and why.
8. Update the checkpoint after each verified milestone and take a safety snapshot (section 10).
9. Advance to the next task, and to the next wave only after the wave meets section 12 and any configured gate (section 13) has been cleared.

Do not mark skipped, blocked, compile-only or unexecuted tests as passed. Do not expand scope silently.

---

## 7. Database safety

### 7.1 Target allowlist (deny by default)

- The **only** database ever written to is `DEV_PROJECT_REF` (`azulbaxszsqxymqmlwfe`).
- Before **every** database write, verify the actual target identity matches. Any mismatch or inability to establish identity is a Manual Blocker.
- Confirm the Supabase MCP is connected to the account/organization that owns the dev project and exposes only that project.
- Production ref `ekspahbsbwdpraaqfqui` and legacy staging ref `kpwdvvcschankgggitnj` must never be connected to, read from or modified. Finding either in any configuration used by a task is a Manual Blocker.
- Prefer T3 (isolated PostgreSQL) for tests. Use the dev project (T4) only for approved verification steps.

### 7.2 Approval by manifest

- A mutation runs only if its row in `APPROVALS.md` is `APPROVED` **and** the script's current sha256 equals the recorded hash. Approval is per exact step; approving one never authorizes another.
- A changed, new or unlisted operation is a Manual Blocker: prepare the exact change, target, reason, risk, rollback and verification, then stop.
- Irreversible or destructive operations (data deletion, dropping objects, tenant provisioning, deleting files that have uncommitted changes, retiring `lib/db.ts`) are separate approval rows.
- Never weaken RLS or tenant isolation to make a test pass.

### 7.3 Write-ahead operation log

Maintain append-only `docs/project-control/DB-OPS-LOG.md`:

- Before each operation: `INTENT | op-id | approval-id | script hash | target ref | timestamp`.
- After it: `RESULT | op-id | success/failure | verification evidence | timestamp`.
- On recovery, an `INTENT` with no `RESULT` is uncertain: inspect the database state first, record the finding, and never blindly re-run.

---

## 8. Manual Blocker policy, retry cap and integrity rules

### 8.1 Stop only when further safe progress genuinely requires the user

- Missing approval for a database change (7.2), or an open Gate G1 that blocks persistence (2.2).
- Ambiguous or unsafe database target.
- Missing credentials, permissions or external configuration (including privileged Supabase Auth credentials for Wave 9) that cannot be resolved safely.
- An unresolved Decision Register row with security or data-integrity consequences.
- A destructive or irreversible step outside approved scope.
- A change that would break an invariant in section 2 or an approved contract.
- A genuine gap found in earlier-wave evidence (section 1).
- An external dependency that blocks required verification with no safe alternative (record the tier reached and the pending evidence instead, where a safe lower tier exists).
- A repository or environment failure that cannot be safely recovered.
- Retry cap reached.

Do not stop for failing unit tests, compiler errors, ordinary integration defects or normal refinement.

### 8.2 Retry cap

After `MAX_FIX_ATTEMPTS` genuinely different approaches to the same problem fail, create a Manual Blocker listing what was tried. Do not loop.

### 8.3 Integrity rules

Never: delete, skip or weaken a test to pass; loosen RLS or authorization; fake or hard-code results; overclaim tenant isolation, RLS or readiness; expand scope silently.

### 8.4 Blocker record

ID and exact reason; last verified checkpoint; work completed and tests run; sanitized error evidence; the exact action or approval needed; and safe work that can continue independently (do that work first, then stop).

---

## 9. Persistent checkpoint, reports and handoff

The single authoritative checkpoint is `docs/project-control/PROGRESS.md`. Update the existing files; create no competing sources of truth.

- **Resume Brief** at the top (≤ 60 lines): current wave/task/status, next exact action, open blockers, last verified HEAD.
- A fenced `execution-state` JSON block: wave, task id, status, files changed, branch, HEAD, tests (command, result, actual counts, skips, failures, tier), database target identity, `DB-OPS-LOG.md` op ids, approval ids and decision ids used, G1/G2 status, event-registry status, remaining work, blockers and resolution status.
- At each wave: `docs/reports/wave-N/COMPLETION_REPORT.md` (using `WAVE_COMPLETION_REPORT_TEMPLATE.md`) and `prompts/NEXT_ANTIGRAVITY_TASK.md` derived from the evidence.

Write atomically (temp file then replace; on Windows `Move-Item -Force`), and re-read to confirm the write before claiming a checkpoint was saved.

After a restart of Antigravity, the OS or the network: re-read the Resume Brief, reconcile against the repository and `DB-OPS-LOG.md`, resume from the last verified safe step, and never restart a completed wave solely because the session restarted.

---

## 10. Git rules and safety snapshots

- Stay on `feature/java-backend-integration`. Never switch branches, reset, clean, stash, rebase or push.
- **Do not commit** unless `COMMITS_AUTHORIZED = yes`. Earlier waves' uncommitted work lives in the same working tree, so a commit would sweep it in; that decision is the user's.
- Because the work is uncommitted, protect it with **snapshots that do not alter repository state**: after each verified task, write `git diff` patches plus a copy of changed and untracked files (excluding `.env*` and credentials) to a folder **outside** the repository, e.g. `..\CampXSync_snapshots\wave-N-task-M\`. Record the snapshot path in the checkpoint.
- Take a snapshot before deleting or replacing any file that has uncommitted changes.

---

## 11. Wave scope for this run

Use `WAVE_PLAN_FILE` as the authority; this is a working summary.

**Wave 6: Attendance.** Sessions derive from timetable slots. Marking window, assigned/substitute faculty authority, statuses, roster snapshot, mid-term lifecycle behaviour per the Decision Register. Idempotent single/bulk marking; reject duplicates and invalid roster; HoD/admin oversight with scope and cursor pagination. Events: session-opened, attendance-marked. Tests: non-roster students, duplicates, outside-window or unassigned marking, department scope, read-back, cross-tenant and unauthorized denial.

**Wave 7: Attendance corrections.** Request → approval → apply; requester cannot self-approve; agreed time limit. Append-only history (original and corrected values, actor, reason, timestamps); concurrency and idempotency; correction event via outbox. Tests: authority matrix, self-approval denial, concurrent apply, replay, history integrity, tenant isolation, read-back of original and corrected values.

**Wave 8: Dashboards.** Metric definitions documented first; per-role aggregates served through gateway APIs only (no ad hoc frontend queries); dashboards are not a second source of truth. Any DB object needs a separate approval row. Measure p50/p95 against the agreed budget. Tests: reconcile to fixtures including corrections; role and tenant leakage; latency; at least two tenants in a safe environment.

**Wave 9: College onboarding.** Inspect the provisioning contract; idempotent onboarding and initial admin assignment; platform-admin-only; prevent tenant/role escalation; privileged Supabase Auth credentials server-side only and narrowly scoped; defined compensation/rollback; tenant-provisioned event. Writes only in the approved dev environment with exact-step approval. Tests: repeated onboarding, partial-failure recovery, escalation attempts, a third test tenant with isolation tests where safe.

**Wave 10: Frontend DB-access retirement and integrated E2E.** Inventory remaining `lib/db.ts` and other direct DB consumers; migrate only where equivalent APIs exist; retire direct access only after consumers and tests are verified. Remove or hard-disable dev impersonation/test sessions from deployable builds, and test gateway rejection in a production configuration without connecting to production. Browser regression by role across student, timetable, attendance, corrections, dashboards and onboarding, covering request/response, read-back, cross-tenant denial, role authorization and signed context. Verify event contracts against the real Intelligence endpoint if reachable; otherwise use a test double and mark real integration PENDING.

---

## 12. Definition of done for every wave

A wave passes when all of these hold:

1. The approved scope is implemented, within the Decision Register.
2. The full chain UI/Frontend → Gateway → Java service → PostgreSQL has been exercised at the **strongest safe tier**, including persisted read-back and cross-tenant denial, with the tier stated. A unit test of one layer alone does not satisfy this.
3. Role, tenant and department scope are tested. Isolation is claimed only per section 2.1 and G1.
4. Events are registered (owner, schema, privacy classification) and tested for tenant context, idempotency, failure and retry; delivery tier stated.
5. Frontend consumers are migrated, or the exact remaining consumers and reasons are recorded.
6. Relevant regression from earlier waves passes.
7. `PROGRESS.md`, the wave completion report and `NEXT_ANTIGRAVITY_TASK.md` are updated, with actual commands, test counts, skips and failures, plus what was **not** tested.
8. No unauthorized database action, commit or push occurred.

Evidence that cannot be obtained safely is recorded as **PENDING** and carried forward in the report. It must never be hidden by marking the wave fully verified. Required report evidence follows plan section 13.

---

## 13. Wave gates

- `every-wave`: after a wave meets section 12, write its report, update the checkpoint and `NEXT_ANTIGRAVITY_TASK.md`, then **stop** for review. Resume on `CONTINUE`.
- `after-waves:N,M`: stop only after those waves.
- `none`: continue directly to the next wave.

A gate is a review stop, not a blocker. Pending evidence stays visible in the report regardless of gate setting.

---

## 14. User commands

`CONTINUE`, `continue` or `resume`:

1. Read the Resume Brief and reconcile with the repository.
2. Resolve the latest Manual Blocker if the user supplied what it needed; re-read `APPROVALS.md` and `DECISIONS.md` for newly marked rows.
3. Resume from the last verified safe step. Do not restart completed work or produce a general status report.
4. If a blocker still needs authorization, state the specific outstanding requirement and stop.

`CONTINUE` is not blanket approval for any database change that is not `APPROVED` in the manifest.

---

## 15. Final closeout after Wave 10

Stop all wave progression and produce one consolidated report containing:

- Completed scope for Waves 6–10, known limitations and incomplete requirements.
- Actual test commands, results, counts, skips and failures, each with its tier.
- Verified end-to-end workflows (UI → Gateway → Java → Database → Intelligence JSON) and what remains PENDING.
- Tenant-isolation and authorization evidence, with G1 status.
- The event registry: types, owners, schemas, privacy classification, delivery tier, idempotency/failure/retry results.
- Frontend consumers migrated and remaining.
- Database changes with approval records and `DB-OPS-LOG.md` references.
- G1, G2 and G3 status (G3 is untouched; it belongs to the next plan).
- Final Git branch, HEAD and working-tree status, plus snapshot locations.
- Location of the final checkpoint.

Do not claim every CampXSync module is complete unless the evidence supports it. Do not start Wave 11, deploy to production, or touch the production database. Wait for the user's next plan.

---

## 16. Operating principle

**Execute, verify, checkpoint, continue.** Minimal narration, no duplicate audits, no repeated questions already answered by documentation. Stop only for a genuine Manual Blocker, a configured wave gate, or an interruption that needs the user to say `CONTINUE`.
