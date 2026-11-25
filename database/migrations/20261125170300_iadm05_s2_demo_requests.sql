-- Migration: 20261125170300_iadm05_s2_demo_requests.sql
-- Plain language: DEMO ONLY export queue rows (US-005/006/014/030 extension).
-- One in-scope approved + one denied cross-tenant. No export payloads stored.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DO $$
DECLARE
  v_requester UUID;
  v_reviewer UUID;
  v_tenant_a UUID;
  v_tenant_b UUID;
  v_policy UUID;
  v_approved UUID := 'a5000000-0000-4000-8000-000000000001';
  v_denied UUID := 'a5000000-0000-4000-8000-000000000002';
  v_partial UUID := 'a5000000-0000-4000-8000-000000000003';
BEGIN
  SELECT user_id INTO v_requester FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
  SELECT user_id INTO v_reviewer FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id OFFSET 1 LIMIT 1;
  IF v_reviewer IS NULL THEN v_reviewer := v_requester; END IF;
  SELECT id INTO v_tenant_a FROM core.tenants ORDER BY created_at NULLS LAST LIMIT 1;
  SELECT id INTO v_tenant_b FROM core.tenants ORDER BY created_at NULLS LAST OFFSET 1 LIMIT 1;
  SELECT id INTO v_policy FROM gov.policies WHERE code = 'DEMO-RET-STUDENT-RECORD' AND status = 'PUBLISHED' LIMIT 1;

  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_approved) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, fields_requested, scope_tenant_id, destination_note,
      applicable_policy_id, applicable_policy_version, demo_label
    ) VALUES (
      v_approved, 'EXPORT', 'APPROVED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO in-scope export for queue screen', ARRAY['DEMO_DOMAIN_A'], ARRAY['id','status'],
      v_tenant_a, 'DEMO destination TBC (G-07)', v_policy, 1, 'DEMO ONLY'
    );
    INSERT INTO gov.domain_outcomes (request_id, domain_code, outcome, receipt_ref, correlation_id)
    VALUES (v_approved, 'DEMO_DOMAIN_A', 'PENDING', NULL, gen_random_uuid());
  END IF;

  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_denied) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, fields_requested, scope_tenant_id, destination_note,
      applicable_policy_id, applicable_policy_version, demo_label
    ) VALUES (
      v_denied, 'EXPORT', 'REJECTED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO denied cross-tenant export', ARRAY['DEMO_DOMAIN_A'], ARRAY['id'],
      v_tenant_b, 'cross-tenant denied', v_policy, 1, 'DEMO ONLY'
    );
  END IF;

  -- Operator partial/failed example (US-014)
  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_partial) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, fields_requested, scope_tenant_id,
      applicable_policy_id, applicable_policy_version, demo_label
    ) VALUES (
      v_partial, 'EXPORT', 'PARTIALLY_COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO partial domain execution', ARRAY['DEMO_DOMAIN_A','DEMO_DOMAIN_B_FAIL'], ARRAY['id'],
      v_tenant_a, v_policy, 1, 'DEMO ONLY'
    );
    INSERT INTO gov.domain_outcomes (request_id, domain_code, outcome, receipt_ref, reason_code, correlation_id)
    VALUES
      (v_partial, 'DEMO_DOMAIN_A', 'COMPLETED', 'demo-receipt-partial-a', NULL, gen_random_uuid()),
      (v_partial, 'DEMO_DOMAIN_B_FAIL', 'REJECTED', NULL, 'DEMO_DOMAIN_REJECT', gen_random_uuid());
  END IF;
END $$;

COMMIT;
