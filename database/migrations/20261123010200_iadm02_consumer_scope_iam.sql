-- IADM-02 S3: ensure SVC-CONSUMER-01 exists and can read iam.delegation.max_validity_days (IADM-03 prep).
-- DEV only. Additive. Idempotent.

BEGIN;

DO $$
DECLARE
  v_client_id uuid;
  v_user_id uuid;
  v_perm_id uuid;
  v_tenant_id uuid := '72ecbfe3-2077-41b5-8a20-b6d3e045311b';
  v_scope jsonb := '{"domains":["security","attendance","iam"],"keys":["security.session_timeout_min","attendance.min_pct","iam.delegation.max_validity_days"]}'::jsonb;
BEGIN
  SELECT id INTO v_perm_id FROM iam.permissions
  WHERE code = 'cfg.consumer.read' AND deleted_at IS NULL LIMIT 1;

  SELECT id INTO v_user_id FROM auth.users
  WHERE email = 'iadm02.consumer.dev@campx.local' LIMIT 1;

  SELECT id INTO v_client_id FROM iam.api_clients
  WHERE name = 'SVC-CONSUMER-01' AND deleted_at IS NULL LIMIT 1;

  IF v_client_id IS NULL AND v_user_id IS NOT NULL THEN
    v_client_id := gen_random_uuid();
    INSERT INTO iam.api_clients (
      id, tenant_id, user_id, name, description, status, expires_at, created_by
    ) VALUES (
      v_client_id,
      v_tenant_id,
      v_user_id,
      'SVC-CONSUMER-01',
      'IADM-02 DEV consumer for effective-config read',
      'ACTIVE',
      now() + interval '90 days',
      '54e8308c-ada3-4065-a9fe-51814af6b971'
    );
  END IF;

  IF v_client_id IS NOT NULL AND v_perm_id IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM iam.api_client_grants g
      WHERE g.client_id = v_client_id AND g.permission_id = v_perm_id AND g.deleted_at IS NULL
    ) THEN
      INSERT INTO iam.api_client_grants (
        tenant_id, client_id, permission_id, resource_scope, valid_to, created_by
      ) VALUES (
        v_tenant_id, v_client_id, v_perm_id, v_scope, now() + interval '90 days',
        '54e8308c-ada3-4065-a9fe-51814af6b971'
      );
    ELSE
      UPDATE iam.api_client_grants
      SET resource_scope = v_scope, updated_at = now()
      WHERE client_id = v_client_id AND permission_id = v_perm_id AND deleted_at IS NULL;
    END IF;
  END IF;
END $$;

COMMIT;
