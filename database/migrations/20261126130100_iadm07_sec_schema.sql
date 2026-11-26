-- Migration: 20261126130100_iadm07_sec_schema.sql
-- Plain language: IADM-07 owns security governance registers only (IADM-07-US-024).
-- Schema name `sec` (short convention; architecture label "security").
-- No audit/identity/configuration tables. Evidence = reference only.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

CREATE SCHEMA IF NOT EXISTS sec;
COMMENT ON SCHEMA sec IS 'IADM-07 Security & Compliance Governance — registers only; never enforces';

-- Security standards (platform-level; institute_id NULL)
CREATE TABLE IF NOT EXISTS sec.security_standards (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                  TEXT NOT NULL,
  title                 TEXT NOT NULL,
  version_no            INTEGER NOT NULL DEFAULT 1,
  status                TEXT NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','SUPERSEDED')),
  body_ref              TEXT NULL,
  author_user_id        UUID NOT NULL,
  approver_user_id      UUID NULL,
  effective_at          TIMESTAMPTZ NULL,
  review_due_at         TIMESTAMPTZ NULL,
  supersedes_id         UUID NULL REFERENCES sec.security_standards(id),
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_standards_code_ver_uq UNIQUE (code, version_no)
);

-- Signals (institute-scoped or platform)
CREATE TABLE IF NOT EXISTS sec.signals (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  correlation_id        TEXT NOT NULL,
  source                TEXT NOT NULL,
  source_demo_only      BOOLEAN NOT NULL DEFAULT false,
  severity              TEXT NOT NULL DEFAULT 'MEDIUM'
                        CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  status                TEXT NOT NULL DEFAULT 'NEW'
                        CHECK (status IN ('NEW','ACKNOWLEDGED','INVESTIGATING','CLOSED','CONFLICT_REVIEW')),
  summary               TEXT NOT NULL,
  conflict_flag         BOOLEAN NOT NULL DEFAULT false,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS sec_signals_corr_uq ON sec.signals (correlation_id) WHERE conflict_flag = false;

-- Findings
CREATE TABLE IF NOT EXISTS sec.findings (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  code                  TEXT NOT NULL,
  title                 TEXT NOT NULL,
  severity              TEXT NOT NULL DEFAULT 'MEDIUM'
                        CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  status                TEXT NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','TRIAGED','ASSIGNED','REMEDIATION_IN_PROGRESS','PENDING_VERIFICATION','RESOLVED')),
  assigned_institute_id UUID NULL,
  target_date           DATE NULL,
  sla_value             TEXT NULL,
  signal_id             UUID NULL REFERENCES sec.signals(id),
  created_by            UUID NOT NULL,
  verified_by           UUID NULL,
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_findings_code_uq UNIQUE (code)
);

-- Risks
CREATE TABLE IF NOT EXISTS sec.risks (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  code                  TEXT NOT NULL,
  title                 TEXT NOT NULL,
  status                TEXT NOT NULL DEFAULT 'IDENTIFIED'
                        CHECK (status IN ('IDENTIFIED','ASSESSED','TREATMENT_PLANNED','TREATING','MONITORED','CLOSED')),
  severity              TEXT NOT NULL DEFAULT 'MEDIUM'
                        CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  demo_label            TEXT NULL,
  created_by            UUID NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_risks_code_uq UNIQUE (code)
);

-- Controls (DEMO ONLY catalogue; platform-level definitions)
CREATE TABLE IF NOT EXISTS sec.controls (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                  TEXT NOT NULL,
  title                 TEXT NOT NULL,
  framework_text        TEXT NOT NULL DEFAULT '',
  demo_label            TEXT NOT NULL DEFAULT 'DEMO ONLY',
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_controls_code_uq UNIQUE (code)
);

-- Per-institute control assessments
CREATE TABLE IF NOT EXISTS sec.control_assessments (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  control_id            UUID NOT NULL REFERENCES sec.controls(id),
  institute_id          UUID NOT NULL,
  status                TEXT NOT NULL DEFAULT 'NOT_ASSESSED'
                        CHECK (status IN ('NOT_ASSESSED','EVIDENCE_PENDING','IN_REVIEW','SATISFIED','GAP','EXCEPTION')),
  updated_by            UUID NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_control_assess_uq UNIQUE (control_id, institute_id)
);

-- Evidence references only (never a file payload)
CREATE TABLE IF NOT EXISTS sec.evidence_refs (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  linked_type           TEXT NOT NULL CHECK (linked_type IN ('FINDING','CONTROL','RISK','EXCEPTION')),
  linked_id             UUID NOT NULL,
  reference_address     TEXT NOT NULL,
  source                TEXT NOT NULL,
  recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by            UUID NOT NULL,
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Exceptions
CREATE TABLE IF NOT EXISTS sec.exceptions (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  code                  TEXT NOT NULL,
  rationale             TEXT NOT NULL,
  linked_control_id     UUID NULL REFERENCES sec.controls(id),
  linked_finding_id     UUID NULL REFERENCES sec.findings(id),
  status                TEXT NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXPIRED')),
  valid_from            TIMESTAMPTZ NOT NULL,
  valid_to              TIMESTAMPTZ NOT NULL,
  requester_user_id     UUID NOT NULL,
  decider_user_id       UUID NULL,
  decision_rationale    TEXT NULL,
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_exceptions_code_uq UNIQUE (code),
  CONSTRAINT sec_exceptions_window_chk CHECK (valid_to > valid_from)
);

-- Alerts (in-app)
CREATE TABLE IF NOT EXISTS sec.alerts (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  code                  TEXT NOT NULL,
  title                 TEXT NOT NULL,
  severity              TEXT NOT NULL DEFAULT 'MEDIUM'
                        CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  status                TEXT NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
  signal_id             UUID NULL REFERENCES sec.signals(id),
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sec_alerts_code_uq UNIQUE (code)
);

-- Enforcement-status reports (IADM-07 records/displays only; never enforces)
CREATE TABLE IF NOT EXISTS sec.enforcement_status (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  institute_id          UUID NULL,
  control_id            UUID NULL REFERENCES sec.controls(id),
  service_name          TEXT NOT NULL,
  reported_status       TEXT NOT NULL,
  report_source         TEXT NOT NULL DEFAULT 'MANUAL'
                        CHECK (report_source IN ('MANUAL','SERVICE','SIMULATOR')),
  reported_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id        TEXT NULL,
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS sec_findings_inst_idx ON sec.findings (institute_id, status);
CREATE INDEX IF NOT EXISTS sec_alerts_inst_idx ON sec.alerts (institute_id, status);
CREATE INDEX IF NOT EXISTS sec_signals_inst_idx ON sec.signals (institute_id, status);
CREATE INDEX IF NOT EXISTS sec_exceptions_inst_idx ON sec.exceptions (institute_id, status);
CREATE INDEX IF NOT EXISTS sec_assess_inst_idx ON sec.control_assessments (institute_id);

-- FORCE RLS on every table
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'security_standards','signals','findings','risks','controls','control_assessments',
    'evidence_refs','exceptions','alerts','enforcement_status'
  ]
  LOOP
    EXECUTE format('ALTER TABLE sec.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE sec.%I FORCE ROW LEVEL SECURITY', t);
  END LOOP;
END $$;

-- Platform-readable standards: platform admin OR holders of posture/register/summary keys
DROP POLICY IF EXISTS sec_standards_select ON sec.security_standards;
CREATE POLICY sec_standards_select ON sec.security_standards
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.posture.view'))
    OR (SELECT iam.has_permission('iadm07.register.read'))
    OR (SELECT iam.has_permission('iadm07.summary.view') AND status = 'PUBLISHED')
    OR (SELECT iam.has_permission('iadm07.standard.propose'))
  );

DROP POLICY IF EXISTS sec_standards_write ON sec.security_standards;
CREATE POLICY sec_standards_write ON sec.security_standards
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.standard.propose'))
    OR (SELECT iam.has_permission('iadm07.standard.approve'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.standard.propose'))
    OR (SELECT iam.has_permission('iadm07.standard.approve'))
  );

-- Helper predicate for institute-scoped rows: platform OR own institute
-- Findings
DROP POLICY IF EXISTS sec_findings_select ON sec.findings;
CREATE POLICY sec_findings_select ON sec.findings
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL
         OR institute_id = (SELECT core.current_tenant_id())
         OR assigned_institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_findings_write ON sec.findings;
CREATE POLICY sec_findings_write ON sec.findings
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
         OR assigned_institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
  );

DROP POLICY IF EXISTS sec_risks_select ON sec.risks;
CREATE POLICY sec_risks_select ON sec.risks
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_risks_write ON sec.risks;
CREATE POLICY sec_risks_write ON sec.risks
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
  );

