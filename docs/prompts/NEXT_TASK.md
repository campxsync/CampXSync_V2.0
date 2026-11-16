# Next Task

**STATUS: STM-07 batch (US-001/002/004/010/013) completed 2026-11-16. Batch stop.**

## Just completed (this batch — host: `student-service`)

| ID | Status | Notes |
|---|---|---|
| STM-07-US-001 | DONE | Eligibility on `linkIamAccount` + `GET link-status` + portal strip |
| STM-07-US-002 | DONE | `evaluateProvisioningEligibility` + `GET .../provisioning-eligibility` |
| STM-07-US-004 | DONE | Eligibility-gated `requestAccountProvisioning` |
| STM-07-US-010 | DONE | Lifecycle SUSPENDED/DETAINED/WITHDRAWN revokes IAM link |
| STM-07-US-013 | DONE | Student self-view link-status (API + BFF + dashboard) |

Commit hash: *(set after local commit)*  
student-service suite: **127/0**

## BLOCKED (do not implement)

| ID | Reason |
|---|---|
| STM-07-US-008 | Assign approved student roles — needs IAM role API; no duplicate role system (EN-002); owner question: which existing IAM contract assigns R25? |
| STM-07-US-009 | Revoke student roles — same IAM role API dependency as US-008 |
| STM-07-EN-003 | Tenant role mapping — FLAG-002 / owner infrastructure decision pending |
| STM-08-* | New service gate — `docs/design/STM-08_ACADEMIC_HISTORY_DESIGN_NOTE.md` |
| STM-09-* | New service gate — `docs/design/STM-09_SERVICE_REQUESTS_DESIGN_NOTE.md` |
| STM-06-EN-003 live AV | owner infrastructure decision pending |
| STM-CROSS-EN-007 | DEFERRED (Kafka) |
| FLAG-002 / FLAG-003 / FLAG-007 | owner infrastructure decision pending |

## Pickup queue (next batch when authorized)

1. STM-07-EN-002 (IAM integration hardening in student-service) then remaining unblocked STM-07 P0/P1
2. STM-10-EN-001 preferences (institute-admin path) if independent and unblocked
3. Unblock STM-08/09 only after owner approves new services

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
