-- =============================================================================
-- Rollback: 20261121000800_iadm_wave_e_handover_and_suspend.sql
-- Environment: DEV ONLY
-- =============================================================================

DROP POLICY IF EXISTS tenant_session_blocks_insert ON plat.tenant_session_blocks;
DROP POLICY IF EXISTS tenant_session_blocks_select ON plat.tenant_session_blocks;
DROP TABLE IF EXISTS plat.tenant_session_blocks CASCADE;

DROP FUNCTION IF EXISTS plat.is_tenant_suspended(uuid);
DROP FUNCTION IF EXISTS plat.is_college_handed_over(uuid);
