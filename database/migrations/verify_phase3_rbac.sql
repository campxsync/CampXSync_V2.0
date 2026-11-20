-- Verification SQL: verify_phase3_rbac.sql
-- Description: Automated assertions verifying Phase 3 RBAC gates
-- Target DB: azulbaxszsqxymqmlwfe (Dev Supabase)

DO $$
DECLARE
  v_constraint_count int;
  v_func_count int;
BEGIN
  -- 1. Assert ck_api_clients_expires_at exists on iam.api_clients
  SELECT count(*) INTO v_constraint_count
  FROM pg_constraint con
  JOIN pg_class rel ON rel.oid = con.conrelid
  JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
  WHERE nsp.nspname = 'iam'
    AND rel.relname = 'api_clients'
    AND con.conname = 'ck_api_clients_expires_at';

  IF v_constraint_count = 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Constraint ck_api_clients_expires_at missing from iam.api_clients';
  END IF;

  -- 2. Assert plat.assign_institution_admin exists
  SELECT count(*) INTO v_func_count
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'plat'
    AND p.proname = 'assign_institution_admin';

  IF v_func_count = 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Function plat.assign_institution_admin missing';
  END IF;

  RAISE NOTICE 'SUCCESS: All Phase 3 database assertions verified cleanly';
END $$;
