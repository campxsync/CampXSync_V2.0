-- Rollback grant of audit.log_event to authenticated (keeps service_role if previously held).

REVOKE EXECUTE ON FUNCTION audit.log_event(uuid, uuid, text, text, uuid, text, text, inet, jsonb, uuid)
  FROM authenticated;