DROP POLICY IF EXISTS sec_signals_select ON sec.signals;
CREATE POLICY sec_signals_select ON sec.signals
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  );

DROP POLICY IF EXISTS sec_signals_write ON sec.signals;
CREATE POLICY sec_signals_write ON sec.signals
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  );

DROP POLICY IF EXISTS sec_controls_select ON sec.controls;
CREATE POLICY sec_controls_select ON sec.controls
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read'))
    OR (SELECT iam.has_permission('iadm07.summary.view'))
    OR (SELECT iam.has_permission('iadm07.posture.view'))
  );

DROP POLICY IF EXISTS sec_controls_write ON sec.controls;
CREATE POLICY sec_controls_write ON sec.controls
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()) OR (SELECT iam.has_permission('iadm07.register.write')))
  WITH CHECK ((SELECT iam.is_platform_admin()) OR (SELECT iam.has_permission('iadm07.register.write')));

DROP POLICY IF EXISTS sec_assess_select ON sec.control_assessments;
CREATE POLICY sec_assess_select ON sec.control_assessments
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND institute_id = (SELECT core.current_tenant_id()))
    OR (SELECT iam.has_permission('iadm07.summary.view') AND institute_id = (SELECT core.current_tenant_id()))
  );

DROP POLICY IF EXISTS sec_assess_write ON sec.control_assessments;
CREATE POLICY sec_assess_write ON sec.control_assessments
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND institute_id = (SELECT core.current_tenant_id()))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
  );

