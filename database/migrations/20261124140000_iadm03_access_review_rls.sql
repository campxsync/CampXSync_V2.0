-- Migration: 20261124140000_iadm03_access_review_rls.sql
-- Plain language: IADM-03 S4 — replace legacy adm09.* RLS on existing
-- iam.access_reviews / iam.access_review_items with platform + tenant policies
-- aligned to iam.review.manage (US-032). Tables already exist. No FORCE RLS.
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Apply only after owner: APPROVE MIGRATION 20261124140000

BEGIN;

ALTER TABLE iam.access_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam.access_review_items ENABLE ROW LEVEL SECURITY;

-- Drop legacy adm09-bound policies (Scripts_V3.0)
DROP POLICY IF EXISTS access_reviews_select ON iam.access_reviews;
DROP POLICY IF EXISTS access_reviews_insert ON iam.access_reviews;
DROP POLICY IF EXISTS access_reviews_update ON iam.access_reviews;
DROP POLICY IF EXISTS access_reviews_delete ON iam.access_reviews;
DROP POLICY IF EXISTS access_review_items_select ON iam.access_review_items;
DROP POLICY IF EXISTS access_review_items_insert ON iam.access_review_items;
DROP POLICY IF EXISTS access_review_items_update ON iam.access_review_items;
DROP POLICY IF EXISTS access_review_items_delete ON iam.access_review_items;

CREATE POLICY access_reviews_select ON iam.access_reviews
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

CREATE POLICY access_reviews_insert ON iam.access_reviews
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

CREATE POLICY access_reviews_update ON iam.access_reviews
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

CREATE POLICY access_review_items_select ON iam.access_review_items
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

CREATE POLICY access_review_items_insert ON iam.access_review_items
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

CREATE POLICY access_review_items_update ON iam.access_review_items
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
      AND (SELECT iam.has_permission('iam.review.manage'))
    )
  );

GRANT SELECT, INSERT, UPDATE ON iam.access_reviews TO authenticated;
GRANT SELECT, INSERT, UPDATE ON iam.access_review_items TO authenticated;
GRANT ALL ON iam.access_reviews TO service_role;
GRANT ALL ON iam.access_review_items TO service_role;

COMMENT ON TABLE iam.access_reviews IS
  'IADM-03 US-032 manual access review campaigns; cadence open; no auto-revoke.';

COMMIT;
