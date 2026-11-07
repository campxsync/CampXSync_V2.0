-- RBAC catalogue addendum approved 2026-11-07 (D-RBAC-01..07).
-- Applied 2026-11-07 to azulbaxszsqxymqmlwfe only. Paired rollback: 20261107000200_rollback__rbac_stu_catalogue_addendum.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO iam.permissions (code, module, action) VALUES
  ('stu03.term.cfg', 'stu03', 'term.cfg'),
  ('stu03.window.cfg', 'stu03', 'window.cfg'),
  ('stu03.enrolment.create', 'stu03', 'enrolment.create'),
  ('stu03.enrolment.approve', 'stu03', 'enrolment.approve'),
  ('stu03.enrolment.view', 'stu03', 'enrolment.view'),
  ('stu03.enrolment.view.own', 'stu03', 'enrolment.view.own'),
  ('stu03.register.execute', 'stu03', 'register.execute'),
  ('stu03.roster.view', 'stu03', 'roster.view'),
  ('stu03.waitlist.join', 'stu03', 'waitlist.join'),
  ('stu03.waitlist.promote', 'stu03', 'waitlist.promote'),
  ('stu03.section.allocate', 'stu03', 'section.allocate'),
  ('stu02.intake.manage', 'stu02', 'intake.manage'),
  ('stu02.application.review', 'stu02', 'application.review'),
  ('stu02.decision.approve', 'stu02', 'decision.approve'),
  ('stu01.view.own', 'stu01', 'view.own'),
  ('stu01.write.own', 'stu01', 'write.own')
ON CONFLICT (code) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO iam.role_templates (code, name, catalogue_id, kind, scope_note) VALUES
  ('REGISTRAR', 'Registrar', 'R31', 'staff', 'STM principal for enrolment, lifecycle, documents, and related student administration'),
  ('ADMISSIONS_OFFICER', 'Admissions Officer', 'R32', 'staff', 'STM-02 admissions operations; not STM-03 CFG by default'),
  ('APPLICANT', 'Applicant', 'R33', 'student', 'Pre-enrolment applicant identity; distinct from R25 Student')
ON CONFLICT (code) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO iam.role_template_permissions (role_code, permission_code) VALUES
  ('REGISTRAR', 'stu03.term.cfg'),
  ('REGISTRAR', 'stu03.window.cfg'),
  ('REGISTRAR', 'stu03.enrolment.create'),
  ('REGISTRAR', 'stu03.enrolment.approve'),
  ('REGISTRAR', 'stu03.enrolment.view'),
  ('REGISTRAR', 'stu03.waitlist.promote'),
  ('REGISTRAR', 'stu02.intake.manage'),
  ('REGISTRAR', 'stu02.application.review'),
  ('REGISTRAR', 'stu02.decision.approve'),
  ('ADMISSIONS_OFFICER', 'stu02.intake.manage'),
  ('ADMISSIONS_OFFICER', 'stu02.application.review'),
  ('STUDENT', 'stu03.enrolment.view.own'),
  ('STUDENT', 'stu03.register.execute'),
  ('STUDENT', 'stu03.waitlist.join'),
  ('STUDENT', 'stu01.view.own'),
  ('STUDENT', 'stu01.write.own'),
  ('FACULTY', 'stu03.roster.view'),
  ('DEAN', 'stu03.section.allocate'),
  ('HOD', 'stu03.section.allocate'),
  ('APPLICANT', 'stu01.view.own')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- Propagate templates into every existing tenant
SELECT iam.resync_system_roles();
