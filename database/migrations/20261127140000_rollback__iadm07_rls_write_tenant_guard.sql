-- Rollback: 20261127140000_rollback__iadm07_rls_write_tenant_guard.sql
-- Restores the S0 WITH CHECK policies from 20261126130100_iadm07_sec_schema.sql.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

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

COMMIT;
