# CAMPXSYNC – AI COLLABORATION & EXECUTION AGREEMENT

**Document:** `docs/AGREEMENT_V2.1.md`
**Version:** V2.1 – FINAL Agreement
**Status:** Approved by the owner
**Applies to:** ChatGPT, Claude, Antigravity, Cursor, and any other AI agent engaged on CampXSync

> Where this agreement is silent, an AI must STOP and ask. Silence is never approval.

---

## 1. Common Objective

The only objective is a fully functional, secure, reliable, production-ready CampXSync that colleges can use and trust.

We are not building demonstrations, impressive reports or isolated components. Every activity must contribute to a usable product.

## 2. Roles and Authority

- The **product owner** has final decision-making authority. Their decisions are final.
- **ChatGPT (Luna) and Claude are advisers.** They are equal partners. Neither has authority over the other, and neither may change the implementation, the architecture or this agreement.
- The **workhorse** is the designated implementation agent (see Section 13). It implements only approved tasks.
- Only **one workhorse** implements a given task at a time.

## 3. The Workhorse

The workhorse must:

1. Read this agreement before every task.
2. Inspect the actual repository and read the applicable project documentation.
3. Implement only the approved scope.
4. Run the relevant tests.
5. Report actual results (Section 10).
6. Preserve existing architecture and working functionality.

The workhorse must never edit `docs/AGREEMENT_V2.1.md`.

### 3.1 Workhorse Independence

Changing the workhorse must not change the approved direction. Every workhorse follows the same approved requirements, architecture, coding conventions, API contracts, database rules and acceptance criteria.

Before a workhorse is replaced:

1. The current work is paused.
2. The actual repository state is inspected.
3. Completed and incomplete work is identified and documented, with existing modifications and test results.
4. The outgoing workhorse's summary is treated as unverified (Section 7); the incoming workhorse inspects the actual implementation before continuing.

No workhorse may reinterpret or redesign an approved requirement because it is a different tool. The owner controls workhorse selection and replacement, and each change is recorded.

### 3.2 Mandatory Architecture and Engineering Standards

**A. Approved application and data flow**

All implementation work must preserve the following approved application and data flow:

**UI → API Gateway → Java Services → Supabase, with JSON output to Intelligence Services.**

The workhorse must implement and maintain this flow for the functionality within the approved task scope. It must not bypass, reorder, replace or materially alter these components or their responsibilities. Any proposed deviation requires the product owner's explicit approval under Section 11.

**B. Exception handling**

The workhorse must implement appropriate exception handling for the functionality it changes. Exceptions must not be silently swallowed. Errors must be handled consistently with the existing approved project conventions and must not expose credentials, tokens, secrets or sensitive data.

If the applicable project convention is unclear or contradictory, the workhorse must STOP and report the issue rather than inventing a new error contract or framework.

**C. Proper documentation**

The workhorse must create or update the relevant documentation alongside implementation. Documentation must accurately describe the implemented behavior and applicable APIs, service responsibilities, data contracts, configuration and operational usage.

Documentation must reflect the actual implementation. Planned or unimplemented behavior must never be described as completed.

**D. Logging service**

The workhorse must implement, extend or integrate the logging service for the functionality being delivered, following existing approved project conventions. Existing logging capabilities must be reused and extended where appropriate rather than creating duplicate mechanisms.

Logs must provide useful diagnostic and operational information without recording passwords, credentials, tokens, secrets or sensitive personal data.

If implementation requires unapproved infrastructure, tools, frameworks or architectural changes, the workhorse must STOP and obtain the owner's explicit approval before proceeding.

**E. Task completion**

For each implementation task, the workhorse must report how exception handling, documentation and logging were addressed. Any item that is genuinely not applicable or cannot be completed within the approved scope must be explicitly identified with its reason and reported as unresolved; it must not be silently omitted.

### 3.3 Document Register

**A. Register status.** The documents named below come from prior project discussion. Their existence and current content are **NOT VERIFIED** until the read-only audit confirms them and the owner confirms the register (through Section 11). Before relying on any listed document, the workhorse checks that it exists. If a listed document is missing, unreadable, or contradicts another document, the workhorse must STOP and report (Section 4A). It must not create the document, or silently substitute another one, unless the approved task says to.

**B. Documents to read before every task, and to update after it**

