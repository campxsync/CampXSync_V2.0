
\set ON_ERROR_STOP off
\pset pager off
\pset tuples_only on
create schema t;
create function t.jwt(uid uuid, tenant uuid) returns void language plpgsql as $$ begin
  perform set_config('request.jwt.claim.sub', uid::text, true);
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'app_metadata', json_build_object('tenant_id', tenant))::text, true); end $$;
create function t.blocked(label text, stmt text) returns void language plpgsql as $$ declare ok boolean := false; begin
  begin execute stmt; ok := true; raise exception 'undo'; exception when others then
    if ok then raise notice 'FAIL  % -- statement SUCCEEDED', label; else raise notice 'PASS  % (blocked: %)', label, left(sqlerrm, 90); end if; end; end $$;
create function t.no_effect(label text, stmt text) returns void language plpgsql as $$ declare n bigint; begin
  begin execute stmt; get diagnostics n = row_count; raise exception 'undo'; exception when others then
    if sqlerrm = 'undo' then if n = 0 then raise notice 'PASS  % (0 rows affected)', label; else raise notice 'FAIL  % -- % rows changed', label, n; end if;
    else raise notice 'PASS  % (blocked: %)', label, left(sqlerrm, 90); end if; end; end $$;
create function t.allowed(label text, stmt text) returns void language plpgsql as $$ begin
  execute stmt; raise notice 'PASS  % (allowed)', label; exception when others then raise notice 'FAIL  % -- %', label, sqlerrm; end $$;
create function t.count_is(label text, q text, expected bigint) returns void language plpgsql as $$ declare n bigint; begin
  execute 'select count(*) from (' || q || ') x' into n;
  if n = expected then raise notice 'PASS  % (count=%)', label, n; else raise notice 'FAIL  % -- count % expected %', label, n, expected; end if; end $$;
create function t.val_is(label text, q text, expected text) returns void language plpgsql as $$ declare v text; begin
  execute q into v; if v is not distinct from expected then raise notice 'PASS  % (=%)', label, v; else raise notice 'FAIL  % -- got % expected %', label, v, expected; end if; end $$;
grant usage on schema t to authenticated, service_role; grant execute on all functions in schema t to authenticated, service_role;


insert into core.tenants (id,code,name) values ('aaaaaaaa-0000-0000-0000-000000000001','TA','Tenant A'),('bbbbbbbb-0000-0000-0000-000000000001','TB','Tenant B');
insert into auth.users(id) values ('a1000000-0000-0000-0000-000000000001'),('a1000000-0000-0000-0000-000000000002'),('a1000000-0000-0000-0000-000000000003'),('a1000000-0000-0000-0000-000000000004'),('a1000000-0000-0000-0000-000000000005'),('a1000000-0000-0000-0000-000000000006'),('a1000000-0000-0000-0000-000000000007'),('a1000000-0000-0000-0000-000000000008'),('a1000000-0000-0000-0000-000000000009'),('a1000000-0000-0000-0000-000000000010'),('a1000000-0000-0000-0000-000000000011'),('a1000000-0000-0000-0000-000000000012'),('a1000000-0000-0000-0000-000000000013'),('a1000000-0000-0000-0000-000000000014'),('a1000000-0000-0000-0000-000000000015'),('a1000000-0000-0000-0000-000000000016'),('a1000000-0000-0000-0000-000000000017'),('a1000000-0000-0000-0000-000000000018'),('a1000000-0000-0000-0000-000000000019'),('a1000000-0000-0000-0000-000000000020'),('a1000000-0000-0000-0000-000000000021'),('b1000000-0000-0000-0000-000000000001');
insert into core.colleges (id,tenant_id,code,name) values ('c1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','C1','College 1'),('c2000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','C2','College 2'),('cb000000-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000001','CB','College B');
insert into core.departments (id,tenant_id,college_id,code,name) values ('d1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','CSE','CSE'),('d2000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','ECE','ECE'),('d3000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','c2000000-0000-0000-0000-000000000002','MECH','MECH');
insert into core.persons (id,tenant_id,first_name,display_name,primary_email,primary_phone,date_of_birth) values
('e0000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','stu1','stu1','stu1@example.com','9999900000','2000-01-01'),
('e0000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','stu2','stu2','stu2@example.com','9999900001','2000-01-01'),
('e0000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','stu3','stu3','stu3@example.com','9999900002','2000-01-01'),
('e0000000-0000-0000-0000-000000000004','aaaaaaaa-0000-0000-0000-000000000001','fac1','fac1','fac1@example.com','9999900003','2000-01-01'),
('e0000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001','fac2','fac2','fac2@example.com','9999900004','2000-01-01'),
('e0000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001','hod','hod','hod@example.com','9999900005','2000-01-01'),
('e0000000-0000-0000-0000-000000000007','aaaaaaaa-0000-0000-0000-000000000001','principal','principal','principal@example.com','9999900006','2000-01-01'),
('e0000000-0000-0000-0000-000000000008','aaaaaaaa-0000-0000-0000-000000000001','parent1','parent1','parent1@example.com','9999900007','2000-01-01'),
('e0000000-0000-0000-0000-000000000009','aaaaaaaa-0000-0000-0000-000000000001','other','other','other@example.com','9999900008','2000-01-01');
insert into iam.user_profiles (id,tenant_id,person_id,college_id,email,full_name) values
 ('a1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001',null,null,'tadmin@a.edu','Tenant Admin'),
 ('a1000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001',null,null,'inst@a.edu','Institution Admin'),
 ('a1000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','cadmin1@a.edu','College Admin 1'),
 ('a1000000-0000-0000-0000-000000000004','aaaaaaaa-0000-0000-0000-000000000001',null,'c2000000-0000-0000-0000-000000000002','cadmin2@a.edu','College Admin 2'),
 ('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000007','c1000000-0000-0000-0000-000000000001','principal@a.edu','Principal'),
 ('a1000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000006','c1000000-0000-0000-0000-000000000001','hod@a.edu','HOD CSE'),
 ('a1000000-0000-0000-0000-000000000007','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000004','c1000000-0000-0000-0000-000000000001','fac1@a.edu','Faculty 1'),
 ('a1000000-0000-0000-0000-000000000008','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000005','c1000000-0000-0000-0000-000000000001','fac2@a.edu','Faculty 2'),
 ('a1000000-0000-0000-0000-000000000009','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','ctrl@a.edu','Exam Controller'),
 ('a1000000-0000-0000-0000-000000000010','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','cell@a.edu','Exam Cell'),
 ('a1000000-0000-0000-0000-000000000011','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','val@a.edu','Valuator'),
 ('a1000000-0000-0000-0000-000000000012','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','finmgr@a.edu','Finance Manager'),
 ('a1000000-0000-0000-0000-000000000013','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','acct@a.edu','Accountant'),
 ('a1000000-0000-0000-0000-000000000014','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','hr@a.edu','HR Manager'),
 ('a1000000-0000-0000-0000-000000000015','aaaaaaaa-0000-0000-0000-000000000001',null,null,'aud@a.edu','Auditor'),
 ('a1000000-0000-0000-0000-000000000016','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','s1@a.edu','Student 1'),
 ('a1000000-0000-0000-0000-000000000017','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000002','c1000000-0000-0000-0000-000000000001','s2@a.edu','Student 2'),
 ('a1000000-0000-0000-0000-000000000018','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000008','c1000000-0000-0000-0000-000000000001','p1@a.edu','Parent 1'),
 ('a1000000-0000-0000-0000-000000000019','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','comm@a.edu','Comm Manager'),
 ('a1000000-0000-0000-0000-000000000021','aaaaaaaa-0000-0000-0000-000000000001',null,'c1000000-0000-0000-0000-000000000001','x1@a.edu','Spare user'),
 ('b1000000-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000001',null,null,'admin@b.edu','Admin B');
