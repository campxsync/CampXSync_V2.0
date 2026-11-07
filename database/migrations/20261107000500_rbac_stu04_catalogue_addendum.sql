-- STM-04 RBAC catalogue addendum. Applied 2026-11-07 to azulbaxszsqxymqmlwfe only.
-- Paired rollback: 20261107000500_rollback__rbac_stu04_catalogue_addendum.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO iam.permissions (code, module, action) VALUES
  ('stu04.lifecycle.view.own', 'stu04', 'lifecycle.view.own'),
  ('stu04.lifecycle.request.submit', 'stu04', 'lifecycle.request.submit'),
  ('stu04.lifecycle.view.linked', 'stu04', 'lifecycle.view.linked'),
  ('stu04.lifecycle.view', 'stu04', 'lifecycle.view'),
  ('stu04.lifecycle.request.decide', 'stu04', 'lifecycle.request.decide'),
  ('stu04.lifecycle.rule.cfg', 'stu04', 'lifecycle.rule.cfg'),
  ('stu04.lifecycle.graduation.validate', 'stu04', 'lifecycle.graduation.validate'),
  ('stu04.lifecycle.graduation.record', 'stu04', 'lifecycle.graduation.record')
ON CONFLICT (code) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO iam.role_template_permissions (role_code, permission_code) VALUES
  ('STUDENT', 'stu04.lifecycle.view.own'),
  ('STUDENT', 'stu04.lifecycle.request.submit'),
  ('PARENT', 'stu04.lifecycle.view.linked'),
  ('REGISTRAR', 'stu04.lifecycle.view'),
  ('REGISTRAR', 'stu04.lifecycle.request.decide'),
  ('REGISTRAR', 'stu04.lifecycle.rule.cfg'),
  ('REGISTRAR', 'stu04.lifecycle.graduation.validate'),
  ('REGISTRAR', 'stu04.lifecycle.graduation.record')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

SELECT iam.resync_system_roles();
