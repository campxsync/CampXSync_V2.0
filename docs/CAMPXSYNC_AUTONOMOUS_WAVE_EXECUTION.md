# CampXSync Autonomous Wave Execution Controller
## Waves 6–10 | Resume on `continue` | Manual-Blocker Stops Only

**Purpose:** Run the existing CampXSync development workflow continuously from the verified completion of Gate G1 through Waves 6, 7, 8, 9, and 10. Use this document as the execution instruction when opened in the existing Antigravity project.

**Repository:** `D:\CampXSync\CampXSync_V2.0`

**Only project-control references authorized by this controller:**
1. `./agent/campxsync-wave-control.md`
2. `./docs/project-control/WAVE_IMPLEMENTATION_PLAN_W4-W11.md`

Do not treat this file as a replacement for either reference. Do not create a parallel wave-control system.

---

## 1. Mission and stop boundary

- Treat **Gate G1 as completed**. Do not repeat G1 work unless a specific new failure or dependency makes a targeted recheck necessary.
- Resume from the actual repository state and the last verified execution checkpoint.
- Complete **Wave 6, Wave 7, Wave 8, Wave 9, and Wave 10**, in the order and scope defined by the two authorized project-control references.
- Implement, integrate, test, correct ordinary defects, and verify each wave before advancing.
- **Stop after Wave 10. Do not begin Wave 11**, even if the roadmap describes it.
- Do not claim completion based only on an earlier report, successful compilation, or an agent's assertion. Use current, relevant evidence.

## 2. Start and recovery procedure

At the start of every session, including after an IDE restart, computer restart, network interruption, or context reset:

1. Read this controller's instructions and its **Execution Checkpoint** section at the bottom.
2. Read `./agent/campxsync-wave-control.md`.
3. Read `./docs/project-control/WAVE_IMPLEMENTATION_PLAN_W4-W11.md`.
4. Inspect the current Git branch, HEAD, working-tree changes, and untracked files without modifying them.
5. Compare the checkpoint with the actual repository and test evidence. The repository is authoritative if the checkpoint is stale; reconcile carefully and never discard work.
6. Establish the next incomplete, safe task. Resume from the last verified step; do not restart a completed wave or repeat completed investigations without a concrete reason.
7. Verify the database target before any database operation. Never reveal credentials or secrets.

If there is no checkpoint yet, create the initial checkpoint in the section at the bottom of this same file, recording the current branch, HEAD, working-tree status, current wave/task, and known approvals. Do not edit other project-control documents to establish a checkpoint.

## 3. Autonomous execution loop

For each wave from 6 through 10:

1. Derive the wave scope and acceptance criteria from the two authorized project-control references.
2. Inspect existing implementation and reuse it. Avoid redesigning completed database structures, policies, migrations, services, or UI without evidence that a change is needed.
3. Break the wave into small, verifiable tasks and record the current task in the Execution Checkpoint.
4. Implement the necessary changes within the existing architecture and current repository/branch.
5. Use the connected Supabase MCP for inspection and supported operations. Determine its actual project target, permissions, and read/write capability; never assume DDL is supported.
6. Run relevant targeted tests after each meaningful change. Fix ordinary code, build, test, and integration failures autonomously within the authorized scope, then rerun the affected checks.
7. Verify persisted data, API Gateway integration, UI workflow, authorization, tenant isolation, transaction behaviour, and outbox/event behaviour where applicable to the wave.
8. Update the Execution Checkpoint after every meaningful verified milestone and before any operation that could be interrupted.
9. Once the wave's defined acceptance criteria are satisfied, record evidence and mark it complete in the Execution Checkpoint. Then proceed automatically to the next wave without asking the user for routine confirmation.
10. After Wave 10 is verified and recorded complete, set the controller status to `STOPPED_AT_WAVE_10` and stop.

Do not pause for routine progress reports, ordinary implementation choices, or test failures that can be investigated and corrected safely. Keep updates concise and checkpoint-focused.

