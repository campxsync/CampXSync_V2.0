-- STM-04-US-011 withdrawal effective date. Development ref azulbaxszsqxymqmlwfe only.
-- Paired rollback: 20261107000500_rollback__add_lifecycle_request_effective_date.sql

ALTER TABLE stu.lifecycle_requests
    ADD COLUMN IF NOT EXISTS effective_date DATE;