-- role assignments: (user, role, college, department)
insert into iam.role_assignments (tenant_id,user_id,role_id,college_id,department_id)
 select r.tenant_id,x.uid,r.id,x.col,x.dep from iam.roles r join (values
  ('a1000000-0000-0000-0000-000000000001'::uuid,'TENANT_ADMIN',null::uuid,null::uuid),
  ('a1000000-0000-0000-0000-000000000002','INSTITUTION_ADMIN',null,null),
  ('a1000000-0000-0000-0000-000000000003','COLLEGE_ADMIN','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000004','COLLEGE_ADMIN','c2000000-0000-0000-0000-000000000002',null),
  ('a1000000-0000-0000-0000-000000000005','PRINCIPAL','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000006','HOD','c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001'),
  ('a1000000-0000-0000-0000-000000000007','FACULTY','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000008','FACULTY','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000009','EXAM_CONTROLLER','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000010','EXAM_CELL','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000011','VALUATOR','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000012','FINANCE_MANAGER','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000013','ACCOUNTANT','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000014','HR_MANAGER','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000015','AUDITOR',null,null),
  ('a1000000-0000-0000-0000-000000000016','STUDENT','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000017','STUDENT','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000018','PARENT','c1000000-0000-0000-0000-000000000001',null),
  ('a1000000-0000-0000-0000-000000000019','COMM_MANAGER','c1000000-0000-0000-0000-000000000001',null)) x(uid,code,col,dep) on x.code=r.code and r.tenant_id='aaaaaaaa-0000-0000-0000-000000000001' and r.college_id is null;
insert into iam.role_assignments (tenant_id,user_id,role_id) select tenant_id,'b1000000-0000-0000-0000-000000000001',id from iam.roles where tenant_id='bbbbbbbb-0000-0000-0000-000000000001' and code='TENANT_ADMIN';
-- academics
insert into acd.programs (id,tenant_id,college_id,department_id,code,name,level,duration_years) values
 ('90000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','P1','Prog CSE','UG',4),('90000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','d2000000-0000-0000-0000-000000000002','P2','Prog ECE','UG',4),('90000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','c2000000-0000-0000-0000-000000000002','d3000000-0000-0000-0000-000000000003','P3','Prog MECH','UG',4);
insert into stu.students (id,tenant_id,person_id,college_id,program_id,enrollment_no) values
 ('5d000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','90000000-0000-0000-0000-000000000001','EN1'),('5d000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000002','c1000000-0000-0000-0000-000000000001','90000000-0000-0000-0000-000000000002','EN2'),('5d000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000003','c2000000-0000-0000-0000-000000000002','90000000-0000-0000-0000-000000000003','EN3');
insert into stu.guardianships (tenant_id,student_id,guardian_person_id,relationship) values ('aaaaaaaa-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000008','FATHER');
insert into hrm.employees (id,tenant_id,person_id,college_id,department_id,employee_no) values
 ('e5000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000004','c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','E1'),('e5000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000005','c1000000-0000-0000-0000-000000000001','d2000000-0000-0000-0000-000000000002','E2'),
 ('e5000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000006','c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','E3'),('e5000000-0000-0000-0000-000000000004','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000007','c1000000-0000-0000-0000-000000000001',null,'E4'),
 ('e5000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001','e0000000-0000-0000-0000-000000000009','c1000000-0000-0000-0000-000000000001','d2000000-0000-0000-0000-000000000002','E5');
insert into core.calendars (id,tenant_id,code,name,calendar_type) values ('ca1e0000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','CAL','Cal','ACADEMIC');
insert into acd.calendar_terms (id,tenant_id,calendar_id,term_no,name,start_date,end_date) values ('7e000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','ca1e0000-0000-0000-0000-000000000001',1,'Sem1','2026-07-01','2026-12-01');
insert into acd.courses (id,tenant_id,code,name,department_id) values ('c0000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','CRS1','Course 1','d1000000-0000-0000-0000-000000000001'),('c0000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','CRS2','Course 2','d2000000-0000-0000-0000-000000000002');
insert into acd.subjects (id,tenant_id,code,name,department_id) values ('50000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','SUB1','Subject 1','d1000000-0000-0000-0000-000000000001'),('50000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','SUB2','Subject 2','d2000000-0000-0000-0000-000000000002');
insert into acd.subject_course_map (tenant_id,subject_id,course_id) values ('aaaaaaaa-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001'),('aaaaaaaa-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000002');
insert into acd.course_offerings (id,tenant_id,college_id,course_id,term_id) values
 ('0f000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001','7e000000-0000-0000-0000-000000000001'),('0f000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000002','7e000000-0000-0000-0000-000000000001');
insert into acd.course_sections (id,tenant_id,offering_id,section_code) values ('5e000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','0f000000-0000-0000-0000-000000000001','A'),('5e000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','0f000000-0000-0000-0000-000000000002','B');
insert into acd.section_faculty (tenant_id,section_id,employee_id) values ('aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001'),('aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000002','e5000000-0000-0000-0000-000000000002');
insert into acd.section_enrollments (tenant_id,section_id,student_id) values ('aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001'),('aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000002');
insert into acd.attendance_sessions (id,tenant_id,section_id,employee_id,session_date) values
 ('a5000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001','2026-09-01'),('a5000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000002','e5000000-0000-0000-0000-000000000002','2026-09-02');
