-- Rollback 20261121001100

DROP POLICY IF EXISTS institute_retention_update ON plat.institute_retention_policies;
DROP POLICY IF EXISTS institute_retention_insert ON plat.institute_retention_policies;
DROP POLICY IF EXISTS institute_retention_select ON plat.institute_retention_policies;
DROP TABLE IF EXISTS plat.institute_retention_policies CASCADE;

DROP POLICY IF EXISTS retention_minimums_write ON plat.retention_minimums;
DROP POLICY IF EXISTS retention_minimums_select ON plat.retention_minimums;
DROP TABLE IF EXISTS plat.retention_minimums CASCADE;

-- Restore Scripts_V3.0 billing policies (approx)
DROP POLICY IF EXISTS subscriptions_select ON plat.subscriptions;
CREATE POLICY subscriptions_select ON plat.subscriptions FOR SELECT TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.read')));
DROP POLICY IF EXISTS subscriptions_insert ON plat.subscriptions;
CREATE POLICY subscriptions_insert ON plat.subscriptions FOR INSERT TO authenticated
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')));
DROP POLICY IF EXISTS subscriptions_update ON plat.subscriptions;
CREATE POLICY subscriptions_update ON plat.subscriptions FOR UPDATE TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')));

DROP POLICY IF EXISTS invoices_select ON plat.invoices;
CREATE POLICY invoices_select ON plat.invoices FOR SELECT TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.read')));
DROP POLICY IF EXISTS invoices_insert ON plat.invoices;
CREATE POLICY invoices_insert ON plat.invoices FOR INSERT TO authenticated
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')));
DROP POLICY IF EXISTS invoices_update ON plat.invoices;
CREATE POLICY invoices_update ON plat.invoices FOR UPDATE TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) AND (SELECT iam.has_permission('plat.write')));
