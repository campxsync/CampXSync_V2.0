-- IADM-01 TECH-003: allow authenticated session role to call durable audit writer.
-- Plain language: Java runs as SET LOCAL ROLE authenticated; audit.log_event is SECURITY DEFINER
-- and writes audit.events. Without EXECUTE grant, mutations fail closed after business write.
-- Rollback: 20261122010300_rollback__iadm01_grant_audit_log_event.sql

GRANT EXECUTE ON FUNCTION audit.log_event(uuid, uuid, text, text, uuid, text, text, inet, jsonb, uuid)
  TO authenticated, service_role;
