-- =============================================================================
-- Migration: 20261105000100_stm01_student_ownership_rls.sql
-- Module: STM-01 Student Identity & Self-Service
-- Description: Scoped ownership RLS policies and fail-closed BEFORE UPDATE guard trigger for
--              student self-service profile corrections, address updates, and audit history.
-- Safety: Pure additive ownership scoping. Staff/admin/service workflows completely unaffected.
-- Target Database: Development only (azulbaxszsqxymqmlwfe)
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. doc.lifecycle_requests: Scoped Student Ownership Policies
-- -----------------------------------------------------------------------------

DROP POLICY IF EXISTS lifecycle_requests_student_select ON doc.lifecycle_requests;
CREATE POLICY lifecycle_requests_student_select ON doc.lifecycle_requests
FOR SELECT TO authenticated
USING (
  tenant_id = (SELECT core.current_tenant_id())
  AND entity_type = 'STUDENT'
  AND entity_id IN (SELECT core.my_student_ids())
  AND request_type = 'CORRECTION'
);

DROP POLICY IF EXISTS lifecycle_requests_student_insert ON doc.lifecycle_requests;
CREATE POLICY lifecycle_requests_student_insert ON doc.lifecycle_requests
FOR INSERT TO authenticated
WITH CHECK (
  tenant_id = (SELECT core.current_tenant_id())
  AND entity_type = 'STUDENT'
  AND entity_id IN (SELECT core.my_student_ids())
  AND request_type = 'CORRECTION'
  AND requested_by = auth.uid()
  AND status = 'PENDING'
  AND completed_at IS NULL
  AND workflow_instance_id IS NULL
);

-- -----------------------------------------------------------------------------
-- 2. core.addresses: Scoped Student Ownership Policies
-- -----------------------------------------------------------------------------

DROP POLICY IF EXISTS addresses_student_select ON core.addresses;
CREATE POLICY addresses_student_select ON core.addresses
FOR SELECT TO authenticated
USING (
  tenant_id = (SELECT core.current_tenant_id())
  AND (
    created_by = auth.uid()
    OR id IN (
      SELECT permanent_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
      UNION
      SELECT current_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
    )
  )
);

DROP POLICY IF EXISTS addresses_student_insert ON core.addresses;
CREATE POLICY addresses_student_insert ON core.addresses
FOR INSERT TO authenticated
WITH CHECK (
  tenant_id = (SELECT core.current_tenant_id())
  AND created_by = auth.uid()
);

DROP POLICY IF EXISTS addresses_student_update ON core.addresses;
CREATE POLICY addresses_student_update ON core.addresses
FOR UPDATE TO authenticated
USING (
  tenant_id = (SELECT core.current_tenant_id())
  AND (
    created_by = auth.uid()
    OR id IN (
      SELECT permanent_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
      UNION
      SELECT current_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
    )
  )
)
WITH CHECK (
  tenant_id = (SELECT core.current_tenant_id())
  AND (
    created_by = auth.uid()
    OR id IN (
      SELECT permanent_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
      UNION
      SELECT current_address_id FROM core.persons WHERE id = (SELECT core.current_person_id())
    )
  )
);

-- -----------------------------------------------------------------------------
-- 3. core.persons: Narrow UPDATE Policy + Fail-Closed Guard Trigger
-- -----------------------------------------------------------------------------

DROP POLICY IF EXISTS persons_student_update ON core.persons;
CREATE POLICY persons_student_update ON core.persons
FOR UPDATE TO authenticated
USING (
  tenant_id = (SELECT core.current_tenant_id())
  AND id = (SELECT core.current_person_id())
)
WITH CHECK (
  tenant_id = (SELECT core.current_tenant_id())
  AND id = (SELECT core.current_person_id())
);

CREATE OR REPLACE FUNCTION core.guard_student_person_update()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_is_staff boolean;
  v_new_created_by uuid;
BEGIN
  -- Apply the guard ONLY for a real authenticated student session (auth.uid() present AND no staff permission)
  IF auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT (iam.has_permission('core.write') OR iam.has_permission('core.pii.write'))
  INTO v_is_staff;

  IF v_is_staff THEN
    RETURN NEW;
  END IF;

  -- Fail-closed allowlist: only permanent_address_id, current_address_id, updated_at, updated_by, row_version may differ
  IF (to_jsonb(NEW) - '{permanent_address_id, current_address_id, updated_at, updated_by, row_version}'::text[])
     IS DISTINCT FROM
     (to_jsonb(OLD) - '{permanent_address_id, current_address_id, updated_at, updated_by, row_version}'::text[])
  THEN
    RAISE EXCEPTION 'Student self-service is restricted to residential address changes. Protected biographical fields require a formal correction request.'
      USING ERRCODE = '42501';
  END IF;

  -- Validate permanent_address_id ownership (must be created by caller or NULL)
  IF NEW.permanent_address_id IS NOT NULL AND NEW.permanent_address_id IS DISTINCT FROM OLD.permanent_address_id THEN
    SELECT created_by INTO v_new_created_by
    FROM core.addresses
    WHERE id = NEW.permanent_address_id
      AND tenant_id = core.current_tenant_id()
      AND deleted_at IS NULL;

    IF v_new_created_by IS NULL OR v_new_created_by <> auth.uid() THEN
      RAISE EXCEPTION 'Permanent address must reference an address created by the authenticated student.'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  -- Validate current_address_id ownership (must be created by caller or match own existing permanent address or NULL)
  IF NEW.current_address_id IS NOT NULL AND NEW.current_address_id IS DISTINCT FROM OLD.current_address_id THEN
    IF NEW.current_address_id IS DISTINCT FROM OLD.permanent_address_id THEN
      SELECT created_by INTO v_new_created_by
      FROM core.addresses
      WHERE id = NEW.current_address_id
        AND tenant_id = core.current_tenant_id()
        AND deleted_at IS NULL;

      IF v_new_created_by IS NULL OR v_new_created_by <> auth.uid() THEN
        RAISE EXCEPTION 'Current address must reference an address created by the authenticated student.'
          USING ERRCODE = '42501';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_student_person_update ON core.persons;
CREATE TRIGGER trg_guard_student_person_update
  BEFORE UPDATE ON core.persons
  FOR EACH ROW
  EXECUTE FUNCTION core.guard_student_person_update();

-- -----------------------------------------------------------------------------
-- 4. audit.change_log: Scoped Student Mutation History Policy
-- -----------------------------------------------------------------------------

DROP POLICY IF EXISTS change_log_student_select ON audit.change_log;
CREATE POLICY change_log_student_select ON audit.change_log
FOR SELECT TO authenticated
USING (
  tenant_id = (SELECT core.current_tenant_id())
  AND table_schema = 'stu'
  AND table_name = 'students'
  AND record_id IN (SELECT core.my_student_ids())
);

COMMIT;
