-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 03 (PROPOSAL ONLY — DO NOT EXECUTE)
-- Migration Proposal: 03_minimum_grants_proposal.sql
-- Description: Establishes a strictly bounded, least-privilege grant matrix
--              for role 'campx_app_server' across student, core, academic,
--              shared audit, outbox, and scope helper functions.
--              Includes audit.events INSERT policy for Java Shared Services.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: PROPOSAL ONLY. Requires explicit owner review and written
--                   approval token before execution. DO NOT EXECUTE DIRECTLY.
-- ==============================================================================

DO $$
BEGIN
    -- 1. Schema USAGE Grants (Enables role to resolve schema qualification)
    GRANT USAGE ON SCHEMA core TO campx_app_server;
    GRANT USAGE ON SCHEMA stu TO campx_app_server;
    GRANT USAGE ON SCHEMA acd TO campx_app_server;
    GRANT USAGE ON SCHEMA iam TO campx_app_server;
    GRANT USAGE ON SCHEMA doc TO campx_app_server;
    GRANT USAGE ON SCHEMA audit TO campx_app_server;
    GRANT USAGE ON SCHEMA sys TO campx_app_server;

    -- 2. DOMAIN TABLE PRIVILEGE MODEL (Question 1 Resolution)
    -- Under the mandatory three-tier model, Java transactions switch immediately to 'authenticated'
    -- via 'SET LOCAL ROLE authenticated' before executing any query.
    -- Therefore, 'campx_app_server' does NOT require direct table DML privileges.
    -- Withholding direct DML from 'campx_app_server' enforces fail-closed behavior: any query
    -- executed outside of 'UserSecurityContext.applyClaimsAndRole()' fails immediately with SQLSTATE 42501.
    -- Direct table DML belongs exclusively to 'authenticated' (already granted on core tables in catalog).
    
    -- Ensure 'authenticated' has required transactional privileges on Tier 1 Core tables:
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE stu.students TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE stu.guardianships TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.persons TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE acd.batch_students TO authenticated;

    -- Ensure 'authenticated' has required transactional privileges on Tier 2 Extended tables:
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.addresses TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.person_contacts TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.person_emergency_contacts TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.person_identifiers TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE acd.section_enrollments TO authenticated;

    -- 3. Read-Only Academic & Institutional Reference Grants (authenticated)
    GRANT SELECT ON TABLE core.tenants TO authenticated;
    GRANT SELECT ON TABLE core.colleges TO authenticated;
    GRANT SELECT ON TABLE core.departments TO authenticated;
    GRANT SELECT ON TABLE core.academic_years TO authenticated;
    GRANT SELECT ON TABLE acd.programs TO authenticated;
    GRANT SELECT ON TABLE acd.batches TO authenticated;
    GRANT SELECT ON TABLE acd.course_sections TO authenticated;
    GRANT SELECT ON TABLE acd.calendar_terms TO authenticated;

    -- 4. Identity & RBAC Grants (authenticated)
    GRANT SELECT ON TABLE iam.roles TO authenticated;
    GRANT SELECT ON TABLE iam.permissions TO authenticated;
    GRANT SELECT ON TABLE iam.role_permissions TO authenticated;
    GRANT SELECT, INSERT, UPDATE ON TABLE iam.user_profiles TO authenticated;
    GRANT SELECT, INSERT, UPDATE ON TABLE iam.role_assignments TO authenticated;

    -- 5. Wave 1 Shared Services Foundation Grants (Audit & Transactional Outbox)
    -- Enforces append-only audit trail; UPDATE/DELETE/TRUNCATE strictly denied
    -- In catalog, audit.events only had SELECT granted to authenticated. We add missing INSERT:
    GRANT SELECT, INSERT ON TABLE audit.events TO authenticated;
    GRANT SELECT, INSERT ON TABLE audit.change_log TO authenticated;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO authenticated;
    GRANT SELECT, INSERT ON TABLE sys.dead_letter_events TO authenticated;

    -- Explicitly forbid destructive operations on audit ledger for all roles
    REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit.events FROM authenticated, campx_app_server;
    REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit.change_log FROM authenticated, campx_app_server;

    -- 7. Shared Services Audit INSERT Policy (Required when running as SET LOCAL ROLE authenticated)
    -- Guarantees Java atomic audit logging succeeds while strictly scoping inserted rows to current tenant
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'audit' AND tablename = 'events' AND policyname = 'events_insert'
    ) THEN
        CREATE POLICY events_insert ON audit.events FOR INSERT TO authenticated
        WITH CHECK (
            tenant_id = (SELECT core.current_tenant_id()) 
            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))
        );
        RAISE NOTICE 'Created missing audit.events_insert policy for authenticated role.';
    END IF;

    -- 8. Scope & Helper Function EXECUTE Grants
    -- Grants execution on the exact functions evaluated by PostgreSQL RLS policies
    DECLARE
        v_func text;
        v_funcs text[] := ARRAY[
            'core.current_tenant_id()',
            'core.current_person_id()',
            'core.current_student_id()',
            'core.my_student_ids()',
            'core.faculty_student_ids()',
            'core.students_in_scope(text, boolean)',
            'core.programs_in_scope(text, boolean)',
            'iam.has_permission(text)',
            'iam.perm_all(text)',
            'iam.perm_colleges(text)',
            'iam.perm_department_ids(text)'
        ];
    BEGIN
        FOREACH v_func IN ARRAY v_funcs LOOP
            IF to_regprocedure(v_func) IS NOT NULL THEN
                EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated, campx_app_server', v_func);
                RAISE NOTICE 'Granted EXECUTE on % to authenticated, campx_app_server', v_func;
            ELSE
                RAISE NOTICE 'Function % does not exist in catalog; skipped grant.', v_func;
            END IF;
        END LOOP;
    END;

    RAISE NOTICE 'Minimum least-privilege grants applied successfully for role campx_app_server.';
END $$;

