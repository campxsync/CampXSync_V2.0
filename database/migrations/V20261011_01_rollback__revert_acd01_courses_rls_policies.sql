-- =============================================================================
-- Migration Rollback: V20261011_01_rollback__revert_acd01_courses_rls_policies.sql
-- Module: ACD-01 Course Management Service
-- Target Schema: acd (tables: courses, course_versions, course_prerequisites, course_program_map)
--
-- Provenance & Evidence:
-- Reconstructed from the live PostgreSQL pg_policies system catalog snapshot captured
-- during pre-migration inspection on 2026-10-11T11:45:55Z (Step 1835 in conversation logs).
--
-- Purpose:
-- Emergency rollback artifact to revert all 16 Row-Level Security policies on ACD-01
-- tables back to their exact pre-migration baseline state if V20261011_01 fails or causes
-- regressions in staging.
--
-- Affected Policies (16 total):
--   1. acd.courses:               courses_select, courses_insert, courses_update, courses_delete
--   2. acd.course_versions:       course_versions_select, course_versions_insert, course_versions_update, course_versions_delete
--   3. acd.course_prerequisites:  course_prerequisites_select, course_prerequisites_insert, course_prerequisites_update, course_prerequisites_delete
--   4. acd.course_program_map:    course_program_map_select, course_program_map_insert, course_program_map_update, course_program_map_delete
--
-- Restored Semantics:
--   - Reverts permission namespace checks from 'acd01.*' back to the previous 'acd03.*' references.
--   - Reverts acd.course_program_map mutating policies back to the previous OR-based disjunction.
--   - Restores the previous '((NOT true) AND (department_id IS NULL) AND ...)' expressions on acd.courses mutations.
--
-- Disclaimer:
-- This rollback script has been reconstructed purely from captured pg_policies evidence.
-- It has NOT been executed or validated against any live database environment.
-- Do not execute without explicit authorization and a preceding pre-flight backup.
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
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.view'::text)) AS unnest)) OR 
    ((NOT false) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.view'::text) AS has_permission))
  )
);

CREATE POLICY courses_insert ON acd.courses FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.write'::text)) AS unnest)) OR 
    ((NOT true) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.write'::text) AS has_permission))
  )
);

CREATE POLICY courses_update ON acd.courses FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.write'::text)) AS unnest)) OR 
    ((NOT true) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.write'::text) AS has_permission))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.write'::text)) AS unnest)) OR 
    ((NOT true) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.write'::text) AS has_permission))
  )
);

CREATE POLICY courses_delete ON acd.courses FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR 
    (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.manage'::text)) AS unnest)) OR 
    ((NOT true) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.manage'::text) AS has_permission))
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
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope))
  )
);

CREATE POLICY course_versions_insert ON acd.course_versions FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_versions_update ON acd.course_versions FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_versions_delete ON acd.course_versions FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope))
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
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope))
  )
);

CREATE POLICY course_prerequisites_insert ON acd.course_prerequisites FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_prerequisites_update ON acd.course_prerequisites FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_prerequisites_delete ON acd.course_prerequisites FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope))
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
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.view'::text, false) AS programs_in_scope)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope))
  )
);

CREATE POLICY course_program_map_insert ON acd.course_program_map FOR INSERT TO authenticated
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true) AS programs_in_scope)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_program_map_update ON acd.course_program_map FOR UPDATE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true) AS programs_in_scope)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
)
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.write'::text, true) AS programs_in_scope)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))
  )
);

CREATE POLICY course_program_map_delete ON acd.course_program_map FOR DELETE TO authenticated
USING (
  (tenant_id = (SELECT core.current_tenant_id() AS current_tenant_id)) AND 
  (
    (SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR 
    (program_id IN (SELECT core.programs_in_scope('acd03.manage'::text, true) AS programs_in_scope)) OR 
    (course_id IN (SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope))
  )
);

COMMIT;
