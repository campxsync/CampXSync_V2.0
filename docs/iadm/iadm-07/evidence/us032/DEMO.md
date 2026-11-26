# IADM-07 headed demo (US-032)

| Step | Result | Detail |
|---|---|---|
| IADM-07 health :8105 | PASS | status=200 |
| R01 seeded SEC-FIND-001 visible | PASS | count=1 |
| R01 SEC-ALERT-001 listed | PASS |  |
| R01 set stale threshold | PASS | thr=Stale threshold (minutes): 1440 action= |
| R02 platform security desk | PASS |  |
| R03A institute finding SEC-FIND-001 | PASS | scope=Scope: d4000000-0000-4000-8000-000000000004 |
| R03A finding transition | PASS | advance clicked |
| R03B scoped institute (not A) | PASS | mode=magic scope=Scope: 72ecbfe3-2077-41b5-8a20-b6d3e045311b |
| R04 college summary only | PASS |  |
| R28 mutation denied 403 | PASS | 403 Auditor cannot change security registers |

Totals: 10 PASS / 0 FAIL

Evidence directory: `D:\CampXSync\CampXSync_V2.0\docs\iadm\iadm-07\evidence\us032`