DROP POLICY IF EXISTS sec_evidence_select ON sec.evidence_refs;
CREATE POLICY sec_evidence_select ON sec.evidence_refs
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_evidence_write ON sec.evidence_refs;
CREATE POLICY sec_evidence_write ON sec.evidence_refs
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
  );

DROP POLICY IF EXISTS sec_exceptions_select ON sec.exceptions;
CREATE POLICY sec_exceptions_select ON sec.exceptions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
    OR (SELECT iam.has_permission('iadm07.exception.request') AND institute_id = (SELECT core.current_tenant_id()))
  );

DROP POLICY IF EXISTS sec_exceptions_write ON sec.exceptions;
CREATE POLICY sec_exceptions_write ON sec.exceptions
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.exception.request'))
    OR (SELECT iam.has_permission('iadm07.exception.decide'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.exception.request'))
    OR (SELECT iam.has_permission('iadm07.exception.decide'))
  );

DROP POLICY IF EXISTS sec_alerts_select ON sec.alerts;
CREATE POLICY sec_alerts_select ON sec.alerts
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_alerts_write ON sec.alerts;
CREATE POLICY sec_alerts_write ON sec.alerts
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
  );

DROP POLICY IF EXISTS sec_enforcement_select ON sec.enforcement_status;
CREATE POLICY sec_enforcement_select ON sec.enforcement_status
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.read') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  );

DROP POLICY IF EXISTS sec_enforcement_write ON sec.enforcement_status;
CREATE POLICY sec_enforcement_write ON sec.enforcement_status
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write'))
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
  );

GRANT USAGE ON SCHEMA sec TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA sec TO authenticated, service_role;

COMMIT;
