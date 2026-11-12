# STANDING WORKFLOW & DEFINITION OF DONE

This workflow applies to every story until explicitly changed by the user.

## Ground Rules
- Dev Supabase `azulbaxszsqxymqmlwfe` only (never production `ekspahbsbwdpraaqfqui` or legacy staging `kpwdvvcschankgggitnj`).
- No new branches, no push, no commit unless explicitly instructed.
- No DDL/migrations without explicit user approval.
- Real data only: every story's path must be UI -> API Gateway -> Java service -> Supabase (and JSON delivered to the Intelligence services where the story requires it), with no dummy pages and no mock or in-memory data in the path. Confirm the services you touch bind the Postgres repository.
- Use the same test accounts as before (from `.env.audit`/existing env; never print passwords).

## Order of Work
- **Part 1**: Foundation retrofit (once).
- **Part 2**: Finish the in-progress story (Story 1) under the Definition of Done.
- Then STOP for user approval (one gate covers both parts). After that, one story at a time.

## The Cycle for Every Story
1. **PLAN (short)**: story ID, acceptance criteria, personas/accounts, routes/services touched, how you will demo it.
2. **IMPLEMENT** only this story.
3. **AUTOMATED CHECKS**: `tsc`, `lint`, `next build` and the relevant unit/integration tests. Fix failures.
4. **START THE STACK**: via `scripts/dev/start_stack.ps1`. User must never start services manually.
5. **VISIBLE DEMO**: HEADED browser (visible window, slowMo about 400 ms), recorded (webm, 1280x720), numbered screenshots, caption overlays with PASS/FAIL, element highlights.
6. **LEAVE EVERYTHING RUNNING** and give a TRY IT YOURSELF section (URL, account, 4-6 click steps). Open final page in default browser and demo folder in Explorer.
7. **UPDATE DOCUMENTATION** for this story (see Definition of Done C).
8. **STOP and report** using the REPORT TEMPLATE. Do not start the next story; you may only name it with a 3-line plan.
   - User replies "APPROVED <ID>": mark Done in docs, start next story.
   - User replies "APPROVED <ID> COMMIT": also commit only this story's files (message "<ID>: <title>"), no push.
   - User replies "CHANGES <ID>: ...": fix, repeat steps 3-8 for the same story.

## Definition of Done (DoD)
A story is not READY FOR REVIEW unless every line passes, with evidence.

### A. Logging (per the existing design)
1. All services (Java and Next.js server side) log through the designed logger/Logging Service. No `System.out`/`printStackTrace`/`console.log` in production paths.
2. Structured logs: service, level, timestamp, trace/correlation id, tenant id, user id (id only), route, status, duration.
3. One trace id flows Next.js -> gateway -> Java service -> DB access, appears in every log line of the request, and is returned to the client (header and error bodies).
4. Log business events (create/update/approve/reject, state changes) and security events (auth failure, permission denied, RLS denial) as the design specifies, including audit/outbox events where required.
5. Never log passwords, tokens, JWTs, secrets or personal data beyond what the design allows (redaction). Correct levels.

### B. Exception Handling
1. One error contract across gateway, Java services and Next.js API routes (RFC 7807 `problem+json` as the gateway uses: `status`, `code`, `title`, `message`, `traceId`, `path`) with stable error codes in a catalog.
2. Global exception handler in each Java service; shared `apiError()` helper plus `error.tsx` / `not-found` / `loading` boundaries in Next.js.
3. Mapping: validation 400/422, unauthenticated 401, forbidden 403, not found 404, conflict/unique violation 409, downstream unavailable 503, timeout 504, unexpected 500 with a generic message. Never return stack traces or SQL to the client.
4. No swallowed exceptions: no empty catch, no log-and-return-success, no silent fallbacks. Any fallback shows a visible banner and logs a warning.
5. The UI shows a clear message with the reference (trace) id for every failure, and per-field validation messages on forms.
6. Transactions roll back on failure; retries only for idempotent calls.
7. Tests cover error paths (validation, permission denied, not found, conflict, downstream down).

### C. Documentation
1. `STM_IMPLEMENTATION_TRACEABILITY.csv`, plan and `PROGRESS.md` status.
2. API contract for every new/changed endpoint: request, response, error codes, required role/permission, tenant rules, example JSON.
3. Design delta: update the relevant DDD if behaviour or data model differs; list deviations.
4. Log-event catalog and error-code catalog entries.
5. Javadoc/TSDoc on public classes/functions; README/run notes for any new service or module (config names only, ports, how to start).
6. Test evidence and the demo pack.

