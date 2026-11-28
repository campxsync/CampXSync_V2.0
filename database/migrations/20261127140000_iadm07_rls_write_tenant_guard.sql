-- Migration: 20261127140000_iadm07_rls_write_tenant_guard.sql
-- Plain language: tighten WITH CHECK (and USING on signals/exceptions/enforcement)
-- so non-platform writers cannot insert or update rows for another institute.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP POLICY IF EXISTS sec_findings_write ON sec.findings;
CREATE POLICY sec_findings_write ON sec.findings
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL
         OR institute_id = (SELECT core.current_tenant_id())
         OR assigned_institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL
         OR institute_id = (SELECT core.current_tenant_id())
         OR assigned_institute_id = (SELECT core.current_tenant_id())
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
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
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
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
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
    OR (SELECT iam.has_permission('iadm07.register.write') AND institute_id = (SELECT core.current_tenant_id()))
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
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_exceptions_write ON sec.exceptions;
CREATE POLICY sec_exceptions_write ON sec.exceptions
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.exception.decide'))
    OR (SELECT iam.has_permission('iadm07.exception.request') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.exception.decide'))
    OR (SELECT iam.has_permission('iadm07.exception.request') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_signals_write ON sec.signals;
CREATE POLICY sec_signals_write ON sec.signals
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

DROP POLICY IF EXISTS sec_enforcement_write ON sec.enforcement_status;
CREATE POLICY sec_enforcement_write ON sec.enforcement_status
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm07.signal.intake'))
    OR (SELECT iam.has_permission('iadm07.register.write') AND (
         institute_id IS NULL OR institute_id = (SELECT core.current_tenant_id())
       ))
  );

COMMIT;
