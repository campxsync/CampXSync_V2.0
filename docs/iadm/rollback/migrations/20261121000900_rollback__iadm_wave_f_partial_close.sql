-- =============================================================================
-- Rollback: 20261121000900_iadm_wave_f_partial_close.sql
-- Environment: DEV ONLY
-- =============================================================================

DROP POLICY IF EXISTS platform_security_minimums_write ON plat.platform_security_minimums;
DROP POLICY IF EXISTS platform_security_minimums_select ON plat.platform_security_minimums;
DROP TABLE IF EXISTS plat.platform_security_minimums CASCADE;

DROP INDEX IF EXISTS plat.ix_usage_metrics_tenant_college;
ALTER TABLE plat.usage_metrics DROP COLUMN IF EXISTS college_id;

DROP POLICY IF EXISTS plan_change_requests_update ON plat.plan_change_requests;
DROP POLICY IF EXISTS plan_change_requests_insert ON plat.plan_change_requests;
DROP POLICY IF EXISTS plan_change_requests_select ON plat.plan_change_requests;
DROP TABLE IF EXISTS plat.plan_change_requests CASCADE;

DROP TRIGGER IF EXISTS trg_setting_value_version ON cfg.setting_values;
DROP FUNCTION IF EXISTS cfg.snapshot_setting_value_version();
DROP POLICY IF EXISTS setting_value_versions_insert ON cfg.setting_value_versions;
DROP POLICY IF EXISTS setting_value_versions_select ON cfg.setting_value_versions;
DROP TABLE IF EXISTS cfg.setting_value_versions CASCADE;
