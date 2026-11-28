# IADM-07 headed demo (US-032)

Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03)×2 institutes, College Admin (R04), Auditor (R28).
Tabs filmed: Signals, Controls, Exceptions, Enforcement, Reports (+ Findings/Alerts/Posture/Standards).
Faults filmed: audit off (+ recovery), stale register, notifications off, invalid transition, self-approval.

| Step | Result | Detail |
|---|---|---|
| IADM-07 health :8105 | PASS | status=200 |
| R01 seeded SEC-FIND-001 visible | PASS | count=1 |
| R01 SEC-ALERT-001 listed | PASS |  |
| R01 Signals tab + denial events panel | PASS |  |
| R01 Controls tab | PASS |  |
| R01 Exceptions tab | PASS |  |
| R01 Enforcement tab | PASS |  |
| R01 Reports tab (counts/trends/freshness) | PASS | null |
| R01 no export control | PASS |  |
| Fault: audit off refuses mutation | PASS | The security governance change cannot be confirmed because audit is unavailable. |
| Fault: audit recovery allows mutation | PASS | {"status":200,"code":"SEC-STD-RECOV-bl16","detail":"DRAFT"} |
| Fault: stale register shows STALE never healthy | PASS | thr=Stale threshold (minutes): 1440 |
| Fault: notifications off alerts still work | PASS |  |
| Fault: invalid transition refused | PASS | {"ok":true,"status":400,"detail":"Transition RESOLVED â†’ OPEN is not allowed for FINDING","from":"RESOLVED","attempted":"OPEN"} |
| Fault: self-approval refused | PASS | {"ok":true,"status":403,"detail":"Decider must differ from requester","id":"7af620b7-5518-4636-91bc-3673e5c11134"} |
| R02 platform security desk | PASS |  |
| R02 Signals tab | PASS |  |
| R02 Reports tab | PASS |  |
| R03A institute finding SEC-FIND-001 | PASS | scope=Scope: d4000000-0000-4000-8000-000000000004 |
| R03A finding transition Assigned→Remediation/Pending | PASS | already advanced / control not shown |
| R03A acknowledge alert | PASS | no OPEN alert or already acknowledged |
| R03B scoped institute (not A) | PASS | mode=magic scope=Scope: 72ecbfe3-2077-41b5-8a20-b6d3e045311b |
| R04 college summary only | PASS |  |
| R28 mutation denied 403 | PASS | 403 Auditor cannot change security registers |
| API proof companion (a–h) | PASS | exit=0 |

Totals: 25 PASS / 0 FAIL

Evidence directory: `D:\CampXSync\CampXSync_V2.0\docs\iadm\iadm-07\evidence\us032`

Also see: `RLS_WRITE_TENANT_GUARD.md`, `API_PROOF.md`.