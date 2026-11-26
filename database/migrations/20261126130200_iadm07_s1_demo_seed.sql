-- Migration: 20261126130200_iadm07_s1_demo_seed.sql
-- Plain language: DEMO ONLY seed rows for IADM-07 screens (IADM-07-US-028).
-- Reuses IADM04_DEMO and MAC institute IDs. No real personal data.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- Platform published standard + review-due + draft for lifecycle demos
INSERT INTO sec.security_standards (id, code, title, version_no, status, body_ref, author_user_id, approver_user_id, effective_at, review_due_at, demo_label, created_at, updated_at)
SELECT 'a7000001-0000-4000-8000-000000000001'::uuid, 'SEC-STD-ACCESS', 'Access Control Baseline', 1, 'PUBLISHED',
       'ref://demo/standards/access-v1', '00000000-0000-0000-0000-0000000000a2'::uuid, '00000000-0000-0000-0000-0000000000a1'::uuid,
       now() - interval '30 days', now() - interval '1 day', 'DEMO ONLY', now() - interval '40 days', now() - interval '30 days'
WHERE NOT EXISTS (SELECT 1 FROM sec.security_standards WHERE code = 'SEC-STD-ACCESS' AND version_no = 1);

INSERT INTO sec.security_standards (id, code, title, version_no, status, body_ref, author_user_id, demo_label, created_at, updated_at)
SELECT 'a7000001-0000-4000-8000-000000000002'::uuid, 'SEC-STD-LOGGING', 'Security Logging Standard', 1, 'DRAFT',
       'ref://demo/standards/logging-draft', '00000000-0000-0000-0000-0000000000a2'::uuid, 'DEMO ONLY', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM sec.security_standards WHERE code = 'SEC-STD-LOGGING' AND version_no = 1);

-- Stale register case: signal with old updated_at (SEC-SRC-STALE)
INSERT INTO sec.signals (id, institute_id, correlation_id, source, source_demo_only, severity, status, summary, created_at, updated_at)
SELECT 'a7000002-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-SRC-STALE', 'DEMO_MONITOR', true, 'HIGH', 'NEW',
       'Deliberately stale signal for posture demo', now() - interval '90 days', now() - interval '90 days'
WHERE NOT EXISTS (SELECT 1 FROM sec.signals WHERE correlation_id = 'SEC-SRC-STALE');

-- Finding SEC-FIND-001 assigned to IADM04_DEMO
INSERT INTO sec.findings (id, institute_id, code, title, severity, status, assigned_institute_id, target_date, sla_value, created_by, demo_label, created_at, updated_at)
SELECT 'a7000003-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-FIND-001', 'Unpatched demo endpoint', 'HIGH', 'ASSIGNED',
       'd4000000-0000-4000-8000-000000000004'::uuid, CURRENT_DATE - 3, NULL,
       '00000000-0000-0000-0000-0000000000a2'::uuid, 'DEMO ONLY', now() - interval '10 days', now() - interval '2 days'
WHERE NOT EXISTS (SELECT 1 FROM sec.findings WHERE code = 'SEC-FIND-001');

-- Risk SEC-RISK-001
INSERT INTO sec.risks (id, institute_id, code, title, status, severity, demo_label, created_by, created_at, updated_at)
SELECT 'a7000004-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-RISK-001', 'Elevated access concentration', 'ASSESSED', 'MEDIUM', 'DEMO ONLY',
       '00000000-0000-0000-0000-0000000000a2'::uuid, now() - interval '7 days', now() - interval '1 day'
WHERE NOT EXISTS (SELECT 1 FROM sec.risks WHERE code = 'SEC-RISK-001');

-- Control SEC-CTRL-001 + assessments
INSERT INTO sec.controls (id, code, title, framework_text, demo_label, created_at, updated_at)
SELECT 'a7000005-0000-4000-8000-000000000001'::uuid, 'SEC-CTRL-001', 'MFA for privileged accounts',
       'DEMO framework: Access Hygiene', 'DEMO ONLY', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM sec.controls WHERE code = 'SEC-CTRL-001');

