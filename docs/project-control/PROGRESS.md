# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**HEAD:** local tip of this branch — run `git rev-parse HEAD`.  
**Updated:** 2026-11-14 (repository consolidation)

## 1. Branch and HEAD

- Working branch: `feature/java-backend-integration`
- Recorded at consolidation; **not pushed**. Confirm with `git rev-parse HEAD` / `git log --oneline -6`.

## 2. Approved application and data flow

**UI → API Gateway → Java Services → Supabase, with JSON output to Intelligence Services.**

## 3. Scoreboard by Service ID

Computed from the final CSV column `Implementation status` (to be added in the same consolidation series), with values taken from `docs/audit/STM_STATUS_AUDIT_2026-11-14.md` Part D **Proposed status**, plus owner override **STM-06-US-002 → partially completed**.

**Totals: 0 completed / 106 partially completed / 114 yet to pickup = 220.**

These totals come from the audit (with that override) and are **NOT owner-verified**.

| Service ID | completed | partially completed | yet to pickup |
|---|---:|---:|---:|
| STM-03 | 0 | 25 | 2 |
| STM-04 | 0 | 24 | 1 |
| STM-05 | 0 | 17 | 3 |
| STM-06 | 0 | 15 | 9 |
| STM-07 | 0 | 0 | 20 |
| STM-08 | 0 | 0 | 25 |
| STM-09 | 0 | 0 | 29 |
| STM-10 | 0 | 12 | 18 |
| STM-CROSS | 0 | 12 | 2 |
| All | 0 | 1 | 2 |
| STM-04, 06, 07, 09 | 0 | 0 | 1 |
| STM-05 / STM-06 | 0 | 0 | 1 |
| STM-04, 07, 08, 09 | 0 | 0 | 1 |
| **TOTAL** | **0** | **106** | **114** |


## 4. Caveats

- **partially completed** means code exists but the audit could not name every acceptance gap; a story's real status is confirmed when it is picked up.
- The 169-row Final User Stories CSV and the legacy `STM03-US-*` / demo numbering do **NOT** map to this backlog (the same Issue ID can be a different story).
- Offline unit tests only in the audit evidence; no live-DB or Playwright runs for the audit redo.
- **STM-06-US-002** has a one-line audit trace only (and is recorded as partially completed by owner override, not completed).

## 5. Owner policy decisions open (do not implement)

Rows whose `RBAC Alignment Verdict` is `POLICY DECISION REQUIRED` (`STM-RBAC-FLAG-*`):

| Issue ID | Summary |
|---|---|
| STM-RBAC-FLAG-001 | Roles used by the STM matrix are not in R01 to R30 |
| STM-RBAC-FLAG-002 | No configure permission in the matrix |
| STM-RBAC-FLAG-003 | Student consent and self-granting |
| STM-RBAC-FLAG-004 | Advisor approval removed |
| STM-RBAC-FLAG-005 | Update, delete and retention rights |
| STM-RBAC-FLAG-006 | Auditor beyond STM-06 |
| STM-RBAC-FLAG-007 | Open policy items from the matrix |


## 6. Canonical files

| Role | Path |
|---|---|
| Backlog | `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` |
| Next task | `docs/prompts/NEXT_TASK.md` |
| Completion reports | `docs/reports/COMPLETION_REPORT.md` |
| Archive (never a source) | `docs/archive/` |

Do not use `docs/archive/` for status, approvals or next-task instructions.

