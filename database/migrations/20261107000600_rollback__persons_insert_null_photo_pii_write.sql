-- Restores the previous core.persons INSERT check. The has_permission branch is inactive again.

ALTER POLICY persons_insert ON core.persons
WITH CHECK (
  (tenant_id = (SELECT core.current_tenant_id()))
  AND (
    (SELECT iam.perm_all('core.pii.write'::text))
    OR (
      (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.write'::text, true)))
      OR (
        (NOT true)
        AND photo_document_id IS NULL
        AND (SELECT iam.has_permission('core.pii.write'::text))
      )
    )
  )
);
