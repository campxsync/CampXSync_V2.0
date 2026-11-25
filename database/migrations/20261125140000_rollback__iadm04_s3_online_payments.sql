-- Rollback: 20261125140000_iadm04_s3_online_payments.sql
-- DEV only. Drops S3 online-payment tables.

BEGIN;

DROP TABLE IF EXISTS plat.online_payment_reconciliations CASCADE;
DROP TABLE IF EXISTS plat.payment_callback_events CASCADE;
DROP TABLE IF EXISTS plat.online_payment_intents CASCADE;

COMMIT;
