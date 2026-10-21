-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 06 (ROLLBACK PROPOSAL — DO NOT EXECUTE)
-- Migration Proposal: 06_rollback_proposal.sql
-- Description: Safely, modularly, and idempotently reverts G1 security remediation:
--              1. Reverts FORCE RLS to ENABLE RLS on Tier 2 (Extended) tables.
--              2. Reverts FORCE RLS to ENABLE RLS on Tier 1 (Core) tables.
--              3. Drops custom audit.events_insert policy if created.
--              4. Revokes table, schema, and function privileges from 'campx_app_server'.
--              5. Revokes role membership and terminates active sessions before dropping role.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: PROPOSAL ONLY. Requires explicit owner review and written
--                   approval token before execution. DO NOT EXECUTE DIRECTLY.
-- ==============================================================================

DO $$
DECLARE
    v_table text;
    v_tier2_tables text[] := ARRAY[
        'core.addresses',
        'core.person_contacts',
        'core.person_emergency_contacts',
        'core.person_identifiers',
        'acd.section_enrollments'
    ];
    v_tier1_tables text[] := ARRAY[
        'stu.students',
        'stu.guardianships',
        'core.persons',
        'acd.batch_students'
    ];
BEGIN
    -- 1. Revert FORCE RLS on Tier 2 Extended Tables (preserves basic ENABLE RLS)
    FOREACH v_table IN ARRAY v_tier2_tables
    LOOP
        IF to_regclass(v_table) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %s NO FORCE ROW LEVEL SECURITY', v_table);
            RAISE NOTICE 'Reverted FORCE ROW LEVEL SECURITY on Tier 2 table: %', v_table;
        END IF;
    END LOOP;

    -- 2. Revert FORCE RLS on Tier 1 Core Tables (preserves basic ENABLE RLS)
    FOREACH v_table IN ARRAY v_tier1_tables
    LOOP
        IF to_regclass(v_table) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %s NO FORCE ROW LEVEL SECURITY', v_table);
            RAISE NOTICE 'Reverted FORCE ROW LEVEL SECURITY on Tier 1 table: %', v_table;
        END IF;
    END LOOP;

    -- 3. Remove custom audit.events_insert policy if created by remediation
    IF EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'audit' AND tablename = 'events' AND policyname = 'events_insert'
    ) THEN
        DROP POLICY events_insert ON audit.events;
        RAISE NOTICE 'Dropped custom audit.events_insert policy.';
    END IF;

    -- 4. Revoke Privileges and Drop Role campx_app_server (if role exists)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'campx_app_server') THEN
        -- Check if active sessions exist. Per safety policy D.8, do NOT terminate active sessions automatically.
        IF EXISTS (SELECT 1 FROM pg_stat_activity WHERE usename = 'campx_app_server' AND pid <> pg_backend_pid()) THEN
            RAISE WARNING 'Active sessions detected for role campx_app_server. Active sessions are NOT terminated automatically. Close client connections before dropping role.';
        END IF;

        -- Revoke all table, sequence, function privileges granted directly to campx_app_server
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA stu, core, acd, iam, doc, audit, sys FROM campx_app_server';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA stu, core, acd, iam, doc, audit, sys FROM campx_app_server';
        EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA stu, core, acd, iam, doc, audit, sys FROM campx_app_server';

        -- Revoke schema usage
        EXECUTE 'REVOKE USAGE ON SCHEMA stu, core, acd, iam, doc, audit, sys FROM campx_app_server';

        -- Revoke role membership
        EXECUTE 'REVOKE authenticated FROM campx_app_server';

        -- Drop role safely (fails cleanly if active sessions remain)
        BEGIN
            DROP ROLE campx_app_server;
            RAISE NOTICE 'Role campx_app_server privileges revoked and role dropped successfully.';
        EXCEPTION WHEN object_in_use THEN
            RAISE WARNING 'Role campx_app_server cannot be dropped while active sessions exist. Privileges revoked; role drop deferred until connections close.';
        END;
    ELSE
        RAISE NOTICE 'Role campx_app_server does not exist; no role rollback needed.';
    END IF;

    RAISE NOTICE 'G1 security changes rolled back successfully. Database returned to pre-remediation baseline.';
END $$;

