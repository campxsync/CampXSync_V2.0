-- Approved 2026-11-07 for STM-05-US-001 on development ref azulbaxszsqxymqmlwfe only.
-- core.persons INSERT previously allowed a photoless row only through iam.perm_all('core.pii.write').
-- The has_permission branch was compiled as (NOT true), so a college-scoped core.pii.write grant could not insert a guardian person.
-- This keeps tenant match, perm_all, and photo-document scope. It allows a null photo when iam.has_permission('core.pii.write') is already true.
-- Update and delete policies are unchanged.

ALTER POLICY persons_insert ON core.persons
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id()))
  AND (
    (SELECT iam.perm_all('core.pii.write'::text))
    OR (
      (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.write'::text, true)))
      OR (
        photo_document_id IS NULL
        AND (SELECT iam.has_permission('core.pii.write'::text))
      )
    )
  )
);
