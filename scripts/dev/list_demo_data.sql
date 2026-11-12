-- scripts/dev/list_demo_data.sql
-- Read-only query to inspect demo records created across CampXSync schemas
-- Run against Dev PostgreSQL: azulbaxszsqxymqmlwfe

SELECT 'doc.documents' AS source_table, id::text, title AS display_name, status, created_at
FROM doc.documents
WHERE title LIKE '%DEMO-%'
ORDER BY created_at DESC
LIMIT 25;

SELECT 'doc.document_types' AS source_table, id::text, code || ' - ' || name AS display_name, is_active::text AS status, created_at
FROM doc.document_types
WHERE code LIKE '%DEMO%' OR name LIKE '%Demo%' OR code LIKE '%TEST%'
ORDER BY created_at DESC
LIMIT 25;

SELECT 'doc.stage_document_requirements' AS source_table, id::text, academic_stage || ' - ' || document_type_id::text AS display_name, is_mandatory::text AS status, created_at
FROM doc.stage_document_requirements
ORDER BY created_at DESC
LIMIT 25;

SELECT 'doc.verifications' AS source_table, id::text, verification_code AS display_name, result AS status, verified_at AS created_at
FROM doc.verifications
WHERE verification_code LIKE '%DEMO%' OR verification_code LIKE '%REJ%' OR verification_code LIKE '%VER%'
ORDER BY verified_at DESC
LIMIT 25;

SELECT 'stu.lifecycle_requests' AS source_table, id::text, request_type || ' (' || student_id::text || ')' AS display_name, status, created_at
FROM stu.lifecycle_requests
WHERE reason LIKE '%DEMO-%'
ORDER BY created_at DESC
LIMIT 25;


