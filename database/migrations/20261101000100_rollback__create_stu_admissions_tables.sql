-- ============================================================================
-- CampXSync Rollback: Revert STM-02 Admissions & Application Management Tables
-- File: 20261101000100_rollback__create_stu_admissions_tables.sql
-- Scope: Revert admissions tables from schema 'stu'
-- Author: CampX Platform Engineering & Architecture Team
-- ============================================================================

DROP POLICY IF EXISTS tenant_isolation_admission_handovers ON stu.admission_handovers;
DROP POLICY IF EXISTS tenant_isolation_admission_offers ON stu.admission_offers;
DROP POLICY IF EXISTS tenant_isolation_admission_decisions ON stu.admission_decisions;
DROP POLICY IF EXISTS tenant_isolation_app_reviews ON stu.application_reviews;
DROP POLICY IF EXISTS tenant_isolation_app_documents ON stu.application_documents;
DROP POLICY IF EXISTS tenant_isolation_app_status_history ON stu.application_status_history;
DROP POLICY IF EXISTS tenant_isolation_applications ON stu.applications;
DROP POLICY IF EXISTS tenant_isolation_admission_eligibility ON stu.admission_eligibility_rules;
DROP POLICY IF EXISTS tenant_isolation_admission_intakes ON stu.admission_intakes;

DROP TABLE IF EXISTS stu.admission_handovers CASCADE;
DROP TABLE IF EXISTS stu.admission_offers CASCADE;
DROP TABLE IF EXISTS stu.admission_decisions CASCADE;
DROP TABLE IF EXISTS stu.application_reviews CASCADE;
DROP TABLE IF EXISTS stu.application_documents CASCADE;
DROP TABLE IF EXISTS stu.application_status_history CASCADE;
DROP TABLE IF EXISTS stu.applications CASCADE;
DROP TABLE IF EXISTS stu.admission_eligibility_rules CASCADE;
DROP TABLE IF EXISTS stu.admission_intakes CASCADE;