insert into acd.attendance_records (tenant_id,session_id,student_id,status) values
 ('aaaaaaaa-0000-0000-0000-000000000001','a5000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','PRESENT'),('aaaaaaaa-0000-0000-0000-000000000001','a5000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000002','PRESENT');
-- LMS
insert into lms.assignments (id,tenant_id,title,section_id,course_id,created_by_employee_id,status) values
 ('a9000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','Assignment A','5e000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001','DRAFT'),('a9000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','Assignment B','5e000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000002','e5000000-0000-0000-0000-000000000002','PUBLISHED');
insert into lms.student_assignments (id,tenant_id,assignment_id,student_id) values ('a8000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','a9000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001'),('a8000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','a9000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000002');
-- exams
insert into exm.examinations (id,tenant_id,college_id,code,name,status) values ('ee000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EX1','Exam 1','REGISTRATION_OPEN'),('ee000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EX2','Exam 2 (planned)','PLANNED');
insert into exm.registrations (id,tenant_id,examination_id,student_id) values ('e1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001');
-- finance
insert into fin.fee_obligations (id,tenant_id,student_id,obligation_no,net_amount,gross_amount) values
 ('0b000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','OB1',1000,1000),('0b000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002','OB2',1000,1000),('0b000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000003','OB3',1000,1000);
insert into fin.payments (id,tenant_id,student_id,payment_no,amount,status) values ('9a000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','P1',1000,'SUCCESS');
insert into core.number_sequences (tenant_id,scope_key,prefix,padding) values ('aaaaaaaa-0000-0000-0000-000000000001','RECEIPT','RCT-',5);
insert into hrm.leave_types (id,tenant_id,code,name) values ('1b000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','CL','Casual');

