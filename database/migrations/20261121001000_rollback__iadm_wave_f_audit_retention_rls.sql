-- Rollback 20261121001000 — restore Scripts_V3.0 audit.retention_policies policies

DROP POLICY IF EXISTS retention_policies_select ON audit.retention_policies;
CREATE POLICY retention_policies_select ON audit.retention_policies
  FOR SELECT TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('audit.read')));

DROP POLICY IF EXISTS retention_policies_insert ON audit.retention_policies;
CREATE POLICY retention_policies_insert ON audit.retention_policies
  FOR INSERT TO authenticated
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('audit.admin')));

DROP POLICY IF EXISTS retention_policies_update ON audit.retention_policies;
CREATE POLICY retention_policies_update ON audit.retention_policies
  FOR UPDATE TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('audit.admin')))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('audit.admin')));

DROP POLICY IF EXISTS retention_policies_delete ON audit.retention_policies;
CREATE POLICY retention_policies_delete ON audit.retention_policies
  FOR DELETE TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('audit.admin')));
