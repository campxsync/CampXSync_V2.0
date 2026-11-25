-- Migration: 20261125170400_iadm05_s3_erasure_sharing_seed.sql
-- Plain language: DEMO erasure (partial) + sharing agreement states (US-007/008/009/016/030).
-- Subject refs are opaque. Legal basis = TBC. Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DO $$
DECLARE
  v_requester UUID;
  v_reviewer UUID;
  v_tenant_a UUID;
  v_policy UUID;
  v_erase UUID := 'a5000000-0000-4000-8000-000000000011';
  v_share_active UUID := 'a5000000-0000-4000-8000-000000000021';
  v_share_susp UUID := 'a5000000-0000-4000-8000-000000000022';
  v_share_rev UUID := 'a5000000-0000-4000-8000-000000000023';
  v_share_exp UUID := 'a5000000-0000-4000-8000-000000000024';
  v_privacy UUID := 'a5000000-0000-4000-8000-000000000031';
BEGIN
  SELECT user_id INTO v_requester FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
  SELECT user_id INTO v_reviewer FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id OFFSET 1 LIMIT 1;
  IF v_reviewer IS NULL THEN v_reviewer := v_requester; END IF;
  SELECT id INTO v_tenant_a FROM core.tenants ORDER BY created_at NULLS LAST LIMIT 1;
  SELECT id INTO v_policy FROM gov.policies WHERE code = 'DEMO-RET-STUDENT-RECORD' AND status = 'PUBLISHED' LIMIT 1;

  -- Erasure with one completed + one pending domain (US-008)
  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_erase) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, scope_tenant_id, subject_ref_opaque,
      applicable_policy_id, applicable_policy_version, demo_label
    ) VALUES (
      v_erase, 'ERASURE', 'PARTIALLY_COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO erasure coordination', ARRAY['DEMO_DOMAIN_A','DEMO_DOMAIN_B'],
      v_tenant_a, 'opaque-demo-subject-001', v_policy, 1, 'DEMO ONLY'
    );
    INSERT INTO gov.domain_outcomes (request_id, domain_code, outcome, receipt_ref, reason_code, correlation_id)
    VALUES
      (v_erase, 'DEMO_DOMAIN_A', 'COMPLETED', 'demo-erasure-a', NULL, gen_random_uuid()),
      (v_erase, 'DEMO_DOMAIN_B', 'PENDING', NULL, 'DEMO_PARTIAL', gen_random_uuid());
  END IF;

  -- Sharing agreements: active / suspended / revoked / expired
  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_share_active) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, fields_requested, scope_tenant_id,
      sharing_provider, sharing_consumer, sharing_validity_start, sharing_validity_end,
      legal_basis_ref, onward_sharing_restriction, revocation_conditions,
      agreement_status, demo_label
    ) VALUES (
      v_share_active, 'SHARING', 'COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO active sharing', ARRAY['STM'], ARRAY['status'], v_tenant_a,
      'DEMO_PROVIDER', 'DEMO_CONSUMER', now() - interval '1 day', now() + interval '30 days',
      'TBC', 'NO_ONWARD_SHARING', 'TBC', 'ACTIVE', 'DEMO ONLY'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_share_susp) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, scope_tenant_id, sharing_provider, sharing_consumer,
      sharing_validity_end, legal_basis_ref, agreement_status, demo_label
    ) VALUES (
      v_share_susp, 'SHARING', 'COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO suspended sharing', ARRAY['STM'], v_tenant_a, 'DEMO_PROVIDER', 'DEMO_CONSUMER_B',
      now() + interval '10 days', 'TBC', 'SUSPENDED', 'DEMO ONLY'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_share_rev) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, scope_tenant_id, sharing_provider, sharing_consumer,
      sharing_validity_end, legal_basis_ref, agreement_status, demo_label
    ) VALUES (
      v_share_rev, 'SHARING', 'COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO revoked sharing', ARRAY['STM'], v_tenant_a, 'DEMO_PROVIDER', 'DEMO_CONSUMER_C',
      now() + interval '10 days', 'TBC', 'REVOKED', 'DEMO ONLY'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM gov.governance_requests WHERE id = v_share_exp) THEN
    INSERT INTO gov.governance_requests (
      id, request_type, status, correlation_id, requester_user_id, reviewer_user_id,
      purpose, domain_codes, scope_tenant_id, sharing_provider, sharing_consumer,
      sharing_validity_start, sharing_validity_end, legal_basis_ref, agreement_status, demo_label
    ) VALUES (
      v_share_exp, 'SHARING', 'COMPLETED', gen_random_uuid(), v_requester, v_reviewer,
      'DEMO expired sharing', ARRAY['STM'], v_tenant_a, 'DEMO_PROVIDER', 'DEMO_CONSUMER_D',
      now() - interval '60 days', now() - interval '1 day', 'TBC', 'EXPIRED', 'DEMO ONLY'
    );
  END IF;

  -- Privacy/consent approved but publish gated (US-015)
  IF NOT EXISTS (SELECT 1 FROM gov.policies WHERE id = v_privacy) THEN
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, demo_label
    ) VALUES (
      v_privacy, 'PRIVACY_CONSENT', 'DEMO-PRIVACY-GATED',
      'Demo privacy/consent (publish gated)', 'DEMO ONLY', 'APPROVED', 1,
      v_requester, 'PLATFORM', 'DEMO ONLY — publish blocked by D-RBAC-M17'
    );
  END IF;
END $$;

COMMIT;
