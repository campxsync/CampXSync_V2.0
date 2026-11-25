-- Rollback: 20261125130000_iadm04_s2_finance.sql
BEGIN;

DROP POLICY IF EXISTS offline_recon_insert ON plat.offline_payment_reconciliations;
DROP POLICY IF EXISTS offline_recon_select ON plat.offline_payment_reconciliations;
DROP POLICY IF EXISTS offline_evidence_insert ON plat.offline_payment_evidence;
DROP POLICY IF EXISTS offline_evidence_select ON plat.offline_payment_evidence;
DROP TABLE IF EXISTS plat.offline_payment_reconciliations;
DROP TABLE IF EXISTS plat.offline_payment_evidence;

DROP POLICY IF EXISTS plan_change_update ON plat.plan_change_requests;
DROP POLICY IF EXISTS plan_change_insert ON plat.plan_change_requests;
DROP POLICY IF EXISTS plan_change_select ON plat.plan_change_requests;
DROP TABLE IF EXISTS plat.plan_change_requests;

DROP POLICY IF EXISTS invoice_lines_write ON plat.invoice_lines;
DROP POLICY IF EXISTS invoice_lines_select ON plat.invoice_lines;
DROP POLICY IF EXISTS invoices_write ON plat.invoices;
DROP POLICY IF EXISTS invoices_select ON plat.invoices;

ALTER TABLE plat.invoice_lines
  DROP COLUMN IF EXISTS unit_price_minor_units,
  DROP COLUMN IF EXISTS amount_minor_units,
  DROP COLUMN IF EXISTS demo_label;

DROP INDEX IF EXISTS uq_invoices_idempotency;
DROP INDEX IF EXISTS uq_invoices_internal_ref;

ALTER TABLE plat.invoices
  DROP COLUMN IF EXISTS amount_minor_units,
  DROP COLUMN IF EXISTS pricing_snapshot,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS demo_label,
  DROP COLUMN IF EXISTS void_reason,
  DROP COLUMN IF EXISTS internal_ref;

COMMIT;
