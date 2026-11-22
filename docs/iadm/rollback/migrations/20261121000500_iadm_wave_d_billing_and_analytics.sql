-- Wave D: entitlement check helper + analytics aggregates view + keys
BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description FROM (VALUES
  ('iadm04.view', 'iadm04', 'view', 'IADM-04 billing/entitlements view'),
  ('iadm04.write', 'iadm04', 'write', 'IADM-04 billing write'),
  ('iadm04.manage', 'iadm04', 'manage', 'IADM-04 billing manage'),
  ('iadm06.view', 'iadm06', 'view', 'IADM-06 analytics view'),
  ('iadm06.export', 'iadm06', 'export', 'IADM-06 analytics export')
) AS v(code, module, action, description)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL);

CREATE OR REPLACE FUNCTION plat.check_entitlement(
  p_tenant_id uuid,
  p_entitlement_key text
) RETURNS TABLE (allowed boolean, limit_value numeric, used_value numeric, deny_reason text)
LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = ''
AS $$
DECLARE
  v_limit numeric;
  v_used numeric;
BEGIN
  IF p_entitlement_key IS NULL OR length(trim(p_entitlement_key)) = 0 THEN
    allowed := false; limit_value := NULL; used_value := NULL; deny_reason := 'UNKNOWN_ENTITLEMENT';
    RETURN NEXT; RETURN;
  END IF;

  SELECT pe.limit_value INTO v_limit
  FROM plat.subscriptions s
  JOIN plat.plan_entitlements pe ON pe.plan_id = s.plan_id
  WHERE s.tenant_id = p_tenant_id
    AND s.status IN ('ACTIVE','TRIAL','GRACE')
    AND pe.entitlement_key = p_entitlement_key
    AND s.deleted_at IS NULL
  ORDER BY s.updated_at DESC NULLS LAST
  LIMIT 1;

  IF v_limit IS NULL AND NOT FOUND THEN
    -- also try when limit_value column naming differs — fail closed
    allowed := false; limit_value := NULL; used_value := NULL; deny_reason := 'UNKNOWN_ENTITLEMENT';
    RETURN NEXT; RETURN;
  END IF;

  SELECT COALESCE(SUM(um.metric_value), 0) INTO v_used
  FROM plat.usage_metrics um
  WHERE um.tenant_id = p_tenant_id AND um.metric_name = p_entitlement_key AND um.deleted_at IS NULL;

  allowed := (v_used IS NULL) OR (v_limit IS NULL) OR (v_used <= v_limit);
  limit_value := v_limit;
  used_value := COALESCE(v_used, 0);
  deny_reason := CASE WHEN allowed THEN NULL ELSE 'LIMIT_EXCEEDED' END;
  RETURN NEXT;
EXCEPTION WHEN undefined_column OR undefined_table THEN
  allowed := false; limit_value := NULL; used_value := NULL; deny_reason := 'UNKNOWN_ENTITLEMENT';
  RETURN NEXT;
END;
$$;

REVOKE ALL ON FUNCTION plat.check_entitlement(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.check_entitlement(uuid, text) TO authenticated, service_role;

COMMIT;
