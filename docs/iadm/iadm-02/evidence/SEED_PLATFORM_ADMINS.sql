-- Apply via Supabase MCP execute_sql (DEV). No passwords.
INSERT INTO plat.platform_admins (user_id, full_name, role_code, status)
SELECT '3121791f-df40-421f-8a97-f6f471c39250'::uuid, 'IADM-02 Super Admin B (DEV)', 'SUPER_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM plat.platform_admins p WHERE p.user_id = '3121791f-df40-421f-8a97-f6f471c39250'::uuid AND p.deleted_at IS NULL);

INSERT INTO plat.platform_admins (user_id, full_name, role_code, status)
SELECT '7c8e1f7a-341c-48c5-ba27-da5aac81d150'::uuid, 'IADM-02 System Admin (DEV)', 'SYSTEM_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM plat.platform_admins p WHERE p.user_id = '7c8e1f7a-341c-48c5-ba27-da5aac81d150'::uuid AND p.deleted_at IS NULL);
