-- Rollback IADM-02 cutover FORCE RLS on cfg.policies (DEV).

BEGIN;

ALTER TABLE cfg.policies NO FORCE ROW LEVEL SECURITY;

COMMIT;
