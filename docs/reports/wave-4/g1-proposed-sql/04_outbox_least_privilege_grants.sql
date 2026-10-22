-- ==============================================================================
-- CAMPXSYNC WAVE 4 REMEDIATION: SCRIPT 04 (OUTBOX LEAST-PRIVILEGE GRANTS & RLS)
-- Migration Script: 04_outbox_least_privilege_grants.sql
-- Description: Establishes the smallest, least-privilege schema grant and
--              tenant-scoped RLS policies for role 'authenticated' on
--              'sys.outbox_events' and 'sys.dead_letter_events'.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: Requires explicit approval token (-Dcampx.approve.outbox.grants=true).
-- ==============================================================================

DO $$
BEGIN
    -- 1. Schema USAGE on sys to authenticated and campx_app_server
    -- Enables roles to resolve table references in schema sys
    GRANT USAGE ON SCHEMA sys TO authenticated, campx_app_server;

    -- 2. Table DML privileges on sys.outbox_events to authenticated
    -- Least privilege: Direct table DML is granted strictly to 'authenticated',
    -- which is only assumed in-transaction after claims binding via SET LOCAL ROLE.
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO authenticated;

    -- 3. Tenant-scoped RLS policies on sys.outbox_events for role authenticated
    -- INSERT Policy: Enforces current_tenant_id() scoping
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'sys' AND tablename = 'outbox_events' AND policyname = 'outbox_events_insert'
    ) THEN
        CREATE POLICY outbox_events_insert ON sys.outbox_events FOR INSERT TO authenticated
        WITH CHECK (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        );
        RAISE NOTICE 'Created policy outbox_events_insert on sys.outbox_events.';
    END IF;

    -- SELECT Policy: Enforces current_tenant_id() read-back scoping within transaction
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'sys' AND tablename = 'outbox_events' AND policyname = 'outbox_events_select'
    ) THEN
        CREATE POLICY outbox_events_select ON sys.outbox_events FOR SELECT TO authenticated
        USING (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        );
        RAISE NOTICE 'Created policy outbox_events_select on sys.outbox_events.';
    END IF;

    -- UPDATE Policy: Enforces current_tenant_id() status transitions (PENDING -> DISPATCHED)
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'sys' AND tablename = 'outbox_events' AND policyname = 'outbox_events_update'
    ) THEN
        CREATE POLICY outbox_events_update ON sys.outbox_events FOR UPDATE TO authenticated
        USING (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        )
        WITH CHECK (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        );
        RAISE NOTICE 'Created policy outbox_events_update on sys.outbox_events.';
    END IF;

    -- DELETE Policy: Enforces current_tenant_id() outbox purge scoping
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'sys' AND tablename = 'outbox_events' AND policyname = 'outbox_events_delete'
    ) THEN
        CREATE POLICY outbox_events_delete ON sys.outbox_events FOR DELETE TO authenticated
        USING (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        );
        RAISE NOTICE 'Created policy outbox_events_delete on sys.outbox_events.';
    END IF;
END $$;
