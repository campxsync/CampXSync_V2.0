-- =============================================================================
-- Migration Rollback: V20261024_01_rollback__revert_asymmetric_course_program_map_rls.sql
-- Module: ACD-01 Course Management Service
-- Target Schema: acd
-- Target Table: acd.course_program_map
--
-- Description:
-- Reverts acd.course_program_map policies back to the verified pre-migration baseline
-- captured directly from live development catalog (project azulbaxszsqxymqmlwfe pg_policies).
-- The baseline uses symmetric OR logic and legacy 'acd03.*' permission expressions:
--   - course_program_map_select: acd03.view (symmetric OR between program & course)
--   - course_program_map_insert: acd03.write (symmetric OR between program & course)
--   - course_program_map_update: acd03.write (symmetric OR on USING and WITH CHECK)
--   - course_program_map_delete: acd03.manage (symmetric OR between program & course)
-- Cleans up helper function core.is_active_course_in_tenant(uuid).
--
-- Note: Local preparation only. Do NOT execute on live database without explicit approval.
-- =============================================================================

BEGIN;

DROP POLICY IF EXISTS course_program_map_select ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_insert ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_update ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_delete ON acd.course_program_map;

-- 1. SELECT: Captured live baseline with acd03.view
CREATE POLICY course_program_map_select ON acd.course_program_map FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd03.view'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.view'::text, false))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.view'::text, false)))
  )
);

-- 2. INSERT: Captured live baseline with acd03.write and symmetric OR
CREATE POLICY course_program_map_insert ON acd.course_program_map FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true)))
  )
);

-- 3. UPDATE: Captured live baseline with acd03.write and symmetric OR
CREATE POLICY course_program_map_update ON acd.course_program_map FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true)))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true)))
  )
);

-- 4. DELETE: Captured live baseline with acd03.manage and symmetric OR
CREATE POLICY course_program_map_delete ON acd.course_program_map FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd03.manage'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.manage'::text, true))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.manage'::text, true)))
  )
);

-- 5. Drop helper function created in forward migration
DROP FUNCTION IF EXISTS core.is_active_course_in_tenant(uuid);

COMMIT;

