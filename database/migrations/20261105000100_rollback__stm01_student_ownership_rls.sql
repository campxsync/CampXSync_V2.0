-- =============================================================================
-- Migration Rollback: 20261105000100_rollback__stm01_student_ownership_rls.sql
-- Module: STM-01 Student Identity & Self-Service
-- Description: Reverts scoped student ownership RLS policies and guard trigger.
-- Target Database: Development only (azulbaxszsqxymqmlwfe)
-- =============================================================================

BEGIN;

-- Revert core.persons trigger & policy
DROP TRIGGER IF EXISTS trg_guard_student_person_update ON core.persons;
DROP FUNCTION IF EXISTS core.guard_student_person_update();
DROP POLICY IF EXISTS persons_student_update ON core.persons;

-- Revert core.addresses policies
DROP POLICY IF EXISTS addresses_student_update ON core.addresses;
DROP POLICY IF EXISTS addresses_student_insert ON core.addresses;
DROP POLICY IF EXISTS addresses_student_select ON core.addresses;

-- Revert doc.lifecycle_requests policies
DROP POLICY IF EXISTS lifecycle_requests_student_insert ON doc.lifecycle_requests;
DROP POLICY IF EXISTS lifecycle_requests_student_select ON doc.lifecycle_requests;

-- Revert audit.change_log policy
DROP POLICY IF EXISTS change_log_student_select ON audit.change_log;

COMMIT;