\echo ---- COLLEGE SCOPE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('SC1 college admin 1 sees only college 1', $$select 1 from core.colleges$$, 1);
select t.count_is('SC2 college admin 1 sees programs of college 1 only (2)', $$select 1 from acd.programs$$, 2);
select t.count_is('SC3 college admin 1 does not see college-2 students', $$select 1 from stu.students where college_id='c2000000-0000-0000-0000-000000000002'$$, 0);
select t.allowed('SC4 college admin 1 creates a program in college 1', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PX','New','UG',4)$$);
select t.blocked('SC5 college admin 1 creates a program in college 2', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c2000000-0000-0000-0000-000000000002','d3000000-0000-0000-0000-000000000003','PY','New','UG',4)$$);
select t.blocked('SC6 college admin 1 creates a TENANT-WIDE calendar (needs institute scope)', $$insert into core.calendars (college_id,code,name,calendar_type) values (null,'ZZ','Tenant wide','ACADEMIC')$$);

rollback;
\echo ---- COLLEGE SCOPE (2)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000004','aaaaaaaa-0000-0000-0000-000000000001');
select t.count_is('SC7 college admin 2 sees only college 2', $$select 1 from core.colleges$$, 1);
select t.count_is('SC8 college admin 2 sees only college-2 students', $$select 1 from stu.students$$, 1);
rollback;
\echo ---- INSTITUTE SCOPE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001');
select t.count_is('SC9 institution admin (institute-wide) sees both colleges', $$select 1 from core.colleges$$, 2);
select t.count_is('SC10 institution admin sees all 3 students', $$select 1 from stu.students$$, 3);
select t.allowed('SC11 institution admin creates a tenant-wide calendar', $$insert into core.calendars (college_id,code,name,calendar_type) values (null,'ZZ','Tenant wide','ACADEMIC')$$);
rollback;
\echo ---- HOD DEPARTMENT SCOPE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('HD1 HOD (CSE) sees programs of own department only', $$select 1 from acd.programs$$, 1);
select t.count_is('HD2 HOD sees students of own department only', $$select 1 from stu.students$$, 1);
select t.allowed('HD3 HOD creates a program in own department (ACD-02 M)', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PH','HOD prog','UG',4)$$);
select t.blocked('HD4 HOD creates a program in ANOTHER department', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d2000000-0000-0000-0000-000000000002','PZ','x','UG',4)$$);
select t.count_is('HD5 HOD sees attendance of own-department students only (ACD-08 RV)', $$select 1 from acd.attendance_records$$, 1);
select t.count_is('HD6 HOD cannot see fee obligations (FIN-02 has no HOD access)', $$select 1 from fin.fee_obligations$$, 0);
select t.count_is('HD7 HOD cannot see payroll', $$select 1 from pay.payslips$$, 0);

rollback;
\echo ---- PRINCIPAL
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('PR1 principal views programs (A = view+approve)', $$select 1 from acd.programs$$, 2);
select t.blocked('PR2 principal cannot create a program (ACD-02 is A, not W)', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PP','x','UG',4)$$);
select t.count_is('PR3 principal sees fee obligations of own college (FIN-02 V)', $$select 1 from fin.fee_obligations$$, 2);
select t.count_is('PR4 principal does not see college-2 obligations', $$select 1 from fin.fee_obligations where student_id='5d000000-0000-0000-0000-000000000003'$$, 0);

rollback;
\echo ---- FACULTY OWNERSHIP
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000007','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('FA1 faculty sees only rostered students', $$select 1 from stu.students$$, 1);
select t.count_is('FA2 faculty cannot read other persons (PII)', $$select 1 from core.persons where id <> 'e0000000-0000-0000-0000-000000000004'$$, 0);
select t.allowed('FA3 faculty creates an assignment in OWN section', $$insert into lms.assignments (title,section_id,course_id,created_by_employee_id) values ('New','5e000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001')$$);
select t.blocked('FA4 faculty creates an assignment in ANOTHER teacher''s section', $$insert into lms.assignments (title,section_id,course_id,created_by_employee_id) values ('Sneaky','5e000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000002','e5000000-0000-0000-0000-000000000002')$$);
select t.no_effect('FA5 faculty edits another teacher''s assignment', $$update lms.assignments set title='hacked' where id='a9000000-0000-0000-0000-000000000002'$$);
select t.count_is('FA6 faculty sees student_assignments of own section only', $$select 1 from lms.student_assignments$$, 1);
select t.allowed('FA7 faculty marks attendance on own session', $$insert into acd.attendance_records (session_id,student_id,status) values ('a5000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002','ABSENT')$$);
select t.blocked('FA8 faculty marks attendance on another teacher''s session', $$insert into acd.attendance_records (session_id,student_id,status) values ('a5000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000001','ABSENT')$$);
select t.count_is('FA9 faculty reads attendance of own session only (2 rows, none of the other session)', $$select 1 from acd.attendance_records$$, 2);
select t.count_is('FA10 faculty has no access to fee obligations', $$select 1 from fin.fee_obligations$$, 0);
select t.blocked('FA11 faculty cannot edit programs (ACD-02 is V)', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PF','x','UG',4)$$);
select t.count_is('FA12 audit trail shows faculty only their own actions (audit.read.own)', $$select 1 from audit.v_change_log$$, 1);
select t.count_is('FA13 faculty cannot read raw audit payloads', $$select 1 from audit.change_log$$, 0);

rollback;
update lms.assignments set status='DRAFT' where id='a9000000-0000-0000-0000-000000000001';
\echo ---- STUDENT
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000016','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('ST1 student sees only own obligations', $$select 1 from fin.fee_obligations$$, 1);
select t.count_is('ST2 student sees only themself in stu.students', $$select 1 from stu.students$$, 1);
select t.count_is('ST3 student sees no other persons', $$select 1 from core.persons where id <> 'e0000000-0000-0000-0000-000000000001'$$, 0);
select t.count_is('ST4 draft assignment of own section is hidden', $$select 1 from lms.assignments$$, 0);
select t.count_is('ST5 student sees exam in registration phase but not the PLANNED one', $$select 1 from exm.examinations$$, 1);
select t.allowed('ST6 student updates own exam registration to SUBMITTED', $$update exm.registrations set status='SUBMITTED' where id='e1000000-0000-0000-0000-000000000001'$$);
select t.blocked('ST7 student approves own registration', $$update exm.registrations set status='APPROVED' where id='e1000000-0000-0000-0000-000000000001'$$);
select t.blocked('ST8 student registers ANOTHER student', $$insert into exm.registrations (examination_id,student_id) values ('ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002')$$);
select t.blocked('ST9 student calls next_number', $$select core.next_number('RECEIPT')$$);
select t.count_is('ST10 student cannot read attendance of others (own only)', $$select 1 from acd.attendance_records$$, 1);
select t.blocked('ST11 student creates a program', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PS','x','UG',4)$$);
select t.count_is('ST12 student sees no payroll', $$select 1 from pay.payslips$$, 0);
select t.allowed('ST13 student starts a payment for own obligation', $$select fin.start_payment('0b000000-0000-0000-0000-000000000001', null, 100)$$);
select t.blocked('ST14 student starts a payment for another student''s obligation', $$select fin.start_payment('0b000000-0000-0000-0000-000000000002', null, 100)$$);

rollback;
update lms.assignments set status='PUBLISHED' where id='a9000000-0000-0000-0000-000000000001';
\echo ---- STUDENT (after publish)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000016','aaaaaaaa-0000-0000-0000-000000000001');
select t.count_is('ST15 published assignment of own section is visible, other section''s is not', $$select 1 from lms.assignments$$, 1);
select t.count_is('ST16 student sees own student_assignment only', $$select 1 from lms.student_assignments$$, 1);
rollback;
\echo ---- PARENT
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000018','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('PA1 parent sees linked child''s obligations only', $$select 1 from fin.fee_obligations$$, 1);
select t.count_is('PA2 parent sees linked child in stu.students', $$select 1 from stu.students$$, 1);
select t.blocked('PA3 parent cannot register the child for exams (V only)', $$insert into exm.registrations (examination_id,student_id) values ('ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001')$$);
select t.blocked('PA4 parent cannot pay while can_pay_fees is false', $$select fin.start_payment('0b000000-0000-0000-0000-000000000001', null, 100)$$);
select t.count_is('PA5 parent sees child''s attendance', $$select 1 from acd.attendance_records$$, 1);

rollback;
update stu.guardianships set can_pay_fees = true where student_id='5d000000-0000-0000-0000-000000000001';
\echo ---- PARENT (can_pay_fees)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000018','aaaaaaaa-0000-0000-0000-000000000001');
select t.allowed('PA6 parent may pay once can_pay_fees is true', $$select fin.start_payment('0b000000-0000-0000-0000-000000000001', null, 100)$$);
select t.blocked('PA7 parent cannot pay for an unlinked student', $$select fin.start_payment('0b000000-0000-0000-0000-000000000002', null, 100)$$);
rollback;
insert into exm.registrations (id,tenant_id,examination_id,student_id) values ('e1000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002');
insert into exm.results (id,tenant_id,registration_id,examination_id,student_id,overall_status) values
 ('e2000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','e1000000-0000-0000-0000-000000000001','ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','PASS'),('e2000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','e1000000-0000-0000-0000-000000000002','ee000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002','PASS');
\echo ---- EXAM CELL
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000010','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('EX1 exam cell processes the result (W)', $$update exm.results set status='PROCESSED' where id='e2000000-0000-0000-0000-000000000001'$$);
select t.blocked('EX2 exam cell cannot approve (EXM-08 W only)', $$update exm.results set status='APPROVED' where id='e2000000-0000-0000-0000-000000000001'$$);
select t.blocked('EX3 exam cell cannot publish', $$update exm.results set status='PUBLISHED' where id='e2000000-0000-0000-0000-000000000001'$$);
select t.count_is('EX4 exam cell reads registrations (EXM-01 W)', $$select 1 from exm.registrations$$, 2);
select t.allowed('EX5 exam cell generates a hall ticket', $$insert into exm.hall_tickets (registration_id,student_id,status) values ('e1000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001','GENERATED')$$);
select t.allowed('EX5b exam cell may publish hall tickets (EXM-03 W/P)', $$insert into exm.hall_tickets (registration_id,student_id,status) values ('e1000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000002','PUBLISHED')$$);

rollback;
update exm.results set status='PROCESSED' where id in ('e2000000-0000-0000-0000-000000000001','e2000000-0000-0000-0000-000000000002');
\echo ---- EXAM CONTROLLER
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000009','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('EX6 controller approves the result (A)', $$update exm.results set status='APPROVED' where id='e2000000-0000-0000-0000-000000000001'$$);
select t.blocked('EX7 controller cannot change marks, only workflow fields', $$update exm.results set total_marks=100 where id='e2000000-0000-0000-0000-000000000001'$$);

rollback;
update exm.results set status='APPROVED' where id='e2000000-0000-0000-0000-000000000001';
\echo ---- RESULTS CHAIN: PUBLISH
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001');
select t.allowed('SD6 principal publishes (different person from the approver)', $$update exm.results set status='PUBLISHED' where id='e2000000-0000-0000-0000-000000000001'$$);
rollback;
\echo ---- RESULT VISIBLE TO STUDENT
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000016','aaaaaaaa-0000-0000-0000-000000000001');
select t.count_is('SD7 student sees own result only after... (own row)', $$select 1 from exm.results$$, 1);
rollback;
\echo ---- SEPARATION OF DUTIES - RESULTS (one person)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('SD2 tenant admin approves a result someone else processed', $$update exm.results set status='APPROVED' where id='e2000000-0000-0000-0000-000000000002'$$);
select t.blocked('SD3 SAME person publishes what they approved (Marks Approver != Result Publisher)', $$update exm.results set status='PUBLISHED' where id='e2000000-0000-0000-0000-000000000002'$$);

rollback;
\echo ---- VALUATOR (anonymised, assigned only)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000011','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('VA1 valuator sees no exam registrations (EXM-01 is -)', $$select 1 from exm.registrations$$, 0);
select t.count_is('VA2 valuator sees no students', $$select 1 from stu.students$$, 0);
select t.count_is('VA3 valuator sees no results', $$select 1 from exm.results$$, 0);
select t.count_is('VA4 valuator sees no scripts unless assigned', $$select 1 from exm.answer_scripts$$, 0);

rollback;
\echo ---- ACCOUNTANT
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000013','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('FN1 accountant allocates a payment (FIN-02 W/X)', $$select fin.allocate_payment('9a000000-0000-0000-0000-000000000001','0b000000-0000-0000-0000-000000000001',400)$$);
select t.val_is('FN2 balance rolled up', $$select paid_amount::text||'/'||status from fin.fee_obligations where obligation_no='OB1'$$, '400.00/PARTIALLY_PAID');
select t.no_effect('FN3 allocations cannot be updated', $$update fin.payment_allocations set amount=900$$);
select t.blocked('FN4 accountant overwrites paid_amount', $$update fin.fee_obligations set paid_amount=0 where obligation_no='OB1'$$);
select t.blocked('FN5 over-allocation', $$select fin.allocate_payment('9a000000-0000-0000-0000-000000000001','0b000000-0000-0000-0000-000000000001',700)$$);
select t.blocked('FN6 allocation to another student''s obligation', $$select fin.allocate_payment('9a000000-0000-0000-0000-000000000001','0b000000-0000-0000-0000-000000000002',100)$$);
select t.allowed('FN7 accountant creates an expense (FIN-03 W)', $$insert into fin.expenses (id,college_id,expense_no,expense_date,total_amount,status) values ('ef000000-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EXP1',current_date,500,'DRAFT')$$);
select t.allowed('FN8 accountant submits it', $$update fin.expenses set status='SUBMITTED' where expense_no='EXP1'$$);
select t.blocked('FN9 accountant cannot approve own expense (FIN-03 A is Principal)', $$update fin.expenses set status='APPROVED' where expense_no='EXP1'$$);
select t.count_is('FN10 accountant cannot read other colleges', $$select 1 from fin.fee_obligations where student_id='5d000000-0000-0000-0000-000000000003'$$, 0);
select t.count_is('FN11 accountant has no HR/payroll access to compensation', $$select 1 from pay.employee_compensations$$, 0);

rollback;
insert into fin.expenses (id,tenant_id,college_id,expense_no,expense_date,total_amount,status) values ('ef000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EXP2',current_date,900,'SUBMITTED'),('ef000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EXP5',current_date,70,'SUBMITTED');
\echo ---- PRINCIPAL APPROVES EXPENSE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('FN12 principal approves the expense (FIN-03 A)', $$update fin.expenses set status='APPROVED' where expense_no='EXP2'$$);
select t.blocked('FN13 principal cannot execute payment', $$update fin.expenses set status='PAID' where expense_no='EXP2'$$);
select t.blocked('FN14 principal cannot edit amounts, only workflow fields', $$update fin.expenses set total_amount=1 where expense_no='EXP2'$$);

rollback;
update fin.expenses set status='APPROVED' where expense_no='EXP2';
\echo ---- FINANCE MANAGER PAYS
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000012','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('FN15 finance manager executes the payment (create != approve != pay)', $$update fin.expenses set status='PAID' where expense_no='EXP2'$$);
select t.blocked('FN16 finance manager cannot approve (FIN-03 M does not include approve)', $$update fin.expenses set status='APPROVED' where expense_no='EXP5'$$);

rollback;
\echo ---- SEPARATION OF DUTIES - EXPENSES
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('SD8 tenant admin creates+submits an expense', $$insert into fin.expenses (id,college_id,expense_no,expense_date,total_amount,status) values ('ef000000-0000-0000-0000-000000000003','c1000000-0000-0000-0000-000000000001','EXP3',current_date,100,'SUBMITTED')$$);
select t.blocked('SD9 same person approves what they created (Create != Approve)', $$update fin.expenses set status='APPROVED' where expense_no='EXP3'$$);

rollback;
\echo ---- EMPLOYEE SELF-SERVICE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000007','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('HR1 employee applies for own leave (HRM-02 Employee W)', $$insert into hrm.leave_applications (employee_id,leave_type_id,from_date,to_date,total_days,status) values ('e5000000-0000-0000-0000-000000000001','1b000000-0000-0000-0000-000000000001','2026-10-10','2026-10-11',2,'SUBMITTED')$$);
select t.blocked('HR2 employee applies leave for a colleague', $$insert into hrm.leave_applications (employee_id,leave_type_id,from_date,to_date,total_days) values ('e5000000-0000-0000-0000-000000000002','1b000000-0000-0000-0000-000000000001','2026-10-10','2026-10-11',2)$$);
select t.count_is('HR3 employee sees only own HR rows', $$select 1 from hrm.employees$$, 1);
select t.blocked('HR4 employee approves own leave', $$update hrm.leave_applications set status='APPROVED'$$);

rollback;
insert into hrm.leave_applications (id,tenant_id,employee_id,leave_type_id,from_date,to_date,total_days,status) values
 ('1e000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001','1b000000-0000-0000-0000-000000000001','2026-10-10','2026-10-11',2,'SUBMITTED'),
 ('1e000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000002','1b000000-0000-0000-0000-000000000001','2026-10-10','2026-10-11',2,'SUBMITTED'),
 ('1e000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000004','1b000000-0000-0000-0000-000000000001','2026-10-10','2026-10-11',2,'SUBMITTED');
\echo ---- HOD LEAVE APPROVAL
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('HR5 HOD sees leave of own department only', $$select 1 from hrm.leave_applications$$, 1);
select t.allowed('HR6 HOD approves a CSE employee''s leave (HRM-02 A)', $$update hrm.leave_applications set status='APPROVED' where id='1e000000-0000-0000-0000-000000000001'$$);
select t.no_effect('HR7 HOD cannot approve leave of another department', $$update hrm.leave_applications set status='APPROVED' where id='1e000000-0000-0000-0000-000000000002'$$);
select t.blocked('HR8 HOD cannot edit leave dates (workflow fields only)', $$update hrm.leave_applications set total_days=9 where id='1e000000-0000-0000-0000-000000000001'$$);

rollback;
\echo ---- SELF-APPROVAL BLOCKED
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001');

select t.blocked('HR9 principal approves OWN leave (block_self)', $$update hrm.leave_applications set status='APPROVED' where id='1e000000-0000-0000-0000-000000000003'$$);
select t.allowed('HR10 principal approves another employee''s leave', $$update hrm.leave_applications set status='APPROVED' where id='1e000000-0000-0000-0000-000000000002'$$);

rollback;
\echo ---- HR MANAGER
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000014','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('HR11 HR manager reads employees of own college', $$select 1 from hrm.employees$$, 5);
select t.count_is('HR12 HR manager reads person PII (core.pii.read)', $$select 1 from core.persons where primary_email is not null$$, 9);
select t.count_is('HR13 HR manager has no finance access', $$select 1 from fin.fee_obligations$$, 0);
select t.count_is('HR14 HR manager has no exam access', $$select 1 from exm.results$$, 0);

rollback;
\echo ---- AUDIT: COLLEGE ADMIN
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('AU1 college admin reads the payload-free trail', $$select 1 from audit.v_change_log where table_name='persons'$$, 9);
select t.count_is('AU2 college admin cannot read raw audit payloads', $$select 1 from audit.change_log$$, 0);
select t.blocked('AU3 college admin forges an audit event', $$insert into audit.events (action) values ('X')$$);

rollback;
\echo ---- AUDIT: AUDITOR
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000015','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('AU4 auditor reads payloads (matrix: Auditor M)', $$select 1 from audit.change_log where table_name='persons'$$, 9);
select t.val_is('AU5 email is redacted in the payload', $$select new_data->>'primary_email' from audit.change_log where table_name='persons' limit 1$$, '[REDACTED]');
select t.blocked('AU6 auditor edits the audit trail', $$update audit.change_log set action='X'$$);
select t.count_is('AU7 auditor cannot read fee obligations', $$select 1 from fin.fee_obligations$$, 0);
select t.blocked('AU8 auditor creates a program', $$insert into acd.programs (college_id,department_id,code,name,level,duration_years) values ('c1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000001','PA','x','UG',4)$$);

rollback;
\echo ---- ROLE ADMINISTRATION
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('RA1 college admin assigns HOD at own college', $$insert into iam.role_assignments (user_id,role_id,college_id,department_id) select 'a1000000-0000-0000-0000-000000000021', id, 'c1000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000002' from iam.roles where code='HOD' and college_id is null$$);
select t.blocked('RA2 college admin assigns a role at ANOTHER college', $$insert into iam.role_assignments (user_id,role_id,college_id) select 'a1000000-0000-0000-0000-000000000021', id, 'c2000000-0000-0000-0000-000000000002' from iam.roles where code='FACULTY' and college_id is null$$);
select t.blocked('RA3 college admin assigns INSTITUTION_ADMIN', $$insert into iam.role_assignments (user_id,role_id,college_id) select 'a1000000-0000-0000-0000-000000000021', id, 'c1000000-0000-0000-0000-000000000001' from iam.roles where code='INSTITUTION_ADMIN' and college_id is null$$);
select t.blocked('RA4 college admin assigns AUDITOR', $$insert into iam.role_assignments (user_id,role_id,college_id) select 'a1000000-0000-0000-0000-000000000021', id, 'c1000000-0000-0000-0000-000000000001' from iam.roles where code='AUDITOR' and college_id is null$$);
select t.blocked('RA5 college admin assigns an institute-wide role', $$insert into iam.role_assignments (user_id,role_id) select 'a1000000-0000-0000-0000-000000000021', id from iam.roles where code='FACULTY' and college_id is null$$);
select t.blocked('RA6 college admin grants self a role', $$insert into iam.role_assignments (user_id,role_id,college_id) select 'a1000000-0000-0000-0000-000000000003', id, 'c1000000-0000-0000-0000-000000000001' from iam.roles where code='PRINCIPAL' and college_id is null$$);
select t.blocked('RA7 add a permission to a system role', $$insert into iam.role_permissions (role_id,permission_id) select r.id, p.id from iam.roles r, iam.permissions p where r.code='FACULTY' and r.college_id is null and p.code='fin03.approve'$$);
select t.blocked('RA8a college admin creates an institute-wide custom role', $$insert into iam.roles (code,name) values ('LAB0','x')$$);
select t.allowed('RA8 college admin creates a college-level custom role', $$insert into iam.roles (code,name,college_id) values ('LAB','Lab assistant','c1000000-0000-0000-0000-000000000001')$$);
select t.blocked('RA9 custom role with a permission the admin lacks', $$insert into iam.role_permissions (role_id,permission_id) select r.id, p.id from iam.roles r, iam.permissions p where r.code='LAB' and p.code='fin06.approve'$$);
select t.allowed('RA9b custom role with a permission the admin holds', $$insert into iam.role_permissions (role_id,permission_id) select r.id, p.id from iam.roles r, iam.permissions p where r.code='LAB' and p.code='acd04.view'$$);

rollback;
\echo ---- ROLE ADMINISTRATION (institute)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('RA10 institution admin assigns COLLEGE_ADMIN at college 2', $$insert into iam.role_assignments (user_id,role_id,college_id) select 'a1000000-0000-0000-0000-000000000021', id, 'c2000000-0000-0000-0000-000000000002' from iam.roles where code='COLLEGE_ADMIN' and college_id is null$$);
select t.allowed('RA11 institution admin assigns AUDITOR', $$insert into iam.role_assignments (user_id,role_id) select 'a1000000-0000-0000-0000-000000000021', id from iam.roles where code='AUDITOR' and college_id is null$$);
select t.blocked('RA12 institution admin assigns TENANT_ADMIN', $$insert into iam.role_assignments (user_id,role_id) select 'a1000000-0000-0000-0000-000000000021', id from iam.roles where code='TENANT_ADMIN' and college_id is null$$);

rollback;
\echo ---- DELEGATION
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('DL1 principal delegates PRINCIPAL for 7 days', $$insert into iam.delegations (from_user_id,to_user_id,role_id,valid_from,valid_to,reason) select 'a1000000-0000-0000-0000-000000000005','a1000000-0000-0000-0000-000000000006', id, now() - interval '1 hour', now() + interval '7 days', 'leave' from iam.roles where code='PRINCIPAL' and college_id is null$$);
select t.blocked('DL2 delegation to self', $$insert into iam.delegations (from_user_id,to_user_id,role_id,valid_from,valid_to) select 'a1000000-0000-0000-0000-000000000005','a1000000-0000-0000-0000-000000000005', id, now(), now() + interval '1 day' from iam.roles where code='PRINCIPAL' and college_id is null$$);
select t.blocked('DL3 delegate a role you do not hold', $$insert into iam.delegations (from_user_id,to_user_id,role_id,valid_from,valid_to) select 'a1000000-0000-0000-0000-000000000005','a1000000-0000-0000-0000-000000000006', id, now(), now() + interval '1 day' from iam.roles where code='TENANT_ADMIN' and college_id is null$$);
select t.blocked('DL4 delegation longer than 90 days', $$insert into iam.delegations (from_user_id,to_user_id,role_id,valid_from,valid_to) select 'a1000000-0000-0000-0000-000000000005','a1000000-0000-0000-0000-000000000006', id, now(), now() + interval '120 days' from iam.roles where code='PRINCIPAL' and college_id is null$$);
select t.blocked('DL5 delegate on someone else''s behalf', $$insert into iam.delegations (from_user_id,to_user_id,role_id,valid_from,valid_to) select 'a1000000-0000-0000-0000-000000000003','a1000000-0000-0000-0000-000000000006', id, now(), now() + interval '1 day' from iam.roles where code='COLLEGE_ADMIN' and college_id is null$$);

rollback;
insert into iam.delegations (tenant_id,from_user_id,to_user_id,role_id,valid_from,valid_to) select 'aaaaaaaa-0000-0000-0000-000000000001','a1000000-0000-0000-0000-000000000005','a1000000-0000-0000-0000-000000000006', id, now() - interval '1 hour', now() + interval '7 days' from iam.roles where tenant_id='aaaaaaaa-0000-0000-0000-000000000001' and code='PRINCIPAL' and college_id is null;
insert into fin.expenses (id,tenant_id,college_id,expense_no,expense_date,total_amount,status) values ('ef000000-0000-0000-0000-000000000004','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EXP4',current_date,50,'SUBMITTED');
\echo ---- DELEGATED AUTHORITY IS EFFECTIVE
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001');

select t.allowed('DL6 HOD, acting on delegated Principal authority, approves an expense (FIN-03 A)', $$update fin.expenses set status='APPROVED' where expense_no='EXP4'$$);

rollback;
update iam.delegations set status='REVOKED' where tenant_id='aaaaaaaa-0000-0000-0000-000000000001';
update fin.expenses set status='SUBMITTED' where expense_no='EXP4';
\echo ---- DELEGATION REVOKED
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000006','aaaaaaaa-0000-0000-0000-000000000001');
select t.no_effect('DL7 after revocation the HOD can no longer approve', $$update fin.expenses set status='APPROVED' where expense_no='EXP4'$$);
rollback;
insert into iam.api_clients (id,tenant_id,user_id,name) values ('ac000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','a1000000-0000-0000-0000-000000000020','Reporting integration');
insert into iam.api_client_grants (tenant_id,client_id,permission_id,college_id) select 'aaaaaaaa-0000-0000-0000-000000000001','ac000000-0000-0000-0000-000000000001', id, 'c1000000-0000-0000-0000-000000000001' from iam.permissions where code in ('fin02.view','fin02.view.all');
\echo ---- API CLIENT (R27)
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000020','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('API1 client reads fee obligations of its college only (grant fin02.view.all @ college 1)', $$select 1 from fin.fee_obligations$$, 2);
select t.blocked('API2 client cannot write (no write grant)', $$insert into fin.payments (student_id,payment_no,amount) values ('5d000000-0000-0000-0000-000000000001','PX',1)$$);
select t.count_is('API3 client has no access to HR', $$select 1 from hrm.employees$$, 0);
select t.count_is('API4 client has no access to students', $$select 1 from stu.students$$, 0);

rollback;
\echo ---- COMMUNICATION MANAGER
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000019','aaaaaaaa-0000-0000-0000-000000000001');

select t.count_is('CM1 communication manager sees no finance', $$select 1 from fin.fee_obligations$$, 0);
select t.allowed('CM2 communication manager drafts a message (CAM-02 M)', $$insert into cam.messages (college_id,channel,subject,body,status) values ('c1000000-0000-0000-0000-000000000001','EMAIL','Hi','Hello','DRAFT')$$);
select t.allowed('CM3 communication manager queues it (manage)', $$insert into cam.messages (id,college_id,channel,subject,body,status) values ('3e5a0000-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','EMAIL','Hi','Hello','QUEUED')$$);
select t.count_is('CM4 reads names via the directory view (not PII)', $$select 1 from core.v_person_directory$$, 9);
select t.count_is('CM5 cannot read person PII table', $$select 1 from core.persons$$, 0);

rollback;
\echo ---- FACULTY MESSAGING
begin; set local role authenticated; select t.jwt('a1000000-0000-0000-0000-000000000007','aaaaaaaa-0000-0000-0000-000000000001');
select t.allowed('CM6 faculty drafts a message (CAM-02 W)', $$insert into cam.messages (college_id,channel,subject,body,status) values ('c1000000-0000-0000-0000-000000000001','EMAIL','Hi','Hello','DRAFT')$$);
select t.blocked('CM7 faculty cannot send it (needs approve/manage)', $$insert into cam.messages (college_id,channel,subject,body,status) values ('c1000000-0000-0000-0000-000000000001','EMAIL','Hi','Hello','SENT')$$);
rollback;
\echo ---- TENANT ISOLATION
begin; set local role authenticated; select t.jwt('b1000000-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000001');

select t.count_is('IS1 tenant B sees no tenant A students', $$select 1 from stu.students$$, 0);
select t.count_is('IS2 tenant B sees only its college', $$select 1 from core.colleges$$, 1);
select t.blocked('IS3 tenant B row pointing at tenant A parent', $$insert into core.departments (college_id,code,name) values ('c1000000-0000-0000-0000-000000000001','X','cross')$$);
select t.count_is('IS4 tenant B sees no tenant A audit rows', $$select 1 from audit.change_log where tenant_id='aaaaaaaa-0000-0000-0000-000000000001'$$, 0);

rollback;

insert into exm.registrations (id,tenant_id,examination_id,student_id) values ('e1000000-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000001','ee000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000001');
insert into core.campuses (id,tenant_id,college_id,code,name) values ('ca000000-0000-0000-0000-000000000091','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','M','Main'),('ca000000-0000-0000-0000-000000000092','aaaaaaaa-0000-0000-0000-000000000001','c2000000-0000-0000-0000-000000000002','X','Other');
insert into core.rooms (id,tenant_id,campus_id,code) values ('3e000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','ca000000-0000-0000-0000-000000000091','R1'),('3e000000-0000-0000-0000-000000000002','aaaaaaaa-0000-0000-0000-000000000001','ca000000-0000-0000-0000-000000000092','R2-other-college');
insert into acd.timetables (id,tenant_id,college_id,term_id,name) values ('7a000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','7e000000-0000-0000-0000-000000000001','TT');
insert into acd.timetable_versions (id,tenant_id,timetable_id,version_no) values ('7b000000-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001','7a000000-0000-0000-0000-000000000001',1);
\echo ---- CROSS-ENTITY CONSISTENCY (superuser: constraints apply to everyone)
select t.blocked('C1 hall ticket names a different student than its registration', $$insert into exm.hall_tickets (tenant_id,registration_id,student_id) values ('aaaaaaaa-0000-0000-0000-000000000001','e1000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002')$$);
select t.blocked('C2 result names a different student than its registration', $$insert into exm.results (tenant_id,registration_id,examination_id,student_id,overall_status) values ('aaaaaaaa-0000-0000-0000-000000000001','e1000000-0000-0000-0000-000000000003','ee000000-0000-0000-0000-000000000002','5d000000-0000-0000-0000-000000000002','PASS')$$);
select t.blocked('C3 submission names a different student than its student_assignment', $$insert into lms.submissions (tenant_id,student_assignment_id,student_id) values ('aaaaaaaa-0000-0000-0000-000000000001','a8000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002')$$);
select t.allowed('C4 submission with the right student', $$insert into lms.submissions (tenant_id,student_assignment_id,student_id) values ('aaaaaaaa-0000-0000-0000-000000000001','a8000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000001')$$);
select t.blocked('C5 grade names a different student', $$insert into lms.grades (tenant_id,student_assignment_id,student_id) values ('aaaaaaaa-0000-0000-0000-000000000001','a8000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002')$$);
select t.blocked('C6 receipt for a payment of another student', $$insert into fin.receipts (tenant_id,payment_id,student_id,receipt_no,receipt_date,total_amount) values ('aaaaaaaa-0000-0000-0000-000000000001','9a000000-0000-0000-0000-000000000001','5d000000-0000-0000-0000-000000000002','RC1',current_date,10)$$);
\echo ---- TIMETABLE RULES
select t.allowed('T1 valid entry', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,section_id,subject_id,employee_id,room_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001','3e000000-0000-0000-0000-000000000001',1,'09:00','10:00')$$);
select t.blocked('T2 faculty double-booked', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,employee_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001',1,'09:30','10:30')$$);
select t.blocked('T3 room double-booked', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,room_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','3e000000-0000-0000-0000-000000000001',1,'09:59','11:00')$$);
select t.allowed('T4 back-to-back is fine', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,employee_id,room_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000001','3e000000-0000-0000-0000-000000000001',1,'10:00','11:00')$$);
select t.blocked('T5 subject not part of the section course', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,section_id,subject_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002',2,'09:00','10:00')$$);
select t.blocked('T6 employee not assigned to the section', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,section_id,employee_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','5e000000-0000-0000-0000-000000000001','e5000000-0000-0000-0000-000000000002',3,'09:00','10:00')$$);
select t.blocked('T7 room belongs to another college', $$insert into acd.timetable_entries (tenant_id,timetable_version_id,room_id,day_of_week,start_time,end_time) values ('aaaaaaaa-0000-0000-0000-000000000001','7b000000-0000-0000-0000-000000000001','3e000000-0000-0000-0000-000000000002',4,'09:00','10:00')$$);

\echo ---- FUNCTION PRIVILEGES
select case when bool_and(ok) then 'PASS  G1 anon cannot execute any function in the ERP schemas' else 'FAIL  G1 anon can execute: ' || string_agg(fn, ', ') end
from (select p.oid::regprocedure::text fn, not has_function_privilege('anon', p.oid, 'execute') ok from pg_proc p join pg_namespace n on n.oid=p.pronamespace
      where n.nspname in ('core','stu','iam','cfg','plat','wf','doc','sys','audit','acd','lms','obe','exm','fin','tax','bud','pay','hrm','cam','rpt')) z;
select case when not has_function_privilege('authenticated','iam.seed_system_roles(uuid)','execute') and not has_function_privilege('authenticated','sys.ensure_partitions(integer)','execute')
  and not has_function_privilege('authenticated','audit.log_event(uuid,uuid,text,text,uuid,text,text,inet,jsonb,uuid)','execute') and not has_function_privilege('authenticated','iam.custom_access_token_hook(jsonb)','execute')
  and not has_function_privilege('authenticated','iam.resync_system_roles()','execute')
 then 'PASS  G2 privileged functions not executable by authenticated' else 'FAIL  G2 privileged function exposed' end;
select case when (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.relkind in ('r','p') and n.nspname in ('core','stu','iam','cfg','plat','wf','doc','sys','audit','acd','lms','obe','exm','fin','tax','bud','pay','hrm','cam','rpt')
   and not c.relrowsecurity and not c.relispartition) = 0 then 'PASS  G3 every table has RLS enabled' else 'FAIL  G3 table without RLS' end;