### D. DoD Table
The report includes a DoD table: item | PASS/FAIL | evidence. Missing evidence = not done.

## Demo Requirements
- HEADED browser (visible window, `slowMo` about 400 ms), not headless, no hidden scripts. Record video (webm, 1280x720) and take a numbered screenshot per step.
- On-screen caption overlay per step: step number, what is being done, PASS/FAIL with expected vs actual. Highlight the clicked element. Understandable without narration.
- Cover the happy path, validation errors, permission-denied path (RBAC matrix), persistence (reload and confirm; also confirm with a read-only DB query), and the other persona seeing the result where relevant.
- Foundation demo: trigger (a) validation error, (b) permission denied, (c) a downstream service stopped, (d) an unexpected failure; show the UI message with its reference id, then show the matching structured log lines found by that trace id across gateway and service.
- Show measured page-load times for the story pages (target <= 1.5 s warm).
- Save to `docs/demos/<ID>/`: `demo.webm`, `screenshots/NN-description.png`, `DEMO.md` (acceptance table with PASS/FAIL and evidence, accounts used, records created tagged `DEMO-<ID>`, timings).
- If a step fails, fix and re-run before showing user. Never mark PASS without evidence.

## Stack Handling
- Scripts: `scripts/dev/start_stack.ps1`, `stop_stack.ps1`, `check_stack.ps1` and root `START_CAMPXSYNC.bat`.
- Start only the services needed for current stories, each in its own minimized window, with sensible `-Xmx` values; wait for health checks; print URLs and accounts.
- Check free RAM first (if < 3 GB, stop and tell user what to close).
- Leave stack running after every story.
- Fast single-service restart: `scripts/dev/restart_service.ps1 <service>` (never restart whole stack for single-service change).

---

## SPEED-UP DIRECTIVE (Target: 30-45 Minutes Per Story)

### One-Time Tooling & Rules
1. **DB Schema Snapshot:** Consult `docs/engineering/DB_SCHEMA_SNAPSHOT.md` BEFORE writing any SQL or repository queries. Re-generate via `node scripts/dev/dump_schema.mjs` only when migrations change. Never guess database schema by trial-and-error against live PostgreSQL.
2. **Generic Demo Harness:** Use `scripts/demo/run_story_demo.mjs <STORY-ID>` reading `docs/stories/<STORY-ID>.demo.json`. Avoid writing custom Playwright scripts for every story.
3. **Stable Locators:** Add `data-testid="<module>-<element>"` (e.g. `data-testid="docs-upload-submit"`) to interactive elements. Do not debug CSS/DOM selectors.
4. **Test-Data Policy:** Never DELETE from tables with append-only triggers. For demos use tagged records (`DEMO-<ID>-<timestamp>`) and query with `scripts/dev/list_demo_data.sql`.
5. **Fast Restart:** Use `scripts/dev/restart_service.ps1 <service>` to compile and restart only the affected module.
6. **Documentation Templates:** Use templates in `docs/templates/` (`API_CONTRACT.md`, `DEMO.md`, `LOG_EVENTS.md`, `ERROR_CODES.md`, `TRACEABILITY_ROW.md`). Do not rewrite whole documents.

### Per-Story Rules to Avoid Repeated Work
- **Reuse first:** Search existing stories, classes, routes, or components and copy pattern. Do not re-audit covered areas.
- **Targeted edits only:** Avoid whole-file rewrites. If a file changes > 30%, explain why in the report.
- **Build and test only what changed:** Compile the affected Maven module and test affected routes. Run full regression suite once every 5 stories.
- **Demo scope:** New acceptance criteria, negative/RBAC cases, persistence, and 1 regression smoke check. Do not re-demo earlier stories in full.
- **Time-box on errors:** If the same error persists after 3 attempts or 15 minutes, stop, summarize attempts, and ask or pivot. Do not loop.

---

## Report Template (One Page)
1. **Story ID/title and status READY FOR REVIEW.**
2. **TIME & REUSE**: Minutes per phase (plan / implement / checks / demo / docs), what was reused from earlier stories/templates, and anything repeated or slow with the reason.
3. **Acceptance criteria table** (criterion | PASS/FAIL | evidence).
4. **Demo pack links.**
5. **TRY IT YOURSELF** (URL, account, 4-6 steps).
6. **DoD table** (A-D).
7. **Files changed** (`git diff --stat`), tests run and results, anything risky or not done.
8. **Docs updated.**
9. **Next story proposal** (3 lines). Waiting for: APPROVED <ID> / APPROVED <ID> COMMIT / CHANGES <ID>: ...


