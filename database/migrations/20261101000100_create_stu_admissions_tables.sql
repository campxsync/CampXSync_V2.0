-- ============================================================================
-- CampXSync Migration: STM-02 Admissions & Application Management Tables
-- File: 20261101000100_create_stu_admissions_tables.sql
-- Scope: Multi-tenant admissions lifecycle persistence within schema 'stu'
-- Author: CampX Platform Engineering & Architecture Team
-- ============================================================================

-- Ensure pgcrypto extension is available for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. ADMISSION INTAKES (Program intake windows and quotas)
CREATE TABLE IF NOT EXISTS stu.admission_intakes (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    college_id UUID NOT NULL,
    program_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    intake_name VARCHAR(120) NOT NULL,
    intake_code VARCHAR(32) NOT NULL,
    application_window_start TIMESTAMPTZ NOT NULL,
    application_window_end TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    admitted_count INTEGER NOT NULL DEFAULT 0 CHECK (admitted_count >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    fee_required BOOLEAN NOT NULL DEFAULT false,
    fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (fee_amount >= 0.00),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    fee_timing VARCHAR(32) NOT NULL DEFAULT 'AT_SUBMISSION'
        CHECK (fee_timing IN ('AT_SUBMISSION', 'POST_ELIGIBILITY', 'UPON_OFFER_ACCEPTANCE', 'NONE')),
    scoring_rubric JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_admission_intakes PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_admission_intakes_code UNIQUE (tenant_id, academic_year_id, intake_code)
);

CREATE INDEX IF NOT EXISTS ix_admission_intakes_tenant_prog_year 
    ON stu.admission_intakes (tenant_id, program_id, academic_year_id);
CREATE INDEX IF NOT EXISTS ix_admission_intakes_window 
    ON stu.admission_intakes (tenant_id, application_window_start, application_window_end)
    WHERE is_active = true AND deleted_at IS NULL;

-- 2. ADMISSION ELIGIBILITY RULES (Versioned criteria)
CREATE TABLE IF NOT EXISTS stu.admission_eligibility_rules (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    intake_id UUID NOT NULL,
    version VARCHAR(32) NOT NULL,
    min_qualification_grade NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    min_age INTEGER NOT NULL DEFAULT 16,
    max_age INTEGER,
    required_prerequisites JSONB NOT NULL DEFAULT '[]'::jsonb,
    rule_definitions JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_admission_eligibility_rules PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_eligibility_intake FOREIGN KEY (tenant_id, intake_id)
        REFERENCES stu.admission_intakes(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_eligibility_rules_version UNIQUE (tenant_id, intake_id, version)
);

CREATE INDEX IF NOT EXISTS ix_admission_eligibility_intake 
    ON stu.admission_eligibility_rules (tenant_id, intake_id, is_active);

-- 3. APPLICATIONS (Primary intake transactional master)
CREATE TABLE IF NOT EXISTS stu.applications (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_number VARCHAR(64) NOT NULL,
    intake_id UUID NOT NULL,
    applicant_person_id UUID NOT NULL,
    assigned_department_id UUID,
    assigned_reviewer_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN (
            'DRAFT', 'SUBMITTED', 'RETURNED_FOR_INFORMATION', 
            'ELIGIBLE', 'UNDER_REVIEW', 'OFFERED', 
            'ACCEPTED', 'DECLINED', 'EXPIRED', 
            'REJECTED', 'WITHDRAWN', 'HANDOVER_PENDING', 'HANDED_OVER'
        )),
    document_verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (document_verification_status IN ('PENDING', 'IN_PROGRESS', 'VERIFIED', 'REJECTED', 'WAIVED')),
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING', 'PAID', 'WAIVED', 'EXEMPT', 'FAILED')),
    fee_obligation_id UUID,
    payment_reference VARCHAR(128),
    payment_amount NUMERIC(12,2) DEFAULT 0.00,
    submission_timestamp TIMESTAMPTZ,
    eligibility_rule_version VARCHAR(32),
    eligibility_result VARCHAR(32),
    eligibility_evaluation_detail JSONB DEFAULT '{}'::jsonb,
    eligibility_overridden BOOLEAN NOT NULL DEFAULT false,
    eligibility_override_reason TEXT,
    eligibility_override_by UUID,
    late_submission_exception_granted BOOLEAN NOT NULL DEFAULT false,
    late_submission_exception_by UUID,
    declaration_accepted BOOLEAN NOT NULL DEFAULT false,
    declaration_timestamp TIMESTAMPTZ,
    privacy_consent_accepted BOOLEAN NOT NULL DEFAULT false,
    applicant_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_comments_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_applications PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_application_intake FOREIGN KEY (tenant_id, intake_id)
        REFERENCES stu.admission_intakes(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_applications_number UNIQUE (tenant_id, intake_id, application_number)
);

-- Unique index to prevent duplicate in-flight applications for same applicant in same intake
CREATE UNIQUE INDEX IF NOT EXISTS uq_applications_prevent_duplicates 
    ON stu.applications (tenant_id, intake_id, applicant_person_id) 
    WHERE deleted_at IS NULL AND status NOT IN ('WITHDRAWN', 'REJECTED');

CREATE INDEX IF NOT EXISTS ix_applications_tenant_applicant 
    ON stu.applications (tenant_id, applicant_person_id);
CREATE INDEX IF NOT EXISTS ix_applications_tenant_status 
    ON stu.applications (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_applications_reviewer 
    ON stu.applications (tenant_id, assigned_reviewer_id)
    WHERE assigned_reviewer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_applications_dept 
    ON stu.applications (tenant_id, assigned_department_id)
    WHERE assigned_department_id IS NOT NULL;

-- 4. APPLICATION STATUS HISTORY (Immutable audit of state machine transitions)
CREATE TABLE IF NOT EXISTS stu.application_status_history (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    transition_action VARCHAR(64) NOT NULL,
    transition_reason TEXT,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_application_status_history PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_status_history_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_app_status_history_app 
    ON stu.application_status_history (tenant_id, application_id, created_at DESC);

-- 5. APPLICATION DOCUMENTS (Metadata and verification tracking)
CREATE TABLE IF NOT EXISTS stu.application_documents (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'WAIVED')),
    verified_by UUID,
    verified_at TIMESTAMPTZ,
    rejection_reason TEXT,
    is_fraudulent BOOLEAN NOT NULL DEFAULT false,
    fraud_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_application_documents PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_app_documents_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_app_documents_app 
    ON stu.application_documents (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS ix_app_documents_doc 
    ON stu.application_documents (tenant_id, document_id);

-- 6. APPLICATION REVIEWS (Departmental rubrics and scoring)
CREATE TABLE IF NOT EXISTS stu.application_reviews (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    rubric_version VARCHAR(32) NOT NULL,
    rubric_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    total_score NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    recommendation VARCHAR(32) NOT NULL
        CHECK (recommendation IN ('RECOMMEND_OFFER', 'RECOMMEND_REJECT', 'WAITLIST', 'REQUEST_INFORMATION')),
    confidential_comments TEXT,
    applicant_facing_remarks TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_application_reviews PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_reviews_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_app_reviews_app 
    ON stu.application_reviews (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS ix_app_reviews_reviewer 
    ON stu.application_reviews (tenant_id, reviewer_id);

-- 7. ADMISSION DECISIONS (Authorized binding institutional decisions)
CREATE TABLE IF NOT EXISTS stu.admission_decisions (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    decision_type VARCHAR(32) NOT NULL
        CHECK (decision_type IN ('OFFER_APPROVED', 'REJECTED', 'DEFERRED', 'WAITLISTED')),
    decision_reason_code VARCHAR(64) NOT NULL,
    decision_notes TEXT,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_admission_decisions PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_decisions_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_admission_decisions_app 
    ON stu.admission_decisions (tenant_id, application_id);

-- 8. ADMISSION OFFERS (Binding offers with expiration and acceptance)
CREATE TABLE IF NOT EXISTS stu.admission_offers (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    offer_code VARCHAR(64) NOT NULL,
    offer_terms_version VARCHAR(32) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    valid_to TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('ISSUED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'REVOKED')),
    accepted_at TIMESTAMPTZ,
    declined_at TIMESTAMPTZ,
    decline_reason TEXT,
    extension_granted BOOLEAN NOT NULL DEFAULT false,
    extension_granted_by UUID,
    extended_valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_admission_offers PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_offers_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_offers_decision FOREIGN KEY (tenant_id, decision_id)
        REFERENCES stu.admission_decisions(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_admission_offers_code UNIQUE (tenant_id, offer_code)
);

CREATE INDEX IF NOT EXISTS ix_admission_offers_app 
    ON stu.admission_offers (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS ix_admission_offers_expiry 
    ON stu.admission_offers (tenant_id, status, valid_to)
    WHERE status = 'ISSUED';

-- 9. ADMISSION HANDOVERS (Downstream handover tracking to STM-01 & STM-03)
CREATE TABLE IF NOT EXISTS stu.admission_handovers (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_student_id UUID,
    enrolment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (enrolment_status IN ('PENDING', 'STUDENT_CREATED', 'ENROLMENT_COMPLETED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    handover_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_admission_handovers PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_handovers_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES stu.applications(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_handovers_offer FOREIGN KEY (tenant_id, offer_id)
        REFERENCES stu.admission_offers(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_admission_handovers_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_admission_handovers_status 
    ON stu.admission_handovers (tenant_id, enrolment_status)
    WHERE enrolment_status IN ('PENDING', 'FAILED');

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES — FAIL-CLOSED MULTI-TENANT ISOLATION
-- ============================================================================

ALTER TABLE stu.admission_intakes ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.admission_intakes FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.admission_eligibility_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.admission_eligibility_rules FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.applications FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.application_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.application_status_history FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.application_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.application_documents FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.application_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.application_reviews FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.admission_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.admission_decisions FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.admission_offers ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.admission_offers FORCE ROW LEVEL SECURITY;

ALTER TABLE stu.admission_handovers ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.admission_handovers FORCE ROW LEVEL SECURITY;

-- Standard tenant isolation policy macro
CREATE POLICY tenant_isolation_admission_intakes ON stu.admission_intakes
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_admission_eligibility ON stu.admission_eligibility_rules
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_applications ON stu.applications
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_app_status_history ON stu.application_status_history
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_app_documents ON stu.application_documents
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_app_reviews ON stu.application_reviews
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_admission_decisions ON stu.admission_decisions
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_admission_offers ON stu.admission_offers
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_admission_handovers ON stu.admission_handovers
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

-- Permissions / Grants
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA stu TO authenticated;

