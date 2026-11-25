# DEMO analytics policy — IADM-05 lifecycle recreate

- Code: `DEMO-ANALYTICS-HANDLING`
- Author: System Admin (R02) — propose + submit-review
- Approver/Publisher: Super Admin (R01) — different from author
- Label: DEMO ONLY
- Finding F-02: no EXPORT policy_type propose path; export gate uses same RETENTION+ANALYTICS resolve
- Direct-insert DEMO-ANALYTICS-EXPORT retired (superseded via SQL after this run)

## Steps
- **propose**: HTTP 200; {"step":"propose","status":200,"policyId":"4c3c9937-627d-40c8-ad7d-117381234ef0","versionNo":2}
- **submit-review**: HTTP 200; {"step":"submit-review","status":200,"statusField":"IN_REVIEW"}
- **approve**: HTTP 200; {"step":"approve","status":200,"statusField":"APPROVED","authorNeApprover":true}
- **publish**: HTTP 200; {"step":"publish","status":200,"statusField":"PUBLISHED","code":"DEMO-ANALYTICS-HANDLING","versionNo":2,"effectiveFrom":"2026-11-25T12:22:47.974Z"}
- **resolve-check**: HTTP 200; {"step":"resolve-check","status":200,"code":"DEMO-ANALYTICS-HANDLING","versionNo":2,"policyId":"4c3c9937-627d-40c8-ad7d-117381234ef0"}