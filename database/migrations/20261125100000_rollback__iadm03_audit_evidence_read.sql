-- Rollback: 20261125100000_iadm03_audit_evidence_read.sql
BEGIN;
DROP FUNCTION IF EXISTS audit.list_iadm03_evidence(integer);
COMMIT;
