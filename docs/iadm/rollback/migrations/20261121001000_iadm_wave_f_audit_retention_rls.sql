-- =============================================================================
-- Migration: 20261121001000_iadm_wave_f_audit_retention_rls.sql
-- Description: IADM-08-US-003 — allow platform admin (and adm01.manage) on audit.retention_policies
-- Environment: DEV ONLY
-- =============================================================================

DROP POLICY IF EXISTS retention_policies_select ON audit.retention_policies;
CREATE POLICY retention_policies_select ON audit.retention_policies
  FOR SELECT TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('audit.read'))
      OR (SELECT iam.perm_all('adm01.view'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS retention_policies_insert ON audit.retention_policies;
CREATE POLICY retention_policies_insert ON audit.retention_policies
  FOR INSERT TO authenticated
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('audit.admin'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS retention_policies_update ON audit.retention_policies;
CREATE POLICY retention_policies_update ON audit.retention_policies
  FOR UPDATE TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('audit.admin'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('audit.admin'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS retention_policies_delete ON audit.retention_policies;
CREATE POLICY retention_policies_delete ON audit.retention_policies
  FOR DELETE TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('audit.admin'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );
