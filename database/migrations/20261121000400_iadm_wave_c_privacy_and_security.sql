-- Wave C: IADM-05 privacy + IADM-07 security tables and permission keys
-- DEV only: azulbaxszsqxymqmlwfe

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description FROM (VALUES
  ('iadm05.view', 'iadm05', 'view', 'IADM-05 Data Governance view'),
  ('iadm05.write', 'iadm05', 'write', 'IADM-05 Data Governance write'),
  ('iadm05.execute', 'iadm05', 'execute', 'IADM-05 execute assigned privacy tasks'),
  ('iadm05.manage', 'iadm05', 'manage', 'IADM-05 Data Governance manage'),
  ('iadm07.view', 'iadm07', 'view', 'IADM-07 Security Compliance view'),
  ('iadm07.write', 'iadm07', 'write', 'IADM-07 Security Compliance write'),
  ('iadm07.manage', 'iadm07', 'manage', 'IADM-07 Security Compliance manage')
) AS v(code, module, action, description)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL);

CREATE TABLE IF NOT EXISTS plat.data_subject_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
  request_type text NOT NULL,
  subject_ref text NOT NULL,
  due_date date,
  status text NOT NULL DEFAULT 'OPEN',
  assigned_college_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_dsr_type CHECK (request_type IN ('ACCESS','CORRECTION','ERASURE','EXPORT')),
  CONSTRAINT ck_dsr_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','REJECTED','ON_HOLD')),
  CONSTRAINT uq_dsr_tenant_id UNIQUE (tenant_id, id)
);

ALTER TABLE plat.data_subject_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.data_subject_requests FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS dsr_all ON plat.data_subject_requests;
CREATE POLICY dsr_all ON plat.data_subject_requests FOR ALL TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));
GRANT SELECT, INSERT, UPDATE ON plat.data_subject_requests TO authenticated;
GRANT ALL ON plat.data_subject_requests TO service_role;

CREATE TABLE IF NOT EXISTS plat.legal_holds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  reason text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  released_at timestamptz,
  created_by uuid DEFAULT auth.uid(),
  released_by uuid,
  deleted_at timestamptz,
  CONSTRAINT uq_legal_holds_tenant_id UNIQUE (tenant_id, id)
);
ALTER TABLE plat.legal_holds ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.legal_holds FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS legal_holds_all ON plat.legal_holds;
CREATE POLICY legal_holds_all ON plat.legal_holds FOR ALL TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));
GRANT SELECT, INSERT, UPDATE ON plat.legal_holds TO authenticated;
GRANT ALL ON plat.legal_holds TO service_role;

CREATE TABLE IF NOT EXISTS plat.security_findings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
  title text NOT NULL,
  severity text NOT NULL DEFAULT 'MEDIUM',
  status text NOT NULL DEFAULT 'OPEN',
  owner_user_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_sec_findings_sev CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_sec_findings_status CHECK (status IN ('OPEN','ASSIGNED','CLOSED')),
  CONSTRAINT uq_sec_findings_tenant_id UNIQUE (tenant_id, id)
);
ALTER TABLE plat.security_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.security_findings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sec_findings_all ON plat.security_findings;
CREATE POLICY sec_findings_all ON plat.security_findings FOR ALL TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));
GRANT SELECT, INSERT, UPDATE ON plat.security_findings TO authenticated;
GRANT ALL ON plat.security_findings TO service_role;

CREATE TABLE IF NOT EXISTS plat.institute_security_policies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  policy_key text NOT NULL,
  policy_value jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT uq_inst_sec_pol_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inst_sec_pol_key ON plat.institute_security_policies (tenant_id, policy_key) WHERE deleted_at IS NULL;
ALTER TABLE plat.institute_security_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.institute_security_policies FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS inst_sec_pol_all ON plat.institute_security_policies;
CREATE POLICY inst_sec_pol_all ON plat.institute_security_policies FOR ALL TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()))
  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));
GRANT SELECT, INSERT, UPDATE ON plat.institute_security_policies TO authenticated;
GRANT ALL ON plat.institute_security_policies TO service_role;

COMMIT;
