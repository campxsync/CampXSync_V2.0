-- Migration Rollback: 20261105000400_rollback__provision_apex_sci.sql
-- Description: Revert live provisioning of institute APEX_SCI
-- Target Environment: Development ONLY (azulbaxszsqxymqmlwfe)
-- Tag: APEX_SCI_LIVE_PROVISIONING

BEGIN;

DO $$
DECLARE
  v_tenant_id uuid := 'c33a407e-3bda-464e-9543-ca46a011aead'::uuid;
  v_college_id uuid := 'c33a407e-3bda-464e-9543-ca46a0110001'::uuid;
  v_admin_id uuid := 'c33a407e-3bda-464e-9543-ca46a0110002'::uuid;
BEGIN
  -- 1. Remove audit & outbox entries tagged with this provisioning
  DELETE FROM audit.events WHERE tenant_id = v_tenant_id;
  DELETE FROM sys.outbox_events WHERE tenant_id = v_tenant_id;

  -- 2. Remove role assignments
  DELETE FROM iam.role_assignments WHERE tenant_id = v_tenant_id;

  -- 3. Remove roles and role_permissions
  DELETE FROM iam.role_permissions WHERE tenant_id = v_tenant_id;
  DELETE FROM iam.roles WHERE tenant_id = v_tenant_id;

  -- 4. Remove user profiles and person
  DELETE FROM iam.user_profiles WHERE tenant_id = v_tenant_id;
  DELETE FROM core.persons WHERE id = v_admin_id;

  -- 5. Remove provisioning job
  DELETE FROM plat.tenant_provisioning WHERE tenant_id = v_tenant_id;

  -- 6. Remove college
  DELETE FROM core.colleges WHERE tenant_id = v_tenant_id;

  -- 7. Remove tenant
  DELETE FROM core.tenants WHERE id = v_tenant_id;

  RAISE NOTICE 'SUCCESS: Rolled back tenant APEX_SCI provisioning.';
END $$;

COMMIT;
