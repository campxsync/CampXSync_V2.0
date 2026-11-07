-- Rollback for 20261107000500_add_lifecycle_request_effective_date.sql

ALTER TABLE stu.lifecycle_requests
    DROP COLUMN IF EXISTS effective_date;
