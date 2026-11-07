-- Rollback for 20261107000400_create_stu_lifecycle_tables.sql

DROP POLICY IF EXISTS tenant_isolation_graduation_records ON stu.graduation_records;
DROP POLICY IF EXISTS tenant_isolation_lifecycle_transition_rules ON stu.lifecycle_transition_rules;
DROP POLICY IF EXISTS tenant_isolation_lifecycle_requests ON stu.lifecycle_requests;
DROP POLICY IF EXISTS tenant_isolation_lifecycle_history ON stu.lifecycle_history;
DROP POLICY IF EXISTS tenant_isolation_lifecycle_status ON stu.lifecycle_status;

DROP TABLE IF EXISTS stu.graduation_records;
DROP TABLE IF EXISTS stu.lifecycle_requests;
DROP TABLE IF EXISTS stu.lifecycle_transition_rules;
DROP TABLE IF EXISTS stu.lifecycle_history;
DROP TABLE IF EXISTS stu.lifecycle_status;
