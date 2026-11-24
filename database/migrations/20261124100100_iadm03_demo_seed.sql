-- Migration: 20261124100100_iadm03_demo_seed.sql
-- Plain language: IADM-03 US-007 DEV demo fixture map — reuse IADM-01 MAC institute
-- and existing personas. Does not create Auth passwords. Does not touch production.
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Owner: S0 decision pack APPROVED 2026-11-24

BEGIN;

-- Ensure SYSTEM_ADMIN tenant role exists where INSTITUTION_ADMIN exists (for R02 ceiling tests)
INSERT INTO iam.roles (tenant_id, code, name)
SELECT DISTINCT r.tenant_id, 'SYSTEM_ADMIN', 'System Admin'
FROM iam.roles r
WHERE r.deleted_at IS NULL
  AND r.code = 'INSTITUTION_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM iam.roles x
    WHERE x.tenant_id = r.tenant_id AND x.code = 'SYSTEM_ADMIN' AND x.deleted_at IS NULL
  );

-- Re-propagate iam.* keys to SYSTEM_ADMIN / INSTITUTION_ADMIN / COLLEGE_ADMIN / AUDITOR
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iam.%'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.tenant_id = r.tenant_id
  );

-- Marker comment only: reserved unprofiled invitee email for first-R03 flow demos
-- iadm03.invitee.unprofiled@campxsync.test — must NOT exist in iam.user_profiles
-- (enforced by verification script; no Auth user created here)

COMMIT;
