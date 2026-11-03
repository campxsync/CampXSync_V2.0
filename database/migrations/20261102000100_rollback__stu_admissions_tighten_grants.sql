-- Rollback: Restore DELETE and UPDATE privileges on STM-02 admissions tables to role authenticated
-- File: 20261102000100_rollback__stu_admissions_tighten_grants.sql

BEGIN;
GRANT DELETE ON 
  stu.admission_decisions, 
  stu.admission_eligibility_rules, 
  stu.admission_handovers,
  stu.admission_intakes, 
  stu.admission_offers, 
  stu.application_reviews,
  stu.application_status_history, 
  stu.applications 
TO authenticated;

GRANT UPDATE ON stu.application_status_history TO authenticated;
COMMIT;
