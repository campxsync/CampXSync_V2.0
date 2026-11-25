-- Migration: 20261125180200_iadm06_catalogue_and_policy_seed.sql
-- Plain language: seed M01–M10 catalogue (US-023) + DEMO ONLY IADM-05 analytics policies (US-021 data, not code).
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- ---- Metric catalogue M01–M10 (catalogue version 1.0) ----
INSERT INTO anl.metric_catalogue (
  metric_code, metric_name, plain_definition, source_owner, grain, time_windows,
  visibility_roles, small_group_rule, catalogue_version, demo_label
) VALUES
('M01','Institutes by lifecycle status','Count of institutes in each lifecycle status as stored by the source','IADM-01','Platform; status','Current snapshot','R01,R02,R28; R03 own institute','Suppress grouped result below threshold','1.0',NULL),
('M02','Total institutes','Count of institutes in the authorized scope','IADM-01','Platform or own institute','Current snapshot','R01,R02,R28; R03 own institute','Suppress comparison groups below threshold','1.0',NULL),
('M03','Colleges per institute','Count of colleges belonging to each institute','IADM-01','Institute','Current snapshot','R01,R02,R28; R03 own institute','Suppress comparison groups below threshold','1.0',NULL),
('M04','Colleges by lifecycle status','Count of colleges in each lifecycle status as stored by the source','IADM-01','Institute; status','Current snapshot','R01,R02,R28; R03 own institute','Suppress grouped result below threshold','1.0',NULL),
('M05','Institutes created','Count of institutes created within the selected time window','IADM-01','Platform','Day, month, quarter','R01,R02,R28; R03 own institute','Suppress time/dimension groups below threshold','1.0',NULL),
('M06','Colleges created','Count of colleges created within the selected time window','IADM-01','Institute; platform aggregate','Day, month, quarter','R01,R02,R28; R03 own institute','Suppress time/dimension groups below threshold','1.0',NULL),
('M07','Subscriptions by plan','Count of subscriptions associated with each plan','IADM-04','Platform; plan','Current snapshot','R01,R02,R28; R03 own institute','Suppress plan groups below threshold','1.0','DEMO values'),
('M08','Subscriptions by state','Count of subscriptions by source state','IADM-04','Platform; subscription state','Current snapshot','R01,R02,R28; R03 own institute','Suppress state groups below threshold','1.0','DEMO values'),
('M09','Entitlement consumption versus limit','Consumption compared with the applicable entitlement limit','IADM-04','Institute/subscription; entitlement','Current snapshot','R01,R02,R28; R03 own institute','Suppress comparison groups; no subtraction reconstruction','1.0','DEMO values'),
('M10','Subscription state trend','Change in counts of source subscription states over a selected time window','IADM-04','Platform; state; time bucket','Day, month, quarter','R01,R02,R28; R03 own institute','Suppress time/dimension groups below threshold','1.0','DEMO values')
ON CONFLICT (metric_code, catalogue_version) DO NOTHING;

-- DEMO ONLY adoption fixture row (deferred metrics) — never presented as real
INSERT INTO anl.metric_catalogue (
  metric_code, metric_name, plain_definition, source_owner, grain, time_windows,
  visibility_roles, small_group_rule, catalogue_version, demo_label
) VALUES (
  'DEMO-ADOPT','DEMO ONLY adoption fixture',
  'Illustrative deferred adoption metric — NOT a verified domain source',
  'DEMO_ONLY','DEMO','N/A','R01 demo only','N/A','1.0','DEMO ONLY — not real adoption/usage'
) ON CONFLICT (metric_code, catalogue_version) DO NOTHING;

-- ---- DEMO ONLY published IADM-05 policies for analytics domain (data lifecycle, not IADM-05 code) ----
-- Exactly one PUBLISHED RETENTION with domain_code=ANALYTICS for data-handling gate.
-- Exactly one PUBLISHED EXPORT policy for export gate.

DO $$
DECLARE
  v_author UUID;
  v_ret UUID := gen_random_uuid();
  v_exp UUID := gen_random_uuid();
BEGIN
  SELECT user_id INTO v_author FROM plat.platform_admins
   WHERE role_code = 'SUPER_ADMIN' AND deleted_at IS NULL LIMIT 1;
  IF v_author IS NULL THEN
    v_author := '00000000-0000-0000-0000-000000000001';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM gov.policies p
    JOIN gov.retention_rules r ON r.policy_id = p.id
    WHERE p.policy_type = 'RETENTION' AND p.status = 'PUBLISHED' AND r.domain_code = 'ANALYTICS'
  ) THEN
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, demo_label, effective_from
    ) VALUES (
      v_ret, 'RETENTION', 'DEMO-ANALYTICS-HANDLING',
      'DEMO ONLY analytics data-handling policy',
      'IADM-06 preflight seed', 'PUBLISHED', 1,
      v_author, 'PLATFORM', 'DEMO ONLY', now()
    );
    INSERT INTO gov.retention_rules (
      policy_id, domain_code, record_category, scope_note, trigger_note,
      rule_reference, duration_note, hold_handling, demo_label
    ) VALUES (
      v_ret, 'ANALYTICS', 'AGGREGATE_METRICS',
      'Platform analytics aggregates', 'On dataset release',
      'TBC', 'TBC', 'TBC',
      'DEMO ONLY — not a privacy standard'
    );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM gov.policies
    WHERE policy_type = 'EXPORT' AND status = 'PUBLISHED' AND code = 'DEMO-ANALYTICS-EXPORT'
  ) THEN
    INSERT INTO gov.policies (
      id, policy_type, code, title, owner_label, status, version_no,
      author_user_id, tenant_scope, demo_label, effective_from
    ) VALUES (
      v_exp, 'EXPORT', 'DEMO-ANALYTICS-EXPORT',
      'DEMO ONLY analytics aggregate export policy',
      'IADM-06 preflight seed', 'PUBLISHED', 1,
      v_author, 'PLATFORM', 'DEMO ONLY', now()
    );
  END IF;
END $$;

COMMIT;
