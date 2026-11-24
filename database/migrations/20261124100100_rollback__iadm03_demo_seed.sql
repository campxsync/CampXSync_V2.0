-- Rollback: 20261124100100_rollback__iadm03_demo_seed.sql
-- DEV only. Does not remove SYSTEM_ADMIN roles (may be shared with IADM-02).

BEGIN;
-- No destructive cleanup; foundation rollback handles permission keys.
COMMIT;
