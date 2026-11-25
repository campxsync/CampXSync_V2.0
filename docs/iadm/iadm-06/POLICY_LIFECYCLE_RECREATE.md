# DEMO analytics policy — lifecycle recreate evidence

**Date:** 2026-11-25  
**Label:** DEMO ONLY  
**No IADM-05 code change.**

## How policies were originally created

Direct DEV seed (migration / SQL insert) created:
- `DEMO-ANALYTICS-HANDLING` (RETENTION, domain ANALYTICS) — PUBLISHED v1, author R01
- `DEMO-ANALYTICS-EXPORT` (EXPORT) — PUBLISHED v1, author R01

Owner finish-prompt requires lifecycle confirmation when inserts were direct.

## Lifecycle recreate (executed)

Script: `scripts/demo/iadm06_policy_lifecycle_recreate.mjs`  
Evidence: `docs/iadm/iadm-06/evidence/policy-lifecycle/`

| Step | Actor | Result |
|---|---|---|
| Propose retention `DEMO-ANALYTICS-HANDLING` | System Admin (R02) | HTTP 200, policyId `4c3c9937-627d-40c8-ad7d-117381234ef0`, **version 2** |
| Submit for review | R02 (author) | IN_REVIEW |
| Approve | Super Admin (R01) — **≠ author** | APPROVED |
| Publish | R01 | PUBLISHED; v1 superseded |
| Resolve `RETENTION` + `ANALYTICS` | R01 | code `DEMO-ANALYTICS-HANDLING` version **2** |

## EXPORT policy finding (F-02)

IADM-05 has no propose API for `policy_type=EXPORT`. Direct `DEMO-ANALYTICS-EXPORT` was **SUPERSEDED** in DEV. IADM-06 export gate resolves the same **RETENTION + ANALYTICS** published version.
