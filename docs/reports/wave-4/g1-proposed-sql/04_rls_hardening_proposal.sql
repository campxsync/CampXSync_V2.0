-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 04 (PROPOSAL ONLY — DO NOT EXECUTE)
-- Migration Proposal: 04_rls_hardening_proposal.sql
-- Description: Applies FORCE ROW LEVEL SECURITY across Student Module tables.
--              Decoupled into Tier 1 (Core Approval Scope: 4 tables) and
--              Tier 2 (Extended Scope: 5 child tables).
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: PROPOSAL ONLY. Requires explicit owner review and written
--                   approval token before execution. DO NOT EXECUTE DIRECTLY.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- BLOCK 1: TIER 1 CORE APPROVAL SCOPE (4 TABLES)
-- Governed by Decision DEC-G1-02A. Required for minimal Wave 4 Student roster.
-- ------------------------------------------------------------------------------
DO $$
DECLARE
    v_table text;
    v_core_tables text[] := ARRAY[
        'stu.students',           -- Core student academic anchor record
        'core.persons',           -- Base human demographic supertype
        'stu.guardianships',      -- Parental/guardian legal relationships
        'acd.batch_students'      -- Academic cohort batch membership & roll number
    ];
BEGIN
    RAISE NOTICE 'Starting Tier 1 Core RLS Hardening (4 tables)...';
    FOREACH v_table IN ARRAY v_core_tables
    LOOP
        IF to_regclass(v_table) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
            EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', v_table);
            RAISE NOTICE 'Applied ENABLE and FORCE ROW LEVEL SECURITY on Tier 1 table: %', v_table;
        ELSE
            RAISE EXCEPTION 'Precondition Failed: Core table % does not exist in catalog', v_table;
        END IF;
    END LOOP;
    RAISE NOTICE 'Tier 1 Core RLS Hardening completed successfully.';
END $$;

-- ------------------------------------------------------------------------------
-- BLOCK 2: TIER 2 EXTENDED SCOPE (5 CHILD TABLES)
-- Governed by Separate Decision DEC-G1-02B.
-- Demographic children of core.persons and academic section enrollments.
-- Requires separate explicit approval token APPROVE-G1-FORCE-RLS-TIER2.
-- ------------------------------------------------------------------------------
DO $$
DECLARE
    v_table text;
    v_extended_tables text[] := ARRAY[
        'core.addresses',                   -- Postal addresses (permanent/current)
        'core.person_contacts',             -- Phone, email, alternate contact channels
        'core.person_emergency_contacts',   -- Emergency contact details
        'core.person_identifiers',          -- Statutory government identifiers (Aadhaar, PAN)
        'acd.section_enrollments'           -- Timetable course section assignments
    ];
BEGIN
    RAISE NOTICE 'Starting Tier 2 Extended RLS Hardening (5 tables)...';
    FOREACH v_table IN ARRAY v_extended_tables
    LOOP
        IF to_regclass(v_table) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
            EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', v_table);
            RAISE NOTICE 'Applied ENABLE and FORCE ROW LEVEL SECURITY on Tier 2 table: %', v_table;
        ELSE
            RAISE EXCEPTION 'Precondition Failed: Extended table % does not exist in catalog', v_table;
        END IF;
    END LOOP;
    RAISE NOTICE 'Tier 2 Extended RLS Hardening completed successfully.';
END $$;

