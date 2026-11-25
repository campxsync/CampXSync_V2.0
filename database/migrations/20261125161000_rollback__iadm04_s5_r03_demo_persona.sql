-- Rollback IADM-04 S5 R03 DEMO persona (DEV). Does not delete Auth user (use Admin API if needed).

DELETE FROM plat.invoices
WHERE idempotency_key = 'iadm04-s5-r03-demo-invoice'
   OR (tenant_id = 'd4000000-0000-4000-8000-000000000004' AND demo_label = 'DEMO');

DELETE FROM plat.subscription_history
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004';

DELETE FROM plat.subscriptions
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004'
   OR idempotency_key = 'iadm04-s5-r03-demo-pending';

DELETE FROM iam.role_assignments
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004';

DELETE FROM iam.user_profiles
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004'
   OR email = 'institution.iadm04.demo@campx.local';

DELETE FROM iam.roles
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004'
  AND code = 'INSTITUTION_ADMIN';

UPDATE core.tenants
SET deleted_at = now(), status = 'SUSPENDED', updated_at = now()
WHERE id = 'd4000000-0000-4000-8000-000000000004';