## 4. Supabase MCP and database operations

Use the connected Supabase MCP whenever it supports the needed operation. Before relying on it, inspect the active configuration and permissions. If the MCP is read-only or lacks a capability, do not attempt to bypass that restriction.

### Mandatory target rules

- Designated development project reference: `azulbaxszsqxymqmlwfe`
- Protected production project reference — **never connect to or modify**: `ekspahbsbwdpraaqfqui`
- Prohibited legacy staging reference — **do not use**: `kpwdvvcschankgggitnj`

Verify the actual target using the established repository guard or trusted target-identification mechanism before each database write operation. If the target is missing, ambiguous, inconsistent, or cannot be verified, stop with a Manual Blocker.

### Approval rules

Follow the existing database-write approval rules in force for this repository. This controller is **not blanket approval** for migrations, DDL, grants, RLS/policy changes, seed scripts, tenant provisioning, destructive changes, or other database mutations.

- Read-only inspection and authorized local code/test work may continue autonomously.
- A database mutation may run only when the exact target and exact operation have the approval required by the existing rules.
- Approval for one operation does not authorize unrelated changes.
- If approval is required but missing, prepare the exact proposed change, target, purpose, risk, and verification plan; checkpoint it and stop with a Manual Blocker.
- Never weaken or disable RLS, bypass tenant context, use production credentials, or hide a failed write to make a test pass.
- If a write's outcome is uncertain after an interruption, inspect the database state before considering any retry. Do not blindly repeat a non-idempotent write.
- Do not print or store passwords, tokens, connection strings containing credentials, API keys, or other secrets in the checkpoint or logs.

## 5. Git and repository safety

- Preserve the current branch, HEAD history, user changes, and untracked files.
- Do not run `git reset`, `git clean`, stash, or branch-switch operations.
- Do not commit or push unless the user separately authorizes it.
- Do not overwrite unrelated files or discard changes made by the user or another agent.
- Avoid editing existing project-control and handover documents. The only project-control references to use are the two named in this controller.
- This controller file's **Execution Checkpoint** section may be updated to persist progress. Do not rewrite or remove the instructions above that section.
- Do not create additional control documents, duplicate roadmaps, or competing state files.

## 6. Manual Blocker policy

Stop only when safe progress genuinely requires user intervention or a required external dependency cannot be resolved safely.

Examples of Manual Blockers:
- Required database-write approval is missing.
- The active database target cannot be conclusively verified.
- Required credentials or permissions are unavailable and cannot be obtained through the authorized setup.
- A destructive or irreversible choice is outside the approved scope.
- A material architecture/security/data-integrity decision cannot be resolved from the existing project-control references.
- A required external service or environment is unavailable and no safe alternative can verify the acceptance criterion.
- Repository corruption or an environment problem prevents safe continuation.

**Not Manual Blockers by themselves:** ordinary compilation errors, failing tests, normal integration defects, formatting issues, or a need to refine code. Investigate and resolve these autonomously when safe.

When blocked:
1. Set the checkpoint status to `MANUAL_BLOCKER`.
2. Record a blocker ID, exact issue, last verified task, current branch/HEAD, evidence, attempts made, what remains, and the precise user action/approval required.
3. Record any independent, safe work that can continue. Continue such work if it does not depend on the blocker and does not violate wave ordering or safety rules.
4. Do not repeatedly ask for information already contained in the two authorized references or the checkpoint.
5. Do not claim the blocked task or wave is complete.

When the user supplies the required information or approval and says `continue`, resume the blocked task after re-verifying the relevant state. Do not interpret `continue` as blanket approval for other operations.

## 7. Resume command: `continue`

Treat a user message consisting of `continue` (case-insensitive, including a short instruction such as “please continue”) as an instruction to resume this workflow.

