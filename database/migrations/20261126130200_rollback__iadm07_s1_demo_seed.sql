-- Rollback: 20261126130200_rollback__iadm07_s1_demo_seed.sql
-- Plain language: remove DEMO ONLY IADM-07 seed rows.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM sec.enforcement_status WHERE demo_label ILIKE 'DEMO%' OR correlation_id LIKE 'SEC-%';
DELETE FROM sec.alerts WHERE code LIKE 'SEC-ALERT-%';
DELETE FROM sec.exceptions WHERE code LIKE 'SEC-EXC-%';
DELETE FROM sec.evidence_refs WHERE id = 'a7000006-0000-4000-8000-000000000001'::uuid;
DELETE FROM sec.control_assessments WHERE control_id = 'a7000005-0000-4000-8000-000000000001'::uuid;
DELETE FROM sec.controls WHERE code = 'SEC-CTRL-001';
DELETE FROM sec.risks WHERE code = 'SEC-RISK-001';
DELETE FROM sec.findings WHERE code = 'SEC-FIND-001';
DELETE FROM sec.signals WHERE correlation_id = 'SEC-SRC-STALE';
DELETE FROM sec.security_standards WHERE code IN ('SEC-STD-ACCESS', 'SEC-STD-LOGGING');

COMMIT;
