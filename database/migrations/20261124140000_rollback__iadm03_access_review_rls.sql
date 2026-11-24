-- Rollback: 20261124140000_iadm03_access_review_rls.sql
-- DEV only. Does not drop iam.access_reviews / items tables.

BEGIN;

DROP POLICY IF EXISTS access_reviews_update ON iam.access_reviews;
DROP POLICY IF EXISTS access_reviews_insert ON iam.access_reviews;
DROP POLICY IF EXISTS access_reviews_select ON iam.access_reviews;
DROP POLICY IF EXISTS access_review_items_update ON iam.access_review_items;
DROP POLICY IF EXISTS access_review_items_insert ON iam.access_review_items;
DROP POLICY IF EXISTS access_review_items_select ON iam.access_review_items;

COMMIT;
