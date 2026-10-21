# Antigravity Prompt — CampXSync Wave 1, Task 1.1

Read `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` (Wave 1 section), `docs/project-control/PROGRESS.md`, and `docs/reports/wave-0/COMPLETION_REPORT.md` first. Continue on `feature/java-backend-integration`; do not restart completed work.

## Mandatory architecture rule
This task is part of a platform whose required flow is UI → shared/versioned API Gateway → Java domain services → Supabase/PostgreSQL, and whose relevant Java service outputs must be shared with the CampXSync Intelligence Layer through governed, versioned contracts. 

Wave 1 establishes the durable, tenant-aware Shared Services audit-outbox foundation that all subsequent domain services (Waves 2–9) and Intelligence Layer analytics (Wave 10) rely upon for reliable asynchronous event publication and auditability.

## Mission
Execute **Wave 1, Task 1.1: Shared Audit and Outbox Foundation**.

### 1. Preserve state
- Check current branch and `git status`; record existing changes and untracked files.
- Do not reset, switch branches, stash, commit, push, or overwrite user work.
- Never read root `.env.local` or `frontend/.env.local`.

### 2. Inspect and Design
1. **Audit & Logging Contracts:** Inspect existing common libraries (`logger/`, `platform/`, `campx-logger`), audit schema, and event structures.
2. **Outbox Schema & Outbox Event Model:** Inspect `sys.outbox_events` table definition in migrations (`database/migrations/`). Align Java outbox event entities/DTOs with the schema (aggregate type, aggregate ID, event type, payload, correlation ID, tenant ID, status, retry count, created_at, processed_at).
3. **Transaction-Aware Outbox Writer:** Ensure outbox records are written within the same database transaction as the domain entity state change, guaranteeing at-least-once durable event capture.
4. **Intelligence Layer Contract Alignment:** Define event metadata schema including tenant isolation context, least-privilege event payloads (no credentials or tokens), correlation IDs, idempotency keys, and versioning.
5. **Failure & Retry Mechanics:** Define retry, backoff, and dead-letter handling contracts for outbox emission.

### 3. Validation
- Implement focused unit and contract tests for audit logging, outbox event generation, tenant isolation preservation, and serialization.
- Run `mvn test` on relevant modules. Record exact commands, results, failures, and skipped tests.

### 4. Non-negotiable safety
- No unapproved DB writes, schema changes, or migrations.
- Never connect to production ref `ekspahbsbwdpraaqfqui` or previous staging ref `kpwdvvcschankgggitnj`.
- Stop after reporting Task 1.1 results; do not begin subsequent tasks automatically.
