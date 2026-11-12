# CampXSync Story Workflow & Definition of Done

Reference: `docs/project-control/STORY_WORKFLOW.md`

## Standing Ground Rules
- Dev Supabase `azulbaxszsqxymqmlwfe` only. Never production or staging.
- No new branches, no push, no commit unless explicitly instructed.
- No DDL/migrations without explicit user approval.
- Real data only: UI -> API Gateway -> Java service -> Supabase (and Intelligence event emissions where required). No mock or in-memory data in story paths.
- Never print passwords in reports.

## Execution Cycle
1. PLAN (short).
2. IMPLEMENT single story.
3. AUTOMATED CHECKS: `tsc`, `lint`, `next build`, unit and integration tests.
4. START THE STACK via `scripts/dev/start_stack.ps1`.
5. VISIBLE DEMO: Headed browser, slowMo ~400ms, 1280x720 video, numbered screenshots, caption overlays, element highlights.
6. LEAVE RUNNING + TRY IT YOURSELF: Open final page and demo folder.
7. UPDATE DOCUMENTATION: Traceability, API contracts, DDD deltas, catalogs.
8. STOP and report with the one-page REPORT TEMPLATE.

## Definition of Done Verification
- All services log through designed logger module (`campx-logger` / Logging Service).
- Unified RFC 7807 `problem+json` error contract across Gateway, Java microservices, and Next.js BFF routes.
- Trace ID propagation end-to-end (`X-Trace-Id` / `X-Correlation-Id`).
- Comprehensive error coverage (400/422, 401, 403, 404, 409, 503, 504, 500).
- DoD evidence table included in final report.

