# IADM-05 US-032 headed evidence

Generated: 2026-11-25T10:14:16.952Z

| Step | Result | Detail |
|---|---|---|
| IADM-05 health :8103 | PASS | status=200 |
| R02 desk shows seeded classifications | PASS | rows=4 |
| R02 propose classification | PASS | OK: classifications |
| R02 submit classification for review | PASS | OK: submit-review |
| R02 approve denied | PASS | status=403 FORBIDDEN |
| R02 submit export request | PASS | OK: exports |
| R01 desk seeded rows | PASS |  |
| R01 export/erasure/sharing queue seeded | PASS | rows=10 |
| R01 approve R02 proposal (SoD) | PASS | OK: decide |
| R01 publish classification | PASS | OK: publish |
| R01 privacy publish blocked (legal gate) | PASS | status=423 IADM05_LEGAL_GATE |
| R01 approve export/request from desk | PASS | clicked approve |
| R01 close erasure (technical rule) | PASS | Closure blocked: one or more required domains are unresolved (technical rule only; legal closure criteria TBC â€” G-05). |
| R03 adherence shows seeded published policies | PASS | Applicable published policies
DEMO-CLASS-H389294 · CLASSIFICATION · v1 · DEMO ONLY
DEMO-CLASS-H604598 · CLASSIFICATION · |
| R03 cannot see other-institute scoped rows | PASS | isolated |
| R03 write returns 403 | PASS | status=403 |
| R04 college view shows policy + entry points | PASS |  |
| R04 write returns 403 | PASS | status=403 |
| R28 evidence desk shows seeded refs | PASS |  |
| R28 write/approve returns 403 | PASS | status=403 |
| R28 BFF approve returns 403 | PASS | status=403 |

**Summary:** 21/21 PASS
