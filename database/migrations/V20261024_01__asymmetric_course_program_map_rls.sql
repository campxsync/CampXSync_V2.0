-- =============================================================================
-- Migration: V20261024_01__asymmetric_course_program_map_rls.sql
-- Module: ACD-01 Course Management Service
-- Target Schema: acd
-- Target Table: acd.course_program_map
-- Direction: Option C (Program-Driven Asymmetric Curriculum Membership)
--
-- Security Hardening:
-- 1. Program-driven authority: Caller must own target program (or hold perm_all).
-- 2. Tenant boundary: All rows strictly bound to core.current_tenant_id().
-- 3. Course validity & tenant confinement: Universal constraint applied to ALL
--    callers (including administrators holding perm_all) via the narrowly scoped
--    SECURITY DEFINER function core.is_active_course_in_tenant(uuid).
--    This ensures a program owner can map an active course from another department
--    in the same tenant without requiring direct SELECT privileges on acd.courses,
--    while strictly preventing cross-tenant or deleted course mappings.
-- 4. Symmetric visibility: SELECT preserves visibility for both program and course owners.
--
-- Note: Local preparation only. Do NOT execute on live database without explicit approval.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Narrowly Scoped Security-Definer Course Validity Helper
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION core.is_active_course_in_tenant(p_course_id uuid) 
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT EXISTS (
    SELECT 1 
    FROM acd.courses c
    WHERE c.id = p_course_id
      AND c.tenant_id = (SELECT core.current_tenant_id())
      AND c.deleted_at IS NULL
  );
$$;

REVOKE ALL ON FUNCTION core.is_active_course_in_tenant(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.is_active_course_in_tenant(uuid) TO authenticated, campx_app_server;

-- -----------------------------------------------------------------------------
-- 2. Table: acd.course_program_map
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS course_program_map_select ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_insert ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_update ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_delete ON acd.course_program_map;

-- 3. SELECT: Symmetric visibility
-- Program owners can view their curriculum; course owners can view programs utilizing their courses.
CREATE POLICY course_program_map_select ON acd.course_program_map 
FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.view'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.view'::text, false))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.view'::text, false)))
  )
);

-- 4. INSERT: Program-driven authority with universal course validity guard
-- Universal guards (tenant boundary + course validity in same tenant) cannot be bypassed by perm_all.
CREATE POLICY course_program_map_insert ON acd.course_program_map 
FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (core.is_active_course_in_tenant(course_id)) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true)))
  )
);

-- 5. UPDATE: Dual-boundary check
-- USING: Caller must have program write authority on the existing mapping.
-- WITH CHECK: Universal course validity guard + program write authority on destination program.
CREATE POLICY course_program_map_update ON acd.course_program_map 
FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true)))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (core.is_active_course_in_tenant(course_id)) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true)))
  )
);

-- 6. DELETE: Program owner controls curriculum removal
CREATE POLICY course_program_map_delete ON acd.course_program_map 
FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.manage'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.manage'::text, true)))
  )
);

COMMIT;
