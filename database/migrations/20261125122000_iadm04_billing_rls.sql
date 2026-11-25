-- Migration: 20261125122000_iadm04_billing_rls.sql
-- Plain language: Align plat subscription RLS with IADM-04 permission keys + platform admin.
-- Keeps legacy plat.read/plat.write paths for monolith compatibility until cutover.

BEGIN;

-- -------------------------------------------------------------------------
-- subscription_plans — platform manage via iadm04.plans.*
-- -------------------------------------------------------------------------
DROP POLICY IF EXISTS subscription_plans_platform ON plat.subscription_plans;
CREATE POLICY subscription_plans_platform ON plat.subscription_plans
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plans.manage'))
    OR (SELECT iam.has_permission('plat.write'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plans.manage'))
    OR (SELECT iam.has_permission('plat.write'))
  );

DROP POLICY IF EXISTS subscription_plans_read ON plat.subscription_plans;
CREATE POLICY subscription_plans_read ON plat.subscription_plans
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plans.view'))
    OR (SELECT iam.has_permission('iadm04.plans.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view.own'))
    OR (SELECT iam.has_permission('plat.read'))
    OR is_published = true
  );

-- -------------------------------------------------------------------------
-- plan_entitlements
-- -------------------------------------------------------------------------
DROP POLICY IF EXISTS plan_entitlements_select ON plat.plan_entitlements;
DROP POLICY IF EXISTS plan_entitlements_all ON plat.plan_entitlements;
DROP POLICY IF EXISTS plan_entitlements_platform ON plat.plan_entitlements;
DROP POLICY IF EXISTS plan_entitlements_read ON plat.plan_entitlements;

CREATE POLICY plan_entitlements_select ON plat.plan_entitlements
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plans.view'))
    OR (SELECT iam.has_permission('iadm04.entitlements.map'))
    OR (SELECT iam.has_permission('iadm04.entitlements.check'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view.own'))
    OR (SELECT iam.has_permission('plat.read'))
  );

CREATE POLICY plan_entitlements_write ON plat.plan_entitlements
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.entitlements.map'))
    OR (SELECT iam.has_permission('plat.write'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.entitlements.map'))
    OR (SELECT iam.has_permission('plat.write'))
  );

-- -------------------------------------------------------------------------
-- subscriptions — cross-tenant platform view + own-institute view
-- -------------------------------------------------------------------------
DROP POLICY IF EXISTS subscriptions_select ON plat.subscriptions;
CREATE POLICY subscriptions_select ON plat.subscriptions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (SELECT iam.has_permission('iadm04.entitlements.check'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (
        (SELECT iam.has_permission('iadm04.subscriptions.view.own'))
        OR (SELECT iam.has_permission('plat.read'))
      )
    )
  );

DROP POLICY IF EXISTS subscriptions_insert ON plat.subscriptions;
CREATE POLICY subscriptions_insert ON plat.subscriptions
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('plat.write'))
    )
  );

DROP POLICY IF EXISTS subscriptions_update ON plat.subscriptions;
CREATE POLICY subscriptions_update ON plat.subscriptions
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
    OR (SELECT iam.has_permission('iadm04.plan_change.decide'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('plat.write'))
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
    OR (SELECT iam.has_permission('iadm04.plan_change.decide'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('plat.write'))
    )
  );

DROP POLICY IF EXISTS subscriptions_delete ON plat.subscriptions;
CREATE POLICY subscriptions_delete ON plat.subscriptions
  FOR DELETE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plans.manage'))
  );

-- -------------------------------------------------------------------------
-- subscription_history — already created; refresh policies
-- -------------------------------------------------------------------------
DROP POLICY IF EXISTS subscription_history_select ON plat.subscription_history;
CREATE POLICY subscription_history_select ON plat.subscription_history
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.audit.view'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.subscriptions.view.own'))
    )
  );

DROP POLICY IF EXISTS subscription_history_insert ON plat.subscription_history;
CREATE POLICY subscription_history_insert ON plat.subscription_history
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  );

COMMIT;
