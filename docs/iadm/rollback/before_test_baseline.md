# Before-rollback test baseline (recorded 2026-11-22)
# Tag: CONFIRMED from docs/project-control and Phase 4 commit 0353f6e message/docs unless noted.

Source: RBAC Phase 4 sign-off targets (commit 0353f6e = Phase 4 verification and patch in DEV)
- institute-admin-service: 510 pass (CONFIRMED — Phase 4 target; NOT re-run at backup HEAD in this step)
- attendance-service: 38 pass (CONFIRMED — Phase 4 target)
- other services (Phase 4 suite): 141 pass (CONFIRMED — Phase 4 target)
- api-gateway: 181 pass (CONFIRMED — Phase 4 target)
- college-admin-service: 148 pass (CONFIRMED — Phase 4 target)
- frontend tsc --noEmit: 0 errors (CONFIRMED — Phase 4 / Wave UI-1 / Slice 1 reports claimed clean; NOT re-run at backup)

IADM-era suites (INFERRED from docs/iadm reports at HEAD dc4063f / prior 9a91d64):
- IADM-01..08 module tests existed on ports 8099-8106 — will disappear after code rollback
- Wave E/F/Slice1 claimed green in their reports — evidence in docs/iadm/*_REPORT.md

Backup refs:
- commit dc4063f = snapshot before IADM rollback
- tag backup-iadm-before-rollback
- branch backup/iadm-before-rollback
