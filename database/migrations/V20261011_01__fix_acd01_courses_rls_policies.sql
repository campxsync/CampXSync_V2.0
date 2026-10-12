-- =============================================================================
-- Migration: V20261011_01__fix_acd01_courses_rls_policies.sql
-- Module: ACD-01 Course Management Service
-- Target Schema: acd (tables: courses, course_versions, course_prerequisites, course_program_map)
-- 
-- Description:
-- Corrects an RLS permission code mismatch in Supabase. The existing policies on
-- ACD-01 tables incorrectly checked 'acd03.*' (Subject Management) instead of
-- 'acd01.*' (Course Management). This caused non-admin users with acd01 permissions
-- to be blocked by RLS kernel enforcement when accessing courses.
--
-- Note: Do NOT execute on live Supabase until user review and authorization.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Table: acd.courses
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS courses_select ON acd.courses;
DROP POLICY IF EXISTS courses_insert ON acd.courses;
DROP POLICY IF EXISTS courses_update ON acd.courses;
DROP POLICY IF EXISTS courses_delete ON acd.courses;

CREATE POLICY courses_select ON acd.courses FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.view'::text)) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd01.view'::text)))) OR 
    (department_id IS NULL AND (SELECT iam.has_permission('acd01.view'::text)))
  )
);

CREATE POLICY courses_insert ON acd.courses FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.write'::text))))
  )
);

CREATE POLICY courses_update ON acd.courses FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.write'::text))))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.write'::text))))
  )
);

CREATE POLICY courses_delete ON acd.courses FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.manage'::text)) OR 
    (department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.manage'::text))))
  )
);

-- -----------------------------------------------------------------------------
-- 2. Table: acd.course_versions
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS course_versions_select ON acd.course_versions;
DROP POLICY IF EXISTS course_versions_insert ON acd.course_versions;
DROP POLICY IF EXISTS course_versions_update ON acd.course_versions;
DROP POLICY IF EXISTS course_versions_delete ON acd.course_versions;

CREATE POLICY course_versions_select ON acd.course_versions FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.view'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.view'::text, false)))
  )
);

CREATE POLICY course_versions_insert ON acd.course_versions FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
);

CREATE POLICY course_versions_update ON acd.course_versions FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
);

CREATE POLICY course_versions_delete ON acd.course_versions FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.manage'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.manage'::text, true)))
  )
);

-- -----------------------------------------------------------------------------
-- 3. Table: acd.course_prerequisites
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS course_prerequisites_select ON acd.course_prerequisites;
DROP POLICY IF EXISTS course_prerequisites_insert ON acd.course_prerequisites;
DROP POLICY IF EXISTS course_prerequisites_update ON acd.course_prerequisites;
DROP POLICY IF EXISTS course_prerequisites_delete ON acd.course_prerequisites;

CREATE POLICY course_prerequisites_select ON acd.course_prerequisites FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.view'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.view'::text, false)))
  )
);

CREATE POLICY course_prerequisites_insert ON acd.course_prerequisites FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
);

CREATE POLICY course_prerequisites_update ON acd.course_prerequisites FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
  )
);

CREATE POLICY course_prerequisites_delete ON acd.course_prerequisites FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.manage'::text)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.manage'::text, true)))
  )
);

-- -----------------------------------------------------------------------------
-- 4. Table: acd.course_program_map
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS course_program_map_select ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_insert ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_update ON acd.course_program_map;
DROP POLICY IF EXISTS course_program_map_delete ON acd.course_program_map;

CREATE POLICY course_program_map_select ON acd.course_program_map FOR SELECT TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.view'::text)) OR 
    (program_id IN (SELECT core.programs_in_scope('acd01.view'::text, false))) OR 
    (course_id IN (SELECT core.courses_in_scope('acd01.view'::text, false)))
  )
);

CREATE POLICY course_program_map_insert ON acd.course_program_map FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (
      (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true))) AND 
      (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
    )
  )
);

CREATE POLICY course_program_map_update ON acd.course_program_map FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (
      (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true))) AND 
      (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
    )
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.write'::text)) OR 
    (
      (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true))) AND 
      (course_id IN (SELECT core.courses_in_scope('acd01.write'::text, true)))
    )
  )
);

CREATE POLICY course_program_map_delete ON acd.course_program_map FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id())) AND 
  (
    (SELECT iam.perm_all('acd01.manage'::text)) OR 
    (
      (program_id IN (SELECT core.programs_in_scope('acd01.manage'::text, true))) AND 
      (course_id IN (SELECT core.courses_in_scope('acd01.manage'::text, true)))
    )
  )
);

COMMIT;