INSERT INTO sec.control_assessments (id, control_id, institute_id, status, created_at, updated_at)
SELECT 'a7000005-0000-4000-8000-000000000011'::uuid, 'a7000005-0000-4000-8000-000000000001'::uuid,
       'd4000000-0000-4000-8000-000000000004'::uuid, 'EVIDENCE_PENDING', now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM sec.control_assessments
  WHERE control_id = 'a7000005-0000-4000-8000-000000000001'::uuid
    AND institute_id = 'd4000000-0000-4000-8000-000000000004'::uuid
);

INSERT INTO sec.control_assessments (id, control_id, institute_id, status, created_at, updated_at)
SELECT 'a7000005-0000-4000-8000-000000000012'::uuid, 'a7000005-0000-4000-8000-000000000001'::uuid,
       '72ecbfe3-2077-41b5-8a20-b6d3e045311b'::uuid, 'NOT_ASSESSED', now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM sec.control_assessments
  WHERE control_id = 'a7000005-0000-4000-8000-000000000001'::uuid
    AND institute_id = '72ecbfe3-2077-41b5-8a20-b6d3e045311b'::uuid
);

-- Evidence pending reference
INSERT INTO sec.evidence_refs (id, institute_id, linked_type, linked_id, reference_address, source, recorded_at, created_by, demo_label)
SELECT 'a7000006-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'FINDING', 'a7000003-0000-4000-8000-000000000001'::uuid,
       'ref://demo/evidence/pending-001', 'MANUAL', now(), '00000000-0000-0000-0000-0000000000a2'::uuid, 'DEMO ONLY — evidence pending'
WHERE NOT EXISTS (SELECT 1 FROM sec.evidence_refs WHERE id = 'a7000006-0000-4000-8000-000000000001'::uuid);

-- Exception SEC-EXC-001 pending + one expiring
INSERT INTO sec.exceptions (id, institute_id, code, rationale, linked_finding_id, status, valid_from, valid_to, requester_user_id, demo_label, created_at, updated_at)
SELECT 'a7000007-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-EXC-001', 'Temporary compensating control while remediating SEC-FIND-001',
       'a7000003-0000-4000-8000-000000000001'::uuid, 'REQUESTED',
       now(), now() + interval '30 days', '00000000-0000-0000-0000-0000000000a3'::uuid, 'DEMO ONLY', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM sec.exceptions WHERE code = 'SEC-EXC-001');

INSERT INTO sec.exceptions (id, institute_id, code, rationale, status, valid_from, valid_to, requester_user_id, decider_user_id, decision_rationale, demo_label, created_at, updated_at)
SELECT 'a7000007-0000-4000-8000-000000000002'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-EXC-EXPIRING', 'Approved exception approaching expiry',
       'APPROVED', now() - interval '25 days', now() + interval '2 days',
       '00000000-0000-0000-0000-0000000000a3'::uuid, '00000000-0000-0000-0000-0000000000a1'::uuid,
       'Approved with short window', 'DEMO ONLY', now() - interval '25 days', now() - interval '25 days'
WHERE NOT EXISTS (SELECT 1 FROM sec.exceptions WHERE code = 'SEC-EXC-EXPIRING');

-- Alert SEC-ALERT-001
INSERT INTO sec.alerts (id, institute_id, code, title, severity, status, demo_label, created_at, updated_at)
SELECT 'a7000008-0000-4000-8000-000000000001'::uuid, 'd4000000-0000-4000-8000-000000000004'::uuid,
       'SEC-ALERT-001', 'Repeated privileged denial pattern (demo)', 'HIGH', 'OPEN', 'DEMO ONLY',
       now() - interval '1 day', now() - interval '1 day'
WHERE NOT EXISTS (SELECT 1 FROM sec.alerts WHERE code = 'SEC-ALERT-001');

-- Empty case: MAC institute has no findings (deliberate empty) — nothing to insert

COMMIT;
