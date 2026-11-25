-- Rollback IADM-04 S5 FORCE RLS + billing_notices (DEV).
-- Does not drop FORCE on tables that should remain FORCED after cutover; only drops notices.

DROP POLICY IF EXISTS billing_notices_platform ON plat.billing_notices;
DROP TABLE IF EXISTS plat.billing_notices;

-- Note: FORCE RLS on plat.subscription_plans / subscriptions / invoices / etc. is left in place
-- intentionally (safer default). Revert only if a prior ENABLE-only state must be restored:
-- ALTER TABLE plat.subscription_plans NO FORCE ROW LEVEL SECURITY;
-- (repeat per table — owner must approve).
