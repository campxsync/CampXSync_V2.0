-- Rollback: 20261128150000_rollback__iadm_rls_write_tenant_guard.sql
-- Restores WITH CHECK policies as captured before 20261128150000_iadm_rls_write_tenant_guard.sql
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP POLICY IF EXISTS usage_write ON plat.usage_measurements;
CREATE POLICY usage_write ON plat.usage_measurements
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.usage.record'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.usage.record'))
  );

DROP POLICY IF EXISTS invoices_write ON plat.invoices;
CREATE POLICY invoices_write ON plat.invoices
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  );

DROP POLICY IF EXISTS invoice_lines_write ON plat.invoice_lines;
CREATE POLICY invoice_lines_write ON plat.invoice_lines
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  );

DROP POLICY IF EXISTS billing_notices_platform ON plat.billing_notices;
CREATE POLICY billing_notices_platform ON plat.billing_notices
  FOR ALL TO authenticated
  USING (
    iam.is_platform_admin()
    OR iam.has_permission('iadm04.audit.view')
    OR iam.has_permission('iadm04.plans.manage')
    OR iam.has_permission('iadm04.invoices.manage')
  )
  WITH CHECK (
    iam.is_platform_admin()
    OR iam.has_permission('iadm04.plans.manage')
    OR iam.has_permission('iadm04.invoices.manage')
    OR iam.has_permission('iadm04.payments.online.initiate')
    OR iam.has_permission('iadm04.payments.offline.reconcile')
  );

DROP POLICY IF EXISTS offline_evidence_insert ON plat.offline_payment_evidence;
CREATE POLICY offline_evidence_insert ON plat.offline_payment_evidence
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.record'))
  );

DROP POLICY IF EXISTS offline_recon_insert ON plat.offline_payment_reconciliations;
CREATE POLICY offline_recon_insert ON plat.offline_payment_reconciliations
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.reconcile'))
  );

DROP POLICY IF EXISTS online_intent_write ON plat.online_payment_intents;
CREATE POLICY online_intent_write ON plat.online_payment_intents
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
    OR (SELECT iam.has_permission('iadm04.payments.callback'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
    OR (SELECT iam.has_permission('iadm04.payments.callback'))
  );

DROP POLICY IF EXISTS online_recon_insert ON plat.online_payment_reconciliations;
CREATE POLICY online_recon_insert ON plat.online_payment_reconciliations
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
  );

DROP POLICY IF EXISTS plan_change_update ON plat.plan_change_requests;
CREATE POLICY plan_change_update ON plat.plan_change_requests
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plan_change.decide'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plan_change.decide'))
  );

DROP POLICY IF EXISTS renewal_action_insert ON plat.renewal_actions;
CREATE POLICY renewal_action_insert ON plat.renewal_actions
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
  );

DROP POLICY IF EXISTS subscription_history_insert ON plat.subscription_history;
CREATE POLICY subscription_history_insert ON plat.subscription_history
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  );

DROP POLICY IF EXISTS institute_lifecycle_requests_update ON plat.institute_lifecycle_requests;
CREATE POLICY institute_lifecycle_requests_update ON plat.institute_lifecycle_requests
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.perm_all('adm01.manage'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.perm_all('adm01.manage'))
  );

DROP POLICY IF EXISTS governance_idempotency_insert ON iam.governance_idempotency;
CREATE POLICY governance_idempotency_insert ON iam.governance_idempotency
  FOR INSERT TO authenticated
  WITH CHECK (
    (actor_user_id = (SELECT auth.uid()))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS governance_idempotency_update ON iam.governance_idempotency;
CREATE POLICY governance_idempotency_update ON iam.governance_idempotency
  FOR UPDATE TO authenticated
  USING (
    (actor_user_id = (SELECT auth.uid()))
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    (actor_user_id = (SELECT auth.uid()))
    OR (SELECT iam.is_platform_admin())
  );

COMMIT;
