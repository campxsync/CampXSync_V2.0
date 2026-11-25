# IADM-06 headed demo (US-028)

DEMO ONLY suppression thresholds: **low=1** (show figures), **raised=50** (show suppression). Not a privacy standard.

| Step | Result | Detail |
|---|---|---|
| IADM-06 health :8104 | PASS | status=200 |
| Pre-clear DEMO outage flags | PASS | status=200 |
| R01 platform overview shows seeded figures (threshold=1) | PASS | metrics=6 |
| R01 metric catalogue M01–M10 | PASS | rows=10 |
| R01 compare institutes | PASS |  |
| R01 entitlement insight | PASS |  |
| R01 trend series | PASS |  |
| R01 raised threshold shows suppression (threshold=50) | PASS | withheld=24 |
| Fault: unresolved policy withholds dataset | PASS | DEMO ONLY outage flags: {"auditDisabled":false,"iadm01Disabled":false,"iadm04Disabled":false,"iadm05Disabled":true,"exportFailOnce":false,"label":"DEMO ONLY"} |
| Fault: source stopped shows unavailable | PASS | DEMO valuesPolicy: DEMO-ANALYTICS-HANDLING v2Refresh (query time): 2026-11-25T13:16:07.777ZM01Status: UNAVAILABLEAnalytics source is temporarily unavailableM02S |
| Fault: audit unavailable refuses action | PASS | Action could not be recorded; please retry. |
| Fault: failed export appears | PASS | failedRows=2 |
| Fault: failed export retry succeeds without duplicate completed id | PASS | completed=9 |
| R02 platform aggregates visible | PASS | metrics=6 |
| R02 export platform aggregates | PASS |  completedRows=3 |
| R03 institute A own analytics | PASS | scope=Scope: d4000000-0000-4000-8000-000000000004 metrics=10 |
| R03 institute A export own aggregates | PASS | Export COMPLETED: e06dc986-dc6f-4354-8bce-f7021b382738 |
| R03 institute B own analytics (no institute A data) | PASS | mode=magic scope=Scope: 72ecbfe3-2077-41b5-8a20-b6d3e045311b metrics=10 |
| R04 College Admin denied | PASS | url=http://127.0.0.1:3000/platform/analytics |
| R28 read-only; export denied | PASS | 403 Auditor cannot export or change analytics settings |

Totals: 20 PASS / 0 FAIL

Evidence directory: `D:\CampXSync\CampXSync_V2.0\docs\iadm\iadm-06\evidence\us028`