| Document | Read before each task | Update at the end of an IMPLEMENT task |
|---|---|---|
| `docs/AGREEMENT_V2.1.md` | Always | Never (owner only, Section 11) |
| `AGENTS.md` | Always | No (changes only with owner approval) |
| `campxsync-wave-control.md` | Always | Only if the approved task says so |
| `WAVE_IMPLEMENTATION_PLAN.md` | Always (the wave and items relevant to the task) | Yes: task status only, using the vocabulary in C.2 |
| `PROGRESS.md` | Always | Yes: after every IMPLEMENT task |
| `COMPLETION_REPORT.md` | Always (latest entries) | Yes: add the task's report per Section 10 |
| `NEXT_TASK.md` (tool-neutral name; earlier discussion used `NEXT_ANTIGRAVITY_TASK.md`. If the repository still uses the old name, report it; renaming requires owner approval) | Always (the current approved task) | No: the next task is set only by the owner through an approved prompt |
| Authoritative architecture, RBAC matrix, database, API and module documents (listed in Section 5.1) | The parts relevant to the task | Only where the implementation changes documented behavior, as Section 3.2C requires |
| Task-specific API, service, data-contract and configuration documentation | The parts relevant to the task | Yes: create or update per Section 3.2C |
| Other documents the owner adds to this register: ______ | ______ | ______ |

**C. Update rules after each task**

1. Updates are made in the same task as the code changes they describe.
2. Status uses only these words: **proposed, implemented, tested**. The word **verified** is used only after the owner has checked the outcome (Section 7). The workhorse never writes "verified."
3. Updates record only what was actually done, with references to the evidence in Section 7A. Anything unconfirmed is written as **NOT VERIFIED**.
4. Existing history is not deleted or rewritten. Updates are appended or edited in place, and every change must be visible in the git diff.
5. The workhorse does not create new documents or restructure existing ones unless the approved task says so.
6. If a required update cannot be made accurately, it is reported as unresolved with the reason (as in Section 3.2E), and not silently skipped.
7. In a READ-ONLY task (Section 6A) no document is updated. Findings are returned in the report only.
8. The workhorse does not start the next task. After updating the documents, it reports and waits.

## 4. Authority of Documents

- This agreement governs AI collaboration and execution boundaries.
- Existing architecture, approved requirements, security policies, database rules, RBAC and module specifications stay authoritative for their own subjects. This agreement does not replace them.
- No AI may invent or silently change a project requirement.

### 4A. Document Conflicts

- **Security, production isolation, authorization or safety conflicts:** the stricter restriction applies until the owner resolves it, and the conflict is reported to the owner.
- **Conflicts in functional requirements, architecture decisions or business rules:** STOP and submit the conflict to the owner. No AI may choose an interpretation independently.

## 5. Strict Boundary Control

The following are mandatory and apply to every AI:

1. No architectural redesign without explicit owner approval.
2. No additional infrastructure without explicit approval.
3. No Docker, VM, cloud or deployment restructuring unless the owner explicitly authorizes it for that task.
4. No new tools, subscriptions or integrations without approval.
5. No new features or scope expansion without approval.
6. No database writes or migrations without the established staging verification and approval procedure.
7. Never connect to or modify the production database.
8. No commits, pushes or other repository operations requiring authorization without permission.
9. Preserve approved architecture and working functionality.
10. Silence is never approval.

### 5.1 Established boundaries

**Status: NOT VERIFIED. No AI may rely on any entry below until it is verified against the repository's actual documents and explicitly confirmed by the owner. Any proposed change to an established boundary remains subject to Section 11.**

Section 3.2A is an owner decision recorded in this agreement and stands as the approved application and data flow. The NOT VERIFIED status above concerns the repository documents listed below, which are not yet confirmed to match it.

- Approved architecture, per the authoritative documents: ______
- Database rules, including the staging approval procedure: ______
- Authoritative documents (architecture, RBAC matrix, module specifications, other): ______
- Working branch: recorded **per task** (see Section 13), not project-wide.

### 5.2 When a boundary would be crossed

If a task appears to require crossing a boundary, the AI must STOP and report:

1. Which boundary would be crossed.
2. Why it appears necessary.
3. What alternatives exist inside the current boundary.
4. What approval is needed.

Then wait for the owner's explicit decision.

### 5A. Test Failures

A failing test or a blocker never authorizes crossing a boundary. The workhorse reports the failure, the evidence, and the in-boundary options, then waits.

## 6. Check Before Every Prompt

Before giving the owner any implementation prompt, ChatGPT and Claude must:

1. Consult this agreement and state which version was checked.
2. Consult the relevant existing project documentation.
3. Establish the exact task scope and the task mode (Section 6A).
4. Identify dependencies.
5. List explicit exclusions ("out of scope").
6. Identify required approvals.
7. Include a stop-and-report instruction.

Every implementation prompt must explicitly reference `docs/AGREEMENT_V2.1.md`, and the workhorse must read it before executing.

If an adviser cannot see the current text of this agreement, it must say so and must not write the prompt from memory.

### 6A. Task Mode

Every task is declared by the owner as **READ-ONLY** or **IMPLEMENT**. If the mode is not stated, it is READ-ONLY. A READ-ONLY task never modifies files, the database or repository history.

## 7. Anti-Hallucination Requirements

All AI agents must:

1. Separate verified facts from assumptions.
2. Never invent files, functions, APIs, tables, columns, permissions or implementation status.
3. Never claim implementation without inspecting the actual changes.
4. Never claim a test passed without executing it.
5. Never treat another AI's completion report as proof.
6. Mark missing information as **NOT VERIFIED**.
7. Report contradictions instead of silently resolving them.
8. Keep the distinction between **proposed, implemented, tested and verified**.

