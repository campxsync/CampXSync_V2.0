# IADM-05 S1 Checkpoint — Policy lifecycle + demo desks

**Slice:** S1 (IADM-05-US-026, US-001, US-002, US-003, US-004, US-018, US-013, US-030)  
**Date:** 2026-11-25  
**Prior S0 commit:** `d3c2e74` — feat(iadm05): S0 scaffold  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Service health:** `GET :8103/api/v1/iadm/05/health` → UP

## Stories

| Story ID | Plain name | Status |
|---|---|---|
| IADM-05-US-026 | Policy lifecycle engine | API-ready — draft → in review → approved → published → superseded; SoD author≠approver |
| IADM-05-US-001 | View the classification catalogue | Screen-ready — `/platform/governance` + seeded DEMO rows |
| IADM-05-US-002 | Propose a classification revision | Screen-ready — propose form on platform desk |
| IADM-05-US-003 | Approve or reject a policy revision | Screen-ready — R01 decide controls; R02 denied at BFF+Java |
| IADM-05-US-004 | Define a retention rule | Screen-ready — duration forced/stored as TBC |
| IADM-05-US-018 | Publish approved classification/retention | Screen-ready — privacy publish remains gated (US-015 in S3) |
| IADM-05-US-013 | Inspect policy history | Screen-ready — history panel |
| IADM-05-US-030 | Demo dataset (DEMO ONLY) | Seeded on DEV — classification + retention |

## Seeded DEMO rows (read-back)

| Code | Type | Status |
|---|---|---|
| DEMO-CLASS-PUBLIC | CLASSIFICATION | PUBLISHED |
| DEMO-CLASS-INTERNAL | CLASSIFICATION | IN_REVIEW |
| DEMO-RET-STUDENT-RECORD | RETENTION | PUBLISHED (duration TBC) |
| DEMO-RET-INST-A / INST-B | RETENTION | PUBLISHED (two institutes) |

Migration: `20261125170200_iadm05_s1_demo_seed` (+ rollback twin).

## UI / BFF

- Platform desk: `/platform/governance` · BFF `/api/platform/governance` · `data-testid="iadm05-platform-desk"`
- Institution adherence (ahead for S4): `/institution/governance`
- College view (ahead for S4): `/college/governance`

## Open / parked for later slices

- Headed Playwright demo film for S1 personas — continue with S2 queues then combined evidence (US-032)
- Export / erasure / sharing seeded queues — S2/S3
- Privacy publish legal gate — S3 US-015

## Continue

Proceeding to S2 without owner stop (per kickoff).
