-- ============================================================================
-- DRAFT CLEANUP SCRIPT: E2E-STM02- TEST DATA REMOVAL
-- Scope: Development Project azulbaxszsqxymqmlwfe ONLY
-- Status: REVIEW ONLY — DO NOT EXECUTE AUTONOMOUSLY
-- ============================================================================

BEGIN;

-- 1. Remove Handover Records & Created Students in E2E Tenant
DELETE FROM stu.admission_handovers 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.students 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001'
  AND enrollment_no LIKE 'E2E-STM02-%';

-- 2. Remove Downstream Admissions Artifacts
DELETE FROM stu.admission_offers 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.admission_decisions 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.application_reviews 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.application_documents 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.application_status_history 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.applications 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

-- 3. Remove Intake & Rules
DELETE FROM stu.admission_eligibility_rules 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM stu.admission_intakes 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

-- 4. Remove Supporting Academic and Organization Fixtures
DELETE FROM acd.programs 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM core.academic_years 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM core.colleges 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM core.persons 
WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001';

DELETE FROM core.tenants 
WHERE id = 'e2e00000-0000-0000-0000-000000000001';

COMMIT;

