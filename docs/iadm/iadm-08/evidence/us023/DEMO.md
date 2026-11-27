# IADM-08 US-023 Headed DEMO (Part B Extended)

| Check | Result | Detail |
|---|---|---|
| R01 platform operations desk | PASS |  |
| R01 health overview visible | PASS | panel=1 err= |
| R01 audit search returns rows | PASS | rows=50 |
| R01 SLA not available | PASS | SLA: not available |
| Part B.5: 6 reads return 200 | PASS | statuses=200,200,200,200,200,200 |
| Part B.5: all 6 IADM08_* rows recorded in shared audit | PASS | IADM08_AUDIT_SEARCH=bd6e1766-97bd-4397-8349-b92808ea6659; IADM08_AUDIT_DETAIL=e85101aa-c87a-4de2-a260-ecc26dfbab9a; IADM08_AUDIT_TIMELINE=917082a8-d9e4-4bc9-a81c-4ad07abd4e22; IADM08_HEALTH_OVERVIEW=69ab2e87-feb4-4faf-907b-b56563e11cbf; IADM08_HEALTH_DETAIL=fe2cb8fa-53bc-4fa5-8c86-67fd658b8780; IADM08_ALERTS_READ=bc978e59-e758-44f1-89bf-157a0a2571b4 |
| Part B.5: auditDisabled=true refuses all 6 reads (503 IADM08_AUDIT_UNAVAILABLE) | PASS | 503:IADM08_AUDIT_UNAVAILABLE,503:IADM08_AUDIT_UNAVAILABLE,503:IADM08_AUDIT_UNAVAILABLE,503:IADM08_AUDIT_UNAVAILABLE,503:IADM08_AUDIT_UNAVAILABLE,503:IADM08_AUDIT_UNAVAILABLE |
| Part B.5: auditDisabled=false recovers all 6 reads (200) | PASS | statuses=200,200,200,200,200,200 |
| R02 platform operations desk | PASS |  |
| R03 institute activity desk | PASS |  |
| R03 health view denied | PASS | count=1 |
| Part B.6: R03 sees only own institute; unattributed hidden | PASS | rows=50, unattributedSampleInDb=fa5c6e81-fde9-4455-89ae-7ae6a2b361a2 |
| Part B.6: R03 cross-institute refused (403 IADM08_SCOPE_DENIED) | PASS | status=403 code=IADM08_SCOPE_DENIED |
| Part B.6: R03 unattributed event detail refused/hidden (403/404) | PASS | eventId=fa5c6e81-fde9-4455-89ae-7ae6a2b361a2 status=404 code=IADM08_NOT_FOUND |
| R04 denied IADM-08 platform desk | PASS | http://127.0.0.1:3000/platform/operations |
| R28 operations desk (health read-only) | PASS |  |
| Part B.7: R28 audit search + detail read-only | PASS | rows=50 detail=true |
| Part B.7: R28 alerts read-only (no ack control) | PASS | panel=true ackControls=0 |
| Part B.7: R28 search recorded in shared audit | PASS | count=3 latest=e465b76d-0a63-46be-9480-c9702f3ae072 |
| R28 export denied | PASS | Export attempt: 404 This feature is not available. |
| Part B.7: R28 acknowledge mutation refused (404) | PASS | status=404 code=IADM08_NOT_AVAILABLE |
| Part B.8: stopped DEV service (iadm-06) shows Unavailable | PASS | status=Unavailable |
| Part B.8: silent/delayed target (iadm-06) shows Unknown | PASS | status=Unknown |
| Part B.8: recovered DEV service (iadm-06) shows Healthy | PASS | status=Healthy |
| Part B.8: denial-only audit search (IADM02_*_DENIED or honest empty) | PASS | rows=4 sample=2026-11-24T07:37:38.259234ZIADM02_CONSUMER_READ_DENIED69822238-2ca1-4e3b-86d4-c7 |

**Score:** 25/25