On `continue`:
1. Read the Execution Checkpoint and both authorized project-control references.
2. Inspect current Git and relevant task state.
3. Check whether the recorded blocker has been resolved and whether any operation was interrupted with an uncertain outcome.
4. Resume at the last verified safe step.
5. Do not repeat completed work or restart a wave from its beginning without a concrete reason.
6. If a required approval or safety condition is still missing, explain that exact blocker and stop.
7. Continue autonomously through the remaining waves once unblocked.

This command resumes work; it does not authorize production access, unapproved database mutations, commits, pushes, or unrelated scope.

## 8. Verification and evidence standard

For each wave, record the actual tests and checks that are relevant to the acceptance criteria in the roadmap. Include exact commands, exit status, real test counts, skipped tests, and any limitations.

Distinguish clearly between:
- implemented,
- compiled,
- unit-tested,
- integration-tested,
- verified against the designated development database,
- end-to-end verified,
- blocked or not verified.

Never describe skipped live tests as passing. Never claim tenant isolation is verified without relevant negative cross-tenant evidence. Never claim a database migration or policy change occurred unless its execution and resulting state were verified.

## 9. Wave completion and final stop

A wave is complete only when its roadmap acceptance criteria are met or any exception is explicitly recorded as a blocker rather than silently waived. Before advancing, checkpoint:
- wave and tasks completed,
- files changed,
- tests and exact results,
- database operations and approvals (without secrets),
- persistence/security/tenant-isolation evidence,
- known limitations,
- next task.

After Wave 10:
- Perform the final Wave 10 acceptance review.
- Record the final branch, HEAD, and working-tree status.
- Record any unmet criteria or unresolved risks honestly.
- Set status to `STOPPED_AT_WAVE_10`.
- Do not start Wave 11, production cutover, or production database operations.
- Wait for the user's next instruction.

---

# Execution Checkpoint
> Antigravity: maintain this section as the persistent execution state. Update only this section when checkpointing; preserve all controller instructions above. Use actual evidence, not guesses. Never include secrets.

- **Controller status:** `STOPPED_AT_WAVE_10`
- **Last verified completed gate:** `G1 (Verified live on azulbaxszsqxymqmlwfe)`
- **Current wave:** `Wave 10`
- **Current task:** `Wave 10 Complete. Halted at Wave 10 stop boundary.`
- **Last verified checkpoint:** `Wave 10 Complete. Frontend direct database access retired across 10 API routes and dashboard pages. Dev test session / mock impersonation gate hard-disabled for all production environments in test-session-gate.ts. Integrated microservice regression passing across API Gateway (154/154), Student Service (39/39), Timetable Management Service (38/38), Attendance Service (35/35), Analytics Service (38/38), and Institute Admin Service (41/41) — 345/345 tests passing with 0 errors. TypeScript static analysis exited 0.`
- **Repository path:** `D:\CampXSync\CampXSync_V2.0`
- **Branch / HEAD:** `feature/java-backend-integration` / `5952ea4f8f3070188b33fc290bf9df5f284a7697`
- **Working-tree status:** `Preserved existing uncommitted files from previous waves`
- **Last completed task:** `Wave 10 Frontend Database-Access Retirement and Integrated E2E Regression`
- **Last test evidence:** `frontend tsc --noEmit: 0 errors; api-gateway: 154/154 PASS; student-service: 39/39 PASS; timetable-management-service: 38/38 PASS; attendance-service: 35/35 PASS; analytics-service: 38/38 PASS; institute-admin-service: 41/41 PASS`
- **Database target:** `Verified development ref azulbaxszsqxymqmlwfe via Supabase MCP (read_only=true) and live JDBC test`
- **Outstanding Manual Blocker:** `None.`
- **Next exact action:** `Execution halted at Wave 10 completion. Awaiting user authorization to proceed to Wave 11 (Production Readiness & Final Cutover).`
- **Wave 6 status:** `COMPLETE`
- **Wave 7 status:** `COMPLETE`
- **Wave 8 status:** `COMPLETE`
- **Wave 9 status:** `COMPLETE`
- **Wave 10 status:** `COMPLETE`
- **Final stop condition:** `STOPPED_AT_WAVE_10`

