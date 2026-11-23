-- IADM-02 cutover step 4: FORCE RLS on cfg.policies (DEV).
-- Writers of platform policies are retired at the gateway; FORCE closes table-owner bypass.
-- Rollback: 20261123010300_rollback__iadm02_cutover_force_policies.sql

BEGIN;

ALTER TABLE cfg.policies FORCE ROW LEVEL SECURITY;

COMMIT;
