# IADM-05 S2 + S3 Report — Requests, domain contract, erasure/sharing, privacy gate

**Date:** 2026-11-25  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Commits prior:** S0 `d3c2e74`, S1 `854d8f1`

## S2 stories

| Story ID | Plain name | Status |
|---|---|---|
| IADM-05-US-027 | Governance request state machine and idempotency | API-ready |
| IADM-05-US-028 | Domain contract endpoints | API-ready — `/domain/resolve-policy`, `/requests/{id}/outcomes` |
| IADM-05-US-029 | Domain execution simulator (DEMO ONLY) | Demo-ready — labelled; disable flag |
| IADM-05-US-005 | Submit governed export request | Screen-ready |
| IADM-05-US-006 | Approve/reject export request | Screen-ready — SoD reviewer≠requester |
| IADM-05-US-017 | Resolve policy + report outcome | API-ready (simulator exercises) |
| IADM-05-US-014 | Identify failed/partial domain execution | Screen-ready — unresolved highlight + re-dispatch |

Seed: `iadm05_s2_demo_requests` — approved in-scope, denied cross-tenant, partial export.

## S3 stories

| Story ID | Plain name | Status |
|---|---|---|
| IADM-05-US-007 | Submit erasure governance request | Screen-ready |
| IADM-05-US-008 | Per-domain erasure outcomes | Seeded partial (A completed, B pending) |
| IADM-05-US-020 | Close erasure (technical rule) | Screen/API — blocked while unresolved (G-05 legal TBC) |
| IADM-05-US-009 | Govern data-sharing agreement | Screen-ready — validity mandatory; legal basis TBC |
| IADM-05-US-016 | Suspend/revoke/expire agreement | API — lifecycle endpoints + apply-expiry |
| IADM-05-US-019 | View retention/consent/sharing registers | Via platform desk queues + retention register |
| IADM-05-US-015 | Publish privacy/consent (gated) | API returns 423 `IADM05_LEGAL_GATE` — no open path |

Seed: `iadm05_s3_erasure_sharing_seed` — erasure partial, sharing ACTIVE/SUSPENDED/REVOKED/EXPIRED, privacy APPROVED but publish gated.

## Assumptions (gaps)

| Gap | Assumption |
|---|---|
| G-05 | Closure = all domains COMPLETED or REJECTED only |
| G-07 | Destination safeguards stored as notes / TBC |
| G-08 | Real domain services not wired; DEMO simulator only |
| G-03/G-04 | Privacy publish hard-blocked (D-RBAC-M17) |
