# IADM-01 Slice S3 Report — Lifecycle

**Date:** 2026-11-22  
**Owner authorization:** Niranjan V.G. — IADM-01 S0–S4 DEV; continue S2–S4 without stop  
**DEV:** `azulbaxszsqxymqmlwfe`

---

## Results

| Story | Result | Evidence |
|---|---|---|
| US-010 request | **PASS** | POST lifecycle-requests → **201** PENDING (SUSPEND/ARCHIVE/FULL_EXPORT) |
| US-011 decide | **PASS** | Self-decision **403** `IADM01_SELF_DECISION`; approve/reject with mandatory note |
| US-021 suspend/reactivate/archive | **PASS** | Approve SUSPEND → institute **SUSPENDED**; reactivate → **ACTIVE**; direct archive throwaway `S3A184154` → **ARCHIVED** |
| US-022 college lifecycle | **PASS** | College SUSPEND request approved; `ce6c8265-…` set **SUSPENDED** via `plat.iadm01_set_college_status` |
| US-023 full export workflow | **PASS** | Approve → **APPROVED_AWAITING_EXECUTION** + deferred message (no export execution) |
| Negative | **PASS** | Student create request **403** |

### Key IDs

| Item | Id |
|---|---|
| Institute under test | `61e9b962-caff-4158-9cc7-dfdd84139f11` (restored ACTIVE) |
| Export request | `6504ce00-…` → APPROVED_AWAITING_EXECUTION |
| Suspend request | `96128b52-…` → APPROVED |
| College suspend | `b1409876-…` → APPROVED |
| Rejected archive | `6c5174bf-…` → REJECTED |
| Archive probe | `1c13a081-…` ARCHIVED |

---

## Screens / BFF

- `/platform/lifecycle` inbox + `/api/platform/lifecycle` / decide  
- `/institution/lifecycle` request form + `/api/institution/lifecycle-requests`  
- `/api/platform/institutes/[id]/lifecycle-requests`, `/archive`

---

## Notes

- Session fail-closed uses `plat.is_tenant_suspended` (status=`SUSPENDED`). `plat.tenant_session_blocks` is absent after rollback — FINDING-IADM01-SESS-01.  
- Platform-created requests must be decided by a different principal (self-decision guard). Demo reassigned `requested_by` to R03 for decide proofs.

## Checks

- `tsc --noEmit` → 0  
- IADM-01 / Gateway health UP  

Continue to Slice S4 (no stop).
