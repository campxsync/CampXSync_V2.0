# Next Task

**STATUS: EN-002 LIVE STOP (2026-11-16).** Service-role JWT in `.env.audit` targets Dev correctly but signature is truncated — Storage API returns `AccessDenied` / failed signature decode.

## Immediate owner action

1. Re-copy the **full** `service_role` secret from Dev Dashboard → Settings → API for project `azulbaxszsqxymqmlwfe` into gitignored `.env.audit` as `CAMPX_SUPABASE_SERVICE_ROLE_KEY=...` (single line, no spaces/newlines).
2. Do **not** paste the key into chat.
3. Reply `CONTINUE EN-002-STORAGE-LIVE` again.

Evidence (no secrets): `docs/reports/STM06_EN002_STORAGE_LIVE_SMOKE_2026-11-16.json`  
Diagnosis: `claim_role=service_role`, `claim_ref=azulbaxszsqxymqmlwfe`, but `sig_len=61` (typical HS256 ≈43) → truncated paste.

---

**Prior STATUS note retained below for queue context.**

**STATUS: QUEUE RUN STOP (2026-11-16).** Batch size 4 — **0 eligible** implementable stories. All remaining queue items DONE, BLOCKED, DEFERRED, or partial with unmet Needs.

## Preflight (this run)

- Branch: `feature/java-backend-integration` @ `6ab99c9`
- `git status`: **known leftovers only** — `classpath.txt` (root + service copies), `docs/reports/_csv_status_*.txt`, CSV scratch (`*.tmp`, `tmpcse61qy9.csv`). No unexpected tracked diffs.
- Owner decisions read from `PROGRESS.md` (D1–D12e).
- Modules touched this run: **none** (no story selected) → no new full-suite required.

## Selection walk (queue order)

| # | Item | Decision | Reason |
|---|---|---|---|
| 0 | IAM Path C Phase 2 | DONE | `268c036` + RLS join `5406419` |
| 0b | R31 pilot activation | DONE (activation) / residual in EN-002 | Auth/IAM/Path C + stack smoke done (`5406419`, smoke evidence) |
| 1 | STM-06-EN-002 live path | **BLOCKED** | `CAMPX_SUPABASE_SERVICE_ROLE_KEY` EMPTY in `.env.audit` / process env. Bucket+deny policies already applied (`6ab99c9`). Needs key + `CONTINUE EN-002-STORAGE-LIVE`. |
| 1b | Frontend contentBase64 | **BLOCKED** | Depends on EN-002 live storage path / product wiring decision. |
| 1c | Frontend Registrar mapping | **BLOCKED** | Pilot user exists; UI role mapping not authorized as next implementable story without product pickup. |
| 1d | R31 Phase B seed | superseded | Replaced by 0b |
| 1e | R31-SoD | DONE | `dfc6788` |
| 2 | STM-CROSS-EN-010 e2e | **BLOCKED** | Multi-service e2e/penetration harness awaiting owner approval. |
| 3–5 | US-010 / US-007 / US-006 | DONE | prior commits |
| 6 | STM-06-US-015 | **BLOCKED** | Notification DDL / DB notifications require owner-approved DDL. |
| 7 | STM-CROSS-EN-014 / STM-03-EN-007 | **BLOCKED** | Scheduled / operational readiness hold. |

**Batch selected:** none (same-service batch of up to 4 impossible — zero unblocked items).  
**Parallel subagents:** not used (nothing eligible; unsafe/empty).

## Immediate owner actions

1. Set `CAMPX_SUPABASE_SERVICE_ROLE_KEY` in gitignored `.env.audit` (Dev Dashboard → Settings → API). **Do not paste into chat.**
2. Reply `CONTINUE EN-002-STORAGE-LIVE` to finish EN-002 live put/get + document-service restart smoke.
3. Authorize next pickup (frontend 1b/1c, US-015 DDL, or CROSS e2e) explicitly if those should leave BLOCKED.

## Rules

- No push unless owner authorizes.
- No production access.
- No unapproved SQL/Auth/Storage writes.
- Never print service-role keys.
