-- Migration: 20261125130000_iadm04_s2_finance.sql
-- Plain language: IADM-04 S2 — invoice minor units + snapshot; plan-change requests;
-- offline payment evidence + SoD reconciliation. DEV only.

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Invoices: minor units, snapshot, idempotency, expanded status
-- -------------------------------------------------------------------------
ALTER TABLE plat.invoices
  ADD COLUMN IF NOT EXISTS amount_minor_units bigint,
  ADD COLUMN IF NOT EXISTS pricing_snapshot jsonb,
  ADD COLUMN IF NOT EXISTS idempotency_key text,
  ADD COLUMN IF NOT EXISTS demo_label text,
  ADD COLUMN IF NOT EXISTS void_reason text,
  ADD COLUMN IF NOT EXISTS internal_ref text;

UPDATE plat.invoices
SET amount_minor_units = ROUND(COALESCE(amount, 0) * 100)::bigint
WHERE amount_minor_units IS NULL;

UPDATE plat.invoices SET amount_minor_units = 0 WHERE amount_minor_units IS NULL;

ALTER TABLE plat.invoices
  ALTER COLUMN amount_minor_units SET NOT NULL,
  ALTER COLUMN amount_minor_units SET DEFAULT 0;

ALTER TABLE plat.invoices DROP CONSTRAINT IF EXISTS ck_invoices_status;
ALTER TABLE plat.invoices
  ADD CONSTRAINT ck_invoices_status
  CHECK (status IN ('DRAFT', 'ISSUED', 'DUE', 'PAID', 'VOID', 'CANCELLED'));

-- Map any legacy statuses if present
UPDATE plat.invoices SET status = 'ISSUED' WHERE status IN ('OPEN', 'SENT') AND deleted_at IS NULL;
UPDATE plat.invoices SET status = 'PAID' WHERE status IN ('SETTLED') AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_idempotency
  ON plat.invoices (idempotency_key)
  WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_internal_ref
  ON plat.invoices (internal_ref)
  WHERE internal_ref IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE plat.invoice_lines
  ADD COLUMN IF NOT EXISTS unit_price_minor_units bigint,
  ADD COLUMN IF NOT EXISTS amount_minor_units bigint,
  ADD COLUMN IF NOT EXISTS demo_label text;

UPDATE plat.invoice_lines
SET unit_price_minor_units = ROUND(COALESCE(unit_price, 0) * 100)::bigint,
    amount_minor_units = ROUND(COALESCE(amount, 0) * 100)::bigint
WHERE unit_price_minor_units IS NULL OR amount_minor_units IS NULL;

-- -------------------------------------------------------------------------
-- 2. Plan-change requests (IADM-04 owned; mirrors IADM-03 request→approve)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.plan_change_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  subscription_id uuid NOT NULL,
  current_plan_id uuid NOT NULL,
  requested_plan_id uuid NOT NULL,
  effective_on date NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  request_reason text,
  decision_reason text,
  requested_by uuid NOT NULL,
  decided_by uuid,
  decided_at timestamptz,
  applied_at timestamptz,
  idempotency_key text,
  correlation_id text,
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_plan_change_status CHECK (status IN (
    'PENDING', 'APPROVED', 'REJECTED', 'APPLIED', 'CANCELLED'
  ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_plan_change_idempotency
  ON plat.plan_change_requests (idempotency_key)
  WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_plan_change_tenant_status
  ON plat.plan_change_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 3. Offline payment evidence (immutable) + reconciliation (SoD)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.offline_payment_evidence (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  instrument_type text NOT NULL,
  reference_text text NOT NULL,
  amount_minor_units bigint NOT NULL,
  currency_code character(3) NOT NULL,
  recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  notes text,
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_offline_evidence_instrument CHECK (instrument_type IN (
    'BANK_TRANSFER', 'CHEQUE', 'CASH', 'OTHER'
  )),
  CONSTRAINT ck_offline_evidence_amount CHECK (amount_minor_units > 0)
);

CREATE INDEX IF NOT EXISTS ix_offline_evidence_invoice
  ON plat.offline_payment_evidence (invoice_id)
  WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS plat.offline_payment_reconciliations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  evidence_id uuid NOT NULL,
  decision text NOT NULL,
  reason text,
  reconciler_user_id uuid NOT NULL,
  reconciled_at timestamptz NOT NULL DEFAULT now(),
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_offline_recon_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
  CONSTRAINT uq_offline_recon_evidence UNIQUE (evidence_id)
);

-- -------------------------------------------------------------------------
-- 4. RLS
-- -------------------------------------------------------------------------
ALTER TABLE plat.plan_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.plan_change_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.offline_payment_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.offline_payment_evidence FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.offline_payment_reconciliations ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.offline_payment_reconciliations FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS plan_change_select ON plat.plan_change_requests;
CREATE POLICY plan_change_select ON plat.plan_change_requests
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.plan_change.decide'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.plan_change.request'))
    )
  );

DROP POLICY IF EXISTS plan_change_insert ON plat.plan_change_requests;
CREATE POLICY plan_change_insert ON plat.plan_change_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.plan_change.request'))
    )
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

DROP POLICY IF EXISTS invoices_select ON plat.invoices;
CREATE POLICY invoices_select ON plat.invoices
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.invoices.view.own'))
    )
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

DROP POLICY IF EXISTS invoice_lines_select ON plat.invoice_lines;
CREATE POLICY invoice_lines_select ON plat.invoice_lines
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.invoices.view.own'))
    )
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

DROP POLICY IF EXISTS offline_evidence_select ON plat.offline_payment_evidence;
CREATE POLICY offline_evidence_select ON plat.offline_payment_evidence
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.record'))
    OR (SELECT iam.has_permission('iadm04.payments.offline.reconcile'))
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS offline_evidence_insert ON plat.offline_payment_evidence;
CREATE POLICY offline_evidence_insert ON plat.offline_payment_evidence
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.record'))
  );

DROP POLICY IF EXISTS offline_recon_select ON plat.offline_payment_reconciliations;
CREATE POLICY offline_recon_select ON plat.offline_payment_reconciliations
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.reconcile'))
    OR (SELECT iam.has_permission('iadm04.payments.offline.record'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS offline_recon_insert ON plat.offline_payment_reconciliations;
CREATE POLICY offline_recon_insert ON plat.offline_payment_reconciliations
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.offline.reconcile'))
  );

GRANT SELECT, INSERT, UPDATE ON plat.plan_change_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE ON plat.invoices TO authenticated;
GRANT SELECT, INSERT, UPDATE ON plat.invoice_lines TO authenticated;
GRANT SELECT, INSERT ON plat.offline_payment_evidence TO authenticated;
GRANT SELECT, INSERT ON plat.offline_payment_reconciliations TO authenticated;
GRANT ALL ON plat.plan_change_requests TO service_role;
GRANT ALL ON plat.offline_payment_evidence TO service_role;
GRANT ALL ON plat.offline_payment_reconciliations TO service_role;

COMMENT ON TABLE plat.plan_change_requests IS
  'IADM-04 S2: plan upgrade/downgrade requests; apply only after Super Admin approval';
COMMENT ON TABLE plat.offline_payment_evidence IS
  'IADM-04 S2: immutable offline payment evidence; does not mark invoice paid';
COMMENT ON TABLE plat.offline_payment_reconciliations IS
  'IADM-04 S2: SoD reconcile by a different Super Admin; APPROVED marks invoice PAID';

COMMIT;
