-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 02 (PROPOSAL ONLY — DO NOT EXECUTE)
-- Migration Proposal: 02_application_role_proposal.sql
-- Description: Creates the dedicated least-privilege application login role
--              'campx_app_server' without BYPASSRLS or superuser privileges.
--              Grants membership in 'authenticated' to enable 'SET LOCAL ROLE'.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: PROPOSAL ONLY. Requires explicit owner review and written
--                   approval token DEC-G1-01 before execution. DO NOT EXECUTE.
-- Credential Policy: Passwords MUST NOT be committed to version control.
--                    The password placeholder below must be provided dynamically
--                    via secure vault, environment variable, or interactive prompt.
-- ==============================================================================

DO $$
BEGIN
    -- 1. Check if application role already exists
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'campx_app_server') THEN
        -- Create unprivileged application login role
        -- Note: Password MUST be set via secure environment secret at deployment time
        -- e.g., in Supabase Dashboard / Vault: ALTER ROLE campx_app_server WITH PASSWORD '...';
        CREATE ROLE campx_app_server WITH
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOREPLICATION
            NOBYPASSRLS
            INHERIT
            CONNECTION LIMIT 25;

        RAISE NOTICE 'Created application login role: campx_app_server (NOBYPASSRLS, NOSUPERUSER)';
    ELSE
        -- Ensure role attributes conform to least-privilege invariants
        -- In PostgreSQL 16+, non-superuser roles (even with CREATEROLE) cannot alter SUPERUSER or REPLICATION attributes.
        ALTER ROLE campx_app_server WITH
            NOCREATEDB
            NOCREATEROLE
            NOBYPASSRLS
            INHERIT
            CONNECTION LIMIT 25;

        RAISE NOTICE 'Role campx_app_server already exists; refreshed least-privilege invariants (NOBYPASSRLS).';
    END IF;

    -- 2. Grant membership in the standard 'authenticated' runtime role
    -- MANDATORY INVARIANT: PostgreSQL requires the session user to be an explicit member
    -- of 'authenticated' in order to execute 'SET LOCAL ROLE authenticated' inside transactions.
    -- Without this grant, HikariCP transaction checkout fails with:
    -- "ERROR: 42501: must be member of role 'authenticated'"
    GRANT authenticated TO campx_app_server;

    -- 3. Explicitly forbid role from owning application tables or creating objects in schemas
    -- (campx_app_server must remain an unprivileged consumer, never a schema owner)
    REVOKE ALL ON SCHEMA public FROM campx_app_server;
    REVOKE CREATE ON SCHEMA core, stu, acd, iam, doc, audit, sys FROM campx_app_server;

    RAISE NOTICE 'Membership in role authenticated granted to campx_app_server.';
END $$;

-- 4. Set secure default search_path for the application role
ALTER ROLE campx_app_server SET search_path = core, stu, acd, iam, doc, audit, sys, public;

-- 5. Set session parameter invariants
-- Enforces that statement timeout prevents runaway queries from consuming pool capacity
ALTER ROLE campx_app_server SET statement_timeout = '15s';
ALTER ROLE campx_app_server SET idle_in_transaction_session_timeout = '30s';

