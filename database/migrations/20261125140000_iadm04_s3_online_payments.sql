-- Migration: 20261125140000_iadm04_s3_online_payments.sql
-- Plain language: IADM-04 S3 — online payment intents, signed callback events,
-- mismatch reconciliation, failed-callback retry. DEV simulator only.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Online payment intents (initiation is not settlement)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.online_payment_intents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  amount_minor_units bigint NOT NULL,
  currency_code character(3) NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  provider_ref text,
  idempotency_key text,
  initiated_by uuid NOT NULL,
  initiated_at timestamptz NOT NULL DEFAULT now(),
  confirmed_at timestamptz,
  failure_reason text,
  mismatch_reason text,
  simulator_outcome text,
  correlation_id text,
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_online_intent_status CHECK (status IN (
    'PENDING', 'CONFIRMED', 'FAILED', 'UNMATCHED', 'MISMATCHED', 'CANCELLED'
  )),
  CONSTRAINT ck_online_intent_amount CHECK (amount_minor_units > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_online_intent_idempotency
  ON plat.online_payment_intents (idempotency_key)
  WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_online_intent_provider_ref
  ON plat.online_payment_intents (provider_ref)
  WHERE provider_ref IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_online_intent_invoice
  ON plat.online_payment_intents (invoice_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_online_intent_status
  ON plat.online_payment_intents (status)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 2. Provider callback events (signature / replay / duplicate evidence)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.payment_callback_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id text NOT NULL,
  intent_id uuid,
  provider_ref text,
  received_at timestamptz NOT NULL DEFAULT now(),
  signature_valid boolean NOT NULL DEFAULT false,
  processing_status text NOT NULL DEFAULT 'RECEIVED',
  settlement_status text,
  amount_minor_units bigint,
  currency_code character(3),
  payload jsonb,
  error_detail text,
  retry_count integer NOT NULL DEFAULT 0,
  last_retry_at timestamptz,
  last_retry_by uuid,
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_callback_processing CHECK (processing_status IN (
    'RECEIVED', 'ACCEPTED', 'REJECTED_SIGNATURE', 'REJECTED_WINDOW',
    'REJECTED_REPLAY', 'DUPLICATE_ACK', 'FAILED_PROCESS', 'RETRY_QUEUED'
  )),
  CONSTRAINT ck_callback_settlement CHECK (
    settlement_status IS NULL
    OR settlement_status IN ('SUCCESS', 'FAILED', 'PENDING')
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_callback_event_id
  ON plat.payment_callback_events (event_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_payment_callback_intent
  ON plat.payment_callback_events (intent_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_payment_callback_failed
  ON plat.payment_callback_events (processing_status)
  WHERE deleted_at IS NULL
    AND processing_status IN ('FAILED_PROCESS', 'RETRY_QUEUED', 'REJECTED_SIGNATURE');

-- -------------------------------------------------------------------------
-- 3. Online reconcile decisions (R01 commercial authority)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.online_payment_reconciliations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  intent_id uuid NOT NULL,
  decision text NOT NULL,
  reason text,
  reconciler_user_id uuid NOT NULL,
  reconciled_at timestamptz NOT NULL DEFAULT now(),
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_online_recon_decision CHECK (decision IN (
    'FORCE_MATCH', 'MARK_FAILED', 'DISMISS'
  )),
  CONSTRAINT uq_online_recon_intent UNIQUE (intent_id)
);

-- -------------------------------------------------------------------------
-- 4. RLS
-- -------------------------------------------------------------------------
ALTER TABLE plat.online_payment_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.online_payment_intents FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.payment_callback_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.payment_callback_events FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.online_payment_reconciliations ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.online_payment_reconciliations FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS online_intent_select ON plat.online_payment_intents;
CREATE POLICY online_intent_select ON plat.online_payment_intents
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
    OR (SELECT iam.has_permission('iadm04.invoices.manage'))
    OR (SELECT iam.has_permission('iadm04.payments.callback.retry'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
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

DROP POLICY IF EXISTS payment_callback_select ON plat.payment_callback_events;
CREATE POLICY payment_callback_select ON plat.payment_callback_events
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.callback'))
    OR (SELECT iam.has_permission('iadm04.payments.callback.retry'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS payment_callback_write ON plat.payment_callback_events;
CREATE POLICY payment_callback_write ON plat.payment_callback_events
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.callback'))
    OR (SELECT iam.has_permission('iadm04.payments.callback.retry'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.callback'))
    OR (SELECT iam.has_permission('iadm04.payments.callback.retry'))
  );

DROP POLICY IF EXISTS online_recon_select ON plat.online_payment_reconciliations;
CREATE POLICY online_recon_select ON plat.online_payment_reconciliations
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS online_recon_insert ON plat.online_payment_reconciliations;
CREATE POLICY online_recon_insert ON plat.online_payment_reconciliations
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.payments.online.initiate'))
  );

GRANT SELECT, INSERT, UPDATE ON plat.online_payment_intents TO authenticated;
GRANT SELECT, INSERT, UPDATE ON plat.payment_callback_events TO authenticated;
GRANT SELECT, INSERT ON plat.online_payment_reconciliations TO authenticated;
GRANT ALL ON plat.online_payment_intents TO service_role;
GRANT ALL ON plat.payment_callback_events TO service_role;
GRANT ALL ON plat.online_payment_reconciliations TO service_role;

COMMENT ON TABLE plat.online_payment_intents IS
  'IADM-04 S3: online payment intents; initiation is not settlement';
COMMENT ON TABLE plat.payment_callback_events IS
  'IADM-04 S3: signed provider callback ledger with replay/duplicate protection';
COMMENT ON TABLE plat.online_payment_reconciliations IS
  'IADM-04 S3: R01 resolution of unmatched/mismatched online payments';

COMMIT;