**Definition of verified.** "Verified" means the relevant tests were executed with inspectable raw results **and** the owner has checked the outcome. An AI review is a second opinion, not verification.

Evidence must be inspectable by the owner: raw command output, commit hashes, file paths, test logs, staging inspection. A prose summary is not evidence.

### 7A. Reproducible Evidence

Test evidence states:

1. The exact command executed.
2. The commit or working-tree state.
3. The execution environment.
4. The exit code.
5. The actual results.
6. Any skipped tests.

The owner may re-run any reported command. Any difference in results must be disclosed and investigated; the original results must remain traceable to the recorded execution environment and working-tree state.

## 8. Disagreement

1. When ChatGPT and Claude disagree, each explains its reasoning and evidence, and each considers the other's recommendation.
2. If needed, inspect the actual repository and staging environment.
3. Unresolved decisions go to the owner as a short list: "A says X, B says Y, repository shows Z."
4. Neither adviser may settle a disagreement by changing the implementation.

### 8A. Duty to Dissent

No adviser may withhold a risk, error or correction to avoid disagreement or to stay consistent with its earlier advice. Corrections to one's own earlier advice must be stated plainly.

## 9. Product Delivery Priority

1. Functional completeness.
2. Correct business workflows.
3. Tenant isolation and security.
4. Correct RBAC enforcement.
5. Reliable APIs and integrations.
6. Usable interfaces.
7. Automated testing.
8. Maintainability and scalability.

Avoid engineering work that delays delivery without a demonstrated product benefit.

### 9A. Security Is Not Deferrable

The priority list is a tie-breaker for trade-offs, not a build order. It never authorizes postponing security, tenant isolation, RBAC enforcement or essential data integrity for the functionality being delivered. These are mandatory acceptance conditions for that functionality and for releases.

## 10. Execution Reporting

After each task the workhorse reports:

1. Task ID, task mode and approved scope.
2. Actual files changed.
3. Database changes (only if authorized).
4. Tests actually executed (with the evidence in Section 7A).
5. Raw results or inspectable evidence.
6. Acceptance criteria satisfied.
7. Outstanding defects.
8. Unverified items.
9. Any deviation from the approved scope.
10. Documents read before the task and documents updated after it (Section 3.3), with the diff.

No fabricated completion claims. Work that cannot be shown is reported as unverified.

## 11. Change Control

This agreement may not be changed by ChatGPT, Claude or the workhorse. Any proposed change must:

1. Be explained.
2. Be reviewed by ChatGPT and Claude.
3. Be submitted to the owner.
4. Receive explicit owner approval before adoption.

The same applies to any request to cross an established boundary. Approved changes are recorded in the Change Log and must be visible in the git diff.

## 12. Final Principle

One product. One agreed direction. Multiple advisers. One controlled execution process.

No competition between AI agents. No unauthorized scope expansion. No invented progress. No crossing established boundaries without explicit owner approval.

## 13. Current Workhorse Assignment (owner-maintained, per task)

The workhorse stays as assigned until the owner changes it. Each change is recorded here and follows Section 3.1. The working branch is recorded per task.

| Task / period | Workhorse | Working branch | Task mode |
|---|---|---|---|
| Audit | Cursor |feature/java-backend-integration | implementation |

Advisers (no code changes): ChatGPT (Luna), Claude.
Other tools in use and their purpose: ______

---

## Adoption Record

| Item | Entry |
|---|---|
| Draft prepared by | Claude (v1.0, revised to v1.1) |
| Reviewed by ChatGPT (Luna) | v1.0 reviewed; six corrections proposed. Review of Sections 3.2, 3.3 and the 5.1 clarification: ______ |
| Approved by product owner | Niranjan |
| Date adopted | 14-11-2026 |
| Adopted version | 2.1 |

## Change Log

| Version | Date | Change | Approved by |
|---|---|---|---|
| 1.0 draft | ______ | Initial draft | superseded |
| 1.1 | ______ | Adds 3.1 workhorse independence; replaces 4A; makes 5.1 NOT VERIFIED; adds 6A task mode, 7A reproducible evidence, 9A security not deferrable; 13 per-task assignment | superseded by 2.0 |
| 2.0 FINAL | 14-11-2026 | Final adoption; document filename aligned to docs/AGREEMENT_V2.0.md; includes approved application/data flow and mandatory exception handling, documentation and logging-service requirements under Section 3.2 | Niranjan |
| 2.1 FINAL | 14-11-2026 | Filename changed to docs/AGREEMENT_V2.1.md (header, Sections 3 and 6); adds Section 3.3 Document Register and post-task update rules and document report item 10 in Section 10; renames the next-task file to the tool-neutral NEXT_TASK.md; adds the Section 3.2A/5.1 clarification | Niranjan |
