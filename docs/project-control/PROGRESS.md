# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**HEAD:** see `git rev-parse HEAD` after the STM-03-US-003 commit.  
**Updated:** 2026-11-14 (`STM-03-US-003` completed + COMMIT per owner directive)

## 1. Branch and HEAD

- Working branch: `feature/java-backend-integration`
- `STM-03-US-001` … `STM-03-US-003` owner-approved and committed locally (push only if separately instructed).

## 2. Approved application and data flow

**UI → API Gateway → Java Services → Supabase, with JSON output to Intelligence Services.**

## 3. Scoreboard by Service ID

From CSV `Implementation status`. Totals after owner-approved `STM-03-US-003` → **completed**:

**Totals: 3 completed / 103 partially completed / 114 yet to pickup = 220.**

Remaining audit-derived rows (except approved stories) are **NOT owner-verified**.

| Service ID | completed | partially completed | yet to pickup |
|---|---:|---:|---:|
| STM-03 | 3 | 22 | 2 |
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
| **TOTAL** | **3** | **103** | **114** |

### Approved this cycle

| Issue ID | Evidence |
|---|---|
| STM-03-US-001 | Unit; tsc; headed demo 11/11 — `docs/demos/STM-03-US-001/` |
| STM-03-US-002 | Unit 39/39; tsc; headed demo 10/10 — `docs/demos/STM-03-US-002/` |
| STM-03-US-003 | Unit 48/48 (9 US-003); tsc; headed demo 14/14; DB ELIGIBLE read-back — `docs/demos/STM-03-US-003/` |

## 4. Caveats

- **partially completed** means code exists but gaps are confirmed only when a story is picked up.
- Final User Stories CSV / legacy demo IDs do **NOT** map 1:1 to this backlog.
- **STM-06-US-002** remains partially completed by owner override.
- Prefer **partially completed** stories before **yet to pickup**.

## 5. Owner policy decisions open (do not implement)

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
