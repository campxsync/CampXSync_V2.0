# CampXSync Antigravity Persistent Rules

Read these files before acting: root `AGENTS.md`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, `docs/project-control/PROGRESS.md`, `prompts/NEXT_ANTIGRAVITY_TASK.md`, and the latest relevant report.

## Wave discipline
- Follow the documented wave order and work on one bounded task at a time.
- Do not restart completed work or repeat broad audits without a concrete reason.
- After every wave, create a completion report, update the progress checkpoint, analyze actual evidence, and prepare the next prompt.
- Clearly distinguish observed evidence, agent-reported claims, assumptions, and unverified behavior.

## Mandatory architecture
- Test the relevant UI → shared/versioned API Gateway → Java domain services → Supabase/PostgreSQL path, including committed read-back and tenant isolation.
- Relevant Java domain-service outputs must be shared with the CampXSync Intelligence Layer through documented, versioned contracts.
- Every wave report must document persistence-path evidence and Intelligence Layer contract/delivery evidence.
- Preserve tenant context, least privilege, data minimization, correlation, idempotency, retries, and failure visibility where applicable.
- Reuse existing Shared Services/audit-outbox mechanisms when appropriate.
- Intelligence Layer integration must not bypass authentication, authorization, Java business rules, tenant isolation, or PostgreSQL as the transactional source of truth.
- If the real Intelligence Layer endpoint is unavailable, test the contract with a controlled adapter and mark live delivery as pending.

## Safety
- Preserve branch, local edits, and untracked files. No reset, branch switch, stash, commit, push, cleanup, or overwrite without explicit authorization.
- Never read root `.env.local` or `frontend/.env.local`.
- Never connect to production ref `ekspahbsbwdpraaqfqui` or previous staging ref `kpwdvvcschankgggitnj`.
- Development ref `azulbaxszsqxymqmlwfe` may be used only when necessary and authorized.
- No DB writes, migrations, schema/role/grant/RLS changes without explicit approval for that exact action.
- Never silently use in-memory/mock identity in production-like execution.
- Stop and report if a safe task requires an approval-gated operation.
