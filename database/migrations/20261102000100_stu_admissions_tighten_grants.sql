-- Migration: Tighten STM-02 admissions table privileges on role authenticated
-- File: 20261102000100_stu_admissions_tighten_grants.sql

BEGIN;
REVOKE DELETE ON 
  stu.admission_decisions, 
  stu.admission_eligibility_rules, 
  stu.admission_handovers,
  stu.admission_intakes, 
  stu.admission_offers, 
  stu.application_reviews,
  stu.application_status_history, 
  stu.applications 
FROM authenticated;

REVOKE UPDATE ON stu.application_status_history FROM authenticated;
COMMIT;
