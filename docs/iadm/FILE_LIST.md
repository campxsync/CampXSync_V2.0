# IADM Waves A–D — File Inventory

**Date:** 2026-11-21 · Branch `feature/java-backend-integration` · No push

## Migrations (+ rollbacks)

| File | Wave |
|---|---|
| `database/migrations/20261121000100_iadm_assign_institution_admin_auth_uid.sql` | Step 0 / A |
| `database/migrations/20261121000100_rollback__iadm_assign_institution_admin_auth_uid.sql` | Step 0 / A |
| `database/migrations/20261121000200_iadm_wave_a_lifecycle_and_config.sql` | A |
| `database/migrations/20261121000200_rollback__iadm_wave_a_lifecycle_and_config.sql` | A |
| `database/migrations/20261121000300_iadm_wave_b_identity_and_audit.sql` | B |
| `database/migrations/20261121000300_rollback__iadm_wave_b_identity_and_audit.sql` | B |
| `database/migrations/20261121000400_iadm_wave_c_privacy_and_security.sql` | C |
| `database/migrations/20261121000400_rollback__iadm_wave_c_privacy_and_security.sql` | C |
| `database/migrations/20261121000500_iadm_wave_d_billing_and_analytics.sql` | D |
| `database/migrations/20261121000500_rollback__iadm_wave_d_billing_and_analytics.sql` | D |
| `database/migrations/20261121000600_iadm_wave_cd_role_grants.sql` | C/D |
| `database/migrations/20261121000600_rollback__iadm_wave_cd_role_grants.sql` | C/D |

## Services (new modules under `services/administration-services/`)

| Module | Port | Gateway prefix |
|---|---|---|
| `iadm-01-tenant-lifecycle-service` | 8099 | `/api/v1/iadm/01` |
| `iadm-02-platform-config-service` | 8100 | `/api/v1/iadm/02` |
| `iadm-03-identity-access-service` | 8101 | `/api/v1/iadm/03` |
| `iadm-04-billing-entitlements-service` | 8102 | `/api/v1/iadm/04` |
| `iadm-05-data-governance-service` | 8103 | `/api/v1/iadm/05` |
| `iadm-06-platform-analytics-service` | 8104 | `/api/v1/iadm/06` |
| `iadm-07-security-compliance-service` | 8105 | `/api/v1/iadm/07` |
| `iadm-08-audit-ops-service` | 8106 | `/api/v1/iadm/08` |

## Docs

- `docs/iadm/STEP0_REPORT.md`, `WAVE_A_REPORT.md`, `WAVE_B_REPORT.md`, `WAVE_C_REPORT.md`, `WAVE_D_REPORT.md`
- `docs/iadm/FINDINGS.md`, `docs/iadm/FILE_LIST.md`
- `docs/user-stories/IADM_User_Stories.md` (final backlog; DRAFT left unchanged)

## Gateway / control docs

- `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java` — IADM routes 01–08
- `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`, `docs/handover/CURSOR_START_HERE.md`
- Context-leak identical fixes (Step 0.2): SubjectController, AnalyticsController (see FINDINGS SEC-02)

## Not committed (noise / out of scope)

- `classpath.txt`, Playwright video dumps, `_verify_*` scratch reports
