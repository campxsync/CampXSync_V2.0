# IADM-06-US-027 — Failure, isolation and role suite

**Module:** IADM-06 Platform Analytics  
**Date:** 2026-11-25  
**Mode:** offline unit proofs + live headed fault film

## Automated unit suite

Command: `mvn -pl services/administration-services/iadm-06-platform-analytics-service test`  
Result: **18 tests, 0 failures** (`AnalyticsFailureTableTest` 9, `AnalyticsPermissionMatrixTest` 6, `SuppressionEngineTest` 3).

| Failure-table row | Covered by | Result |
|---|---|---|
| No permitted data / empty groups | `AnalyticsFailureTableTest` | PASS |
| Small-group suppression + subtraction guard | `SuppressionEngineTest` | PASS |
| Source unavailable flags (IADM-01/04/05) | `AnalyticsFailureTableTest` | PASS |
| Audit unavailable fail-closed | `AnalyticsFailureTableTest` + DurableAuditWriter | PASS |
| Role denials (R04 / non-holders) | `AnalyticsPermissionMatrixTest` | PASS |
| Threshold DEMO ONLY semantics | unit + headed | PASS |

## Live headed faults (US-009 / US-028)

Evidence: `docs/iadm/iadm-06/evidence/us028/DEMO.md` (20/20 PASS)

| Fault | How simulated | Observed |
|---|---|---|
| Unresolved policy | DEMO ONLY `iadm05Disabled` | Dataset withheld — “Data policy could not be verified” |
| Source stopped | DEMO ONLY `iadm01Disabled` | Metrics UNAVAILABLE / temporarily unavailable |
| Audit unavailable | DEMO ONLY `auditDisabled` | Action refused — “Action could not be recorded; please retry.” |
| Failed export retry | DEMO ONLY `exportFailOnce` | FAILED row then retry → COMPLETED; completed export not duplicated |

## Isolation

| Check | Evidence |
|---|---|
| R03 institute A vs B | Headed: scopes `d4000000-…0004` vs `72ecbfe3-…311b`; no foreign institute data |
| R04 denied | Headed + BFF 403 |
| R28 export denied | Headed 403 |
| Additive IADM-01 R03 cannot broaden tenant | `docs/iadm/iadm-06/evidence/prefilm/ADDITIVE_ENDPOINTS.json` |
