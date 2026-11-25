-- IADM-04 S5: FORCE RLS on legacy plat.* billing tables + billing notices (US-038).
-- DEV only (azulbaxszsqxymqmlwfe). Keep plat.* tables; IADM-04 owns writes.

-- -------------------------------------------------------------------------
-- 1. FORCE RLS on commercial tables that previously had ENABLE only
-- -------------------------------------------------------------------------
ALTER TABLE plat.subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.subscription_plans FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.plan_entitlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.plan_entitlements FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.subscriptions FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.invoices FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.invoice_lines FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.gateway_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.gateway_transactions FORCE ROW LEVEL SECURITY;

ALTER TABLE plat.usage_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.usage_metrics FORCE ROW LEVEL SECURITY;

-- -------------------------------------------------------------------------
-- 2. Billing notices (best-effort; never grant/deny access)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.billing_notices (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       uuid REFERENCES core.tenants (id),
  notice_type     text NOT NULL,
  entity_type     text,
  entity_id       text,
  message         text NOT NULL,
  status          text NOT NULL DEFAULT 'DELAYED'
                  CHECK (status IN ('DELAYED', 'DELIVERED', 'FAILED')),
  demo_label      text DEFAULT 'DEMO',
  created_by      uuid,
  created_at      timestamptz NOT NULL DEFAULT now(),
  delivered_at    timestamptz,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz
);

CREATE INDEX IF NOT EXISTS idx_billing_notices_status
  ON plat.billing_notices (status) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_billing_notices_tenant
  ON plat.billing_notices (tenant_id) WHERE deleted_at IS NULL;

ALTER TABLE plat.billing_notices ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.billing_notices FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS billing_notices_platform ON plat.billing_notices;
CREATE POLICY billing_notices_platform ON plat.billing_notices
  FOR ALL
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

COMMENT ON TABLE plat.billing_notices IS
  'IADM-04 S5 DEMO: best-effort billing notices; DELAYED when notifications disabled (US-038).';
