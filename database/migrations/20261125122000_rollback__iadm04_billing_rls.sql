-- Rollback: 20261125122000_iadm04_billing_rls.sql (restores pre-IADM-04 subscription RLS shape)
BEGIN;

DROP POLICY IF EXISTS subscription_plans_platform ON plat.subscription_plans;
CREATE POLICY subscription_plans_platform ON plat.subscription_plans
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()) OR (SELECT iam.has_permission('plat.write')))
  WITH CHECK ((SELECT iam.is_platform_admin()) OR (SELECT iam.has_permission('plat.write')));

DROP POLICY IF EXISTS subscription_plans_read ON plat.subscription_plans;
CREATE POLICY subscription_plans_read ON plat.subscription_plans
  FOR SELECT TO authenticated
  USING ((SELECT iam.has_permission('plat.read')) OR is_published = true);

DROP POLICY IF EXISTS plan_entitlements_select ON plat.plan_entitlements;
DROP POLICY IF EXISTS plan_entitlements_write ON plat.plan_entitlements;

DROP POLICY IF EXISTS subscriptions_select ON plat.subscriptions;
CREATE POLICY subscriptions_select ON plat.subscriptions
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND (SELECT iam.has_permission('plat.read'))
  );

DROP POLICY IF EXISTS subscriptions_insert ON plat.subscriptions;
CREATE POLICY subscriptions_insert ON plat.subscriptions
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND (SELECT iam.has_permission('plat.write'))
  );

DROP POLICY IF EXISTS subscriptions_update ON plat.subscriptions;
CREATE POLICY subscriptions_update ON plat.subscriptions
  FOR UPDATE TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND (SELECT iam.has_permission('plat.write'))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND (SELECT iam.has_permission('plat.write'))
  );

DROP POLICY IF EXISTS subscriptions_delete ON plat.subscriptions;
CREATE POLICY subscriptions_delete ON plat.subscriptions
  FOR DELETE TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND (SELECT iam.has_permission('plat.write'))
  );

COMMIT;
