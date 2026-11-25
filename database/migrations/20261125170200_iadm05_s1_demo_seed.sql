-- Migration: 20261125170200_iadm05_s1_demo_seed.sql
-- Plain language: DEMO ONLY classification + retention rows for S1 screens (US-030).
-- Duration always TBC. Labels are NOT LEGAL CLASSIFICATIONS (G-06).
-- Idempotent by policy code. Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- Author: first platform admin user (existing); never invent emails
DO $$
DECLARE
  v_author UUID;
  v_approver UUID;
  v_pol_pub UUID;
  v_pol_draft UUID;
  v_pol_ret UUID;
  v_tenant_a UUID;
  v_tenant_b UUID;
BEGIN
  SELECT user_id INTO v_author FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
  SELECT user_id INTO v_approver FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY id OFFSET 1 LIMIT 1;
  IF v_approver IS NULL THEN
    v_approver := v_author; -- SoD demos use different accounts at API level; seed still needs a UUID
  END IF;

  SELECT id INTO v_tenant_a FROM core.tenants ORDER BY created_at NULLS LAST LIMIT 1;
  SELECT id INTO v_tenant_b FROM core.tenants ORDER BY created_at NULLS LAST OFFSET 1 LIMIT 1;

  -- Published classification DEMO-PUBLIC-v1
  IF NOT EXISTS (SELECT 1 FROM gov.policies WHERE code = 'DEMO-CLASS-PUBLIC' AND version_no = 1) THEN
    v_pol_pub := gen_random_uuid();
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, scope_tenant_id, effective_from, demo_label
    ) VALUES (
      v_pol_pub, 'CLASSIFICATION', 'DEMO-CLASS-PUBLIC',
      'Demo Public classification', 'DEMO ONLY', 'PUBLISHED', 1,
      v_author, 'PLATFORM', NULL, now() - interval '1 day',
      'DEMO ONLY / NOT LEGAL CLASSIFICATIONS'
    );
    INSERT INTO gov.classification_defs (
      policy_id, class_code, class_name, description, handling_guidance, permitted_use_notes, demo_label
    ) VALUES (
      v_pol_pub, 'DEMO-CLASS-PUBLIC', 'Demo Public',
      'Illustrative public label for demos only.', 'TBC', 'TBC',
      'DEMO ONLY / NOT LEGAL CLASSIFICATIONS'
    );
    INSERT INTO gov.policy_decisions (policy_id, decision, actor_user_id, rationale_ref)
    VALUES (v_pol_pub, 'APPROVE', COALESCE(v_approver, v_author), 'demo-seed-approve-ref');
  END IF;

  -- Draft classification awaiting review (for propose/approve demos)
  IF NOT EXISTS (SELECT 1 FROM gov.policies WHERE code = 'DEMO-CLASS-INTERNAL' AND version_no = 1) THEN
    v_pol_draft := gen_random_uuid();
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, demo_label
    ) VALUES (
      v_pol_draft, 'CLASSIFICATION', 'DEMO-CLASS-INTERNAL',
      'Demo Internal classification (draft)', 'DEMO ONLY', 'IN_REVIEW', 1,
      v_author, 'PLATFORM', 'DEMO ONLY / NOT LEGAL CLASSIFICATIONS'
    );
    INSERT INTO gov.classification_defs (
      policy_id, class_code, class_name, description, handling_guidance, permitted_use_notes, demo_label
    ) VALUES (
      v_pol_draft, 'DEMO-CLASS-INTERNAL', 'Demo Internal',
      'Draft illustrative internal label.', 'TBC', 'TBC',
      'DEMO ONLY / NOT LEGAL CLASSIFICATIONS'
    );
  END IF;

  -- Published retention rule with duration TBC (never invent a number)
  IF NOT EXISTS (SELECT 1 FROM gov.policies WHERE code = 'DEMO-RET-STUDENT-RECORD' AND version_no = 1) THEN
    v_pol_ret := gen_random_uuid();
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, effective_from, demo_label
    ) VALUES (
      v_pol_ret, 'RETENTION', 'DEMO-RET-STUDENT-RECORD',
      'Demo student-record retention', 'DEMO ONLY', 'PUBLISHED', 1,
      v_author, 'PLATFORM', now() - interval '1 day', 'DEMO ONLY'
    );
    INSERT INTO gov.retention_rules (
      policy_id, domain_code, record_category, scope_note, trigger_note,
      rule_reference, duration_note, hold_handling, demo_label
    ) VALUES (
      v_pol_ret, 'STM', 'student_lifecycle_record', 'platform demo scope',
      'end_of_relationship', 'TBC', 'TBC', 'TBC', 'DEMO ONLY'
    );
    INSERT INTO gov.policy_decisions (policy_id, decision, actor_user_id, rationale_ref)
    VALUES (v_pol_ret, 'APPROVE', COALESCE(v_approver, v_author), 'demo-seed-retention-approve');
  END IF;

  -- Institute-scoped published retention placeholders (two institutes when available)
  IF v_tenant_a IS NOT NULL AND NOT EXISTS (
      SELECT 1 FROM gov.policies WHERE code = 'DEMO-RET-INST-A' AND version_no = 1
  ) THEN
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, scope_tenant_id, effective_from, demo_label
    ) VALUES (
      gen_random_uuid(), 'RETENTION', 'DEMO-RET-INST-A',
      'Demo institute-A retention placeholder', 'DEMO ONLY', 'PUBLISHED', 1,
      v_author, 'INSTITUTE', v_tenant_a, now() - interval '1 day', 'DEMO ONLY'
    );
  END IF;

  IF v_tenant_b IS NOT NULL AND NOT EXISTS (
      SELECT 1 FROM gov.policies WHERE code = 'DEMO-RET-INST-B' AND version_no = 1
  ) THEN
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, scope_tenant_id, effective_from, demo_label
    ) VALUES (
      gen_random_uuid(), 'RETENTION', 'DEMO-RET-INST-B',
      'Demo institute-B retention placeholder', 'DEMO ONLY', 'PUBLISHED', 1,
      v_author, 'INSTITUTE', v_tenant_b, now() - interval '1 day', 'DEMO ONLY'
    );
  END IF;
END $$;

COMMIT;
