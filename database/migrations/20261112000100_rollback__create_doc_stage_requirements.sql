-- Rollback Migration: 20261112000100_rollback__create_doc_stage_requirements.sql
-- Module: STM-06 Student Documents Service
-- User Story: STM-06-US-002 Define mandatory documents for programmes and stages

DROP POLICY IF EXISTS stage_document_requirements_delete ON doc.stage_document_requirements;
DROP POLICY IF EXISTS stage_document_requirements_update ON doc.stage_document_requirements;
DROP POLICY IF EXISTS stage_document_requirements_insert ON doc.stage_document_requirements;
DROP POLICY IF EXISTS stage_document_requirements_select ON doc.stage_document_requirements;

DROP INDEX IF EXISTS doc.ix_stage_document_requirements_doctype;
DROP INDEX IF EXISTS doc.ix_stage_document_requirements_stage_prog;
DROP INDEX IF EXISTS doc.uq_stage_document_requirements_rule;

DROP TABLE IF EXISTS doc.stage_document_requirements;

