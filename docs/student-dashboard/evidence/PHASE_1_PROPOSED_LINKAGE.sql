-- ==============================================================================
-- PHASE 1: LINK EXISTING STUDENT ACCOUNT & SEED TAGGED ATTENDANCE SET (DEV ONLY)
-- Target Project Ref: azulbaxszsqxymqmlwfe
-- Existing Auth User ID: 81dd67d3-1d5f-4d8e-8061-a6764861f924
-- Email: student1.campx@gmail.com
-- Display Name: Student One
-- Tag: PHASE1_STUDENT1_DEV
-- Executed & Verified: 2026-11-05
-- ==============================================================================

-- 1. Academic Year in Dev Tenant
INSERT INTO core.academic_years (
    id, tenant_id, code, start_date, end_date, is_current, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000030',
    'a0000000-0000-0000-0000-000000000001',
    'DEV-AY-2026-27',
    '2026-08-01',
    '2027-05-31',
    true,
    1
) ON CONFLICT (id) DO NOTHING;

-- 2. Core Calendar
INSERT INTO core.calendars (
    id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, week_start_day, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000031',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000030',
    'DEV-CAL-2026-27',
    'Dev Academic Calendar 2026-27',
    'ACADEMIC',
    'PUBLISHED',
    1,
    1
) ON CONFLICT (id) DO NOTHING;

-- 3. Calendar Term
INSERT INTO acd.calendar_terms (
    id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000032',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000031',
    1,
    'Term 1 - Fall 2026',
    'SEMESTER',
    '2026-08-01',
    '2026-12-31',
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 4. Academic Program
INSERT INTO acd.programs (
    id, tenant_id, college_id, department_id, code, name, level, duration_years, total_terms, published, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000040',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    '92816069-ec39-45d1-8288-483b3a5a43c0',
    'DEV-BTECH-CSE',
    'B.Tech Computer Science & Engineering',
    'UG',
    4.0,
    8,
    true,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 5. Batch & Section
INSERT INTO acd.batches (
    id, tenant_id, college_id, program_id, code, name, current_term_no, capacity, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000060',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000040',
    'BATCH-2026-A',
    'Section A (2026-2030)',
    1,
    60,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 6. Course & Offering & Section
INSERT INTO acd.courses (
    id, tenant_id, department_id, code, name, credits, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000033',
    'a0000000-0000-0000-0000-000000000001',
    '92816069-ec39-45d1-8288-483b3a5a43c0',
    'CSE-CORE-2026',
    'Computer Science Core Curriculum',
    4.0,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO acd.course_offerings (
    id, tenant_id, college_id, course_id, term_id, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000034',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000033',
    'a0000000-0000-0000-0000-000000000032',
    'OPEN',
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO acd.course_sections (
    id, tenant_id, offering_id, batch_id, section_code, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000034',
    'a0000000-0000-0000-0000-000000000060',
    'SEC-A',
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 7. Timetable & Timetable Version
INSERT INTO acd.timetables (
    id, tenant_id, college_id, term_id, batch_id, name, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000036',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000032',
    'a0000000-0000-0000-0000-000000000060',
    'Fall 2026 Class Timetable',
    'PUBLISHED',
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO acd.timetable_versions (
    id, tenant_id, timetable_id, version_no, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000037',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000036',
    1,
    'PUBLISHED',
    1
) ON CONFLICT (id) DO NOTHING;

-- 8. Subjects (CS101 & CS102)
INSERT INTO acd.subjects (
    id, tenant_id, department_id, code, name, subject_type, credits, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000038',
    'a0000000-0000-0000-0000-000000000001',
    '92816069-ec39-45d1-8288-483b3a5a43c0',
    'CS101',
    'Data Structures & Algorithms',
    'THEORY',
    4.0,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO acd.subjects (
    id, tenant_id, department_id, code, name, subject_type, credits, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000039',
    'a0000000-0000-0000-0000-000000000001',
    '92816069-ec39-45d1-8288-483b3a5a43c0',
    'CS102',
    'Database Management Systems',
    'THEORY',
    4.0,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 9. Timetable Entries
INSERT INTO acd.timetable_entries (
    id, tenant_id, timetable_version_id, section_id, subject_id, day_of_week, start_time, end_time, entry_type, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000071',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000037',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000038',
    1,
    '09:00:00',
    '10:00:00',
    'LECTURE',
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO acd.timetable_entries (
    id, tenant_id, timetable_version_id, section_id, subject_id, day_of_week, start_time, end_time, entry_type, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000072',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000037',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000039',
    2,
    '10:00:00',
    '11:00:00',
    'LECTURE',
    1
) ON CONFLICT (id) DO NOTHING;

-- 10. Core Person for Student1
INSERT INTO core.persons (
    id, tenant_id, first_name, last_name, display_name, primary_email, status, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'a0000000-0000-0000-0000-000000000001',
    'Student',
    'One',
    'Student One',
    'student1.campx@gmail.com',
    'ACTIVE',
    1
) ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    status = EXCLUDED.status;

-- 11. IAM User Profile linking Auth User
INSERT INTO iam.user_profiles (
    id, tenant_id, person_id, college_id, username, email, full_name, status, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'a0000000-0000-0000-0000-000000000002',
    'student1.campx',
    'student1.campx@gmail.com',
    'Student One',
    'ACTIVE',
    1
) ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    person_id = EXCLUDED.person_id,
    college_id = EXCLUDED.college_id,
    full_name = EXCLUDED.full_name,
    status = EXCLUDED.status;

-- 12. IAM Student Role Assignment (Role code 'STUDENT': caa7f8a2-c3eb-46b3-bf2e-ec6640289ecb)
INSERT INTO iam.role_assignments (
    id, tenant_id, user_id, role_id, college_id, valid_from, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610005',
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'caa7f8a2-c3eb-46b3-bf2e-ec6640289ecb',
    'a0000000-0000-0000-0000-000000000002',
    CURRENT_DATE - INTERVAL '30 days',
    1
) ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role_id = EXCLUDED.role_id,
    valid_from = EXCLUDED.valid_from;

-- 13. STM-01 Student Record
INSERT INTO stu.students (
    id, tenant_id, person_id, college_id, program_id,
    enrollment_no, admission_no, admission_date, status, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000040',
    'STU-DEV-2026-0001',
    'ADM-DEV-2026-0001',
    CURRENT_DATE - INTERVAL '60 days',
    'ACTIVE',
    1
) ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    program_id = EXCLUDED.program_id;

-- 14. Batch Enrollment for Student
INSERT INTO acd.batch_students (
    id, tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610006',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000060',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'CSE-001',
    CURRENT_DATE - INTERVAL '60 days',
    'ACTIVE',
    1
) ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status;

-- 15. STM-02 Admission Intake
INSERT INTO stu.admission_intakes (
    tenant_id, id, college_id, program_id, academic_year_id,
    intake_name, intake_code, application_window_start, application_window_end,
    capacity, admitted_count, is_active, fee_required, fee_amount, currency, fee_timing, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000050',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000040',
    'a0000000-0000-0000-0000-000000000030',
    'Dev Fall 2026 CSE Intake',
    'DEV-INTAKE-2026',
    NOW() - INTERVAL '60 days',
    NOW() + INTERVAL '120 days',
    120,
    1,
    true,
    false,
    0,
    'USD',
    'NONE',
    1
) ON CONFLICT (tenant_id, id) DO NOTHING;

-- 16. STM-02 Application (Known state: HANDED_OVER)
INSERT INTO stu.applications (
    tenant_id, id, intake_id, applicant_person_id, application_number,
    status, document_verification_status, payment_status, eligibility_result,
    declaration_accepted, privacy_consent_accepted, submission_timestamp,
    eligibility_overridden, late_submission_exception_granted,
    applicant_data, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a67648610003',
    'a0000000-0000-0000-0000-000000000050',
    '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    'APP-DEV-2026-0001',
    'HANDED_OVER',
    'VERIFIED',
    'PAID',
    'PASS',
    true,
    true,
    NOW() - INTERVAL '30 days',
    false,
    false,
    '{"first_name":"Student","last_name":"One","primary_email":"student1.campx@gmail.com","phone":"+1-555-0199","tag":"PHASE1_STUDENT1_DEV"}'::jsonb,
    1
) ON CONFLICT (tenant_id, id) DO UPDATE SET
    status = EXCLUDED.status,
    document_verification_status = EXCLUDED.document_verification_status;

-- 17. STM-02 Admission Decision
INSERT INTO stu.admission_decisions (
    tenant_id, id, application_id, decision_type, decision_reason_code,
    decision_notes, decided_by, decided_at, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a67648610007',
    '81dd67d3-1d5f-4d8e-8061-a67648610003',
    'OFFER_APPROVED',
    'MERIT',
    'Approved on academic merit (Tag: PHASE1_STUDENT1_DEV)',
    'ba77b8f7-ab32-46bd-85d2-5aade3526880',
    NOW() - INTERVAL '15 days',
    1
) ON CONFLICT (tenant_id, id) DO NOTHING;

-- 18. STM-02 Admission Offer (Accepted)
INSERT INTO stu.admission_offers (
    tenant_id, id, application_id, decision_id, offer_code, offer_terms_version,
    valid_from, valid_to, status, accepted_at, extension_granted, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '81dd67d3-1d5f-4d8e-8061-a67648610004',
    '81dd67d3-1d5f-4d8e-8061-a67648610003',
    '81dd67d3-1d5f-4d8e-8061-a67648610007',
    'OFF-DEV-2026-0001',
    '1.0',
    NOW() - INTERVAL '15 days',
    NOW() + INTERVAL '30 days',
    'ACCEPTED',
    NOW() - INTERVAL '10 days',
    false,
    1
) ON CONFLICT (tenant_id, id) DO UPDATE SET
    status = EXCLUDED.status;

-- 19. Small Tagged Attendance Set: Sessions (Closed)
-- Session 1 (CS101, Yesterday)
INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, session_date, start_time, end_time, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000081',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000071',
    CURRENT_DATE - INTERVAL '1 day',
    '09:00:00',
    '10:00:00',
    'CLOSED',
    1
) ON CONFLICT (id) DO NOTHING;

-- Session 2 (CS101, 2 Days Ago)
INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, session_date, start_time, end_time, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000082',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000071',
    CURRENT_DATE - INTERVAL '2 days',
    '09:00:00',
    '10:00:00',
    'CLOSED',
    1
) ON CONFLICT (id) DO NOTHING;

-- Session 3 (CS101, 3 Days Ago)
INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, session_date, start_time, end_time, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000083',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000071',
    CURRENT_DATE - INTERVAL '3 days',
    '09:00:00',
    '10:00:00',
    'CLOSED',
    1
) ON CONFLICT (id) DO NOTHING;

-- Session 4 (CS102, Yesterday)
INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, session_date, start_time, end_time, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000084',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000072',
    CURRENT_DATE - INTERVAL '1 day',
    '10:00:00',
    '11:00:00',
    'CLOSED',
    1
) ON CONFLICT (id) DO NOTHING;

-- Session 5 (CS102, 2 Days Ago)
INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, session_date, start_time, end_time, status, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000085',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000035',
    'a0000000-0000-0000-0000-000000000072',
    CURRENT_DATE - INTERVAL '2 days',
    '10:00:00',
    '11:00:00',
    'CLOSED',
    1
) ON CONFLICT (id) DO NOTHING;

-- 20. Small Tagged Attendance Set: Records for student1
-- Record 1: CS101 Session 1 -> PRESENT
INSERT INTO acd.attendance_records (
    id, tenant_id, session_id, student_id, status, method, marked_at, remarks, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610011',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000081',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'PRESENT',
    'MANUAL',
    NOW() - INTERVAL '1 day',
    'Tag: PHASE1_STUDENT1_DEV',
    1
) ON CONFLICT (id) DO NOTHING;

-- Record 2: CS101 Session 2 -> PRESENT
INSERT INTO acd.attendance_records (
    id, tenant_id, session_id, student_id, status, method, marked_at, remarks, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610012',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000082',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'PRESENT',
    'MANUAL',
    NOW() - INTERVAL '2 days',
    'Tag: PHASE1_STUDENT1_DEV',
    1
) ON CONFLICT (id) DO NOTHING;

-- Record 3: CS101 Session 3 -> ABSENT
INSERT INTO acd.attendance_records (
    id, tenant_id, session_id, student_id, status, method, marked_at, remarks, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610013',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000083',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'ABSENT',
    'MANUAL',
    NOW() - INTERVAL '3 days',
    'Tag: PHASE1_STUDENT1_DEV',
    1
) ON CONFLICT (id) DO NOTHING;

-- Record 4: CS102 Session 4 -> PRESENT
INSERT INTO acd.attendance_records (
    id, tenant_id, session_id, student_id, status, method, marked_at, remarks, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610014',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000084',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'PRESENT',
    'MANUAL',
    NOW() - INTERVAL '1 day',
    'Tag: PHASE1_STUDENT1_DEV',
    1
) ON CONFLICT (id) DO NOTHING;

-- Record 5: CS102 Session 5 -> PRESENT
INSERT INTO acd.attendance_records (
    id, tenant_id, session_id, student_id, status, method, marked_at, remarks, row_version
) VALUES (
    '81dd67d3-1d5f-4d8e-8061-a67648610015',
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000085',
    '81dd67d3-1d5f-4d8e-8061-a67648610002',
    'PRESENT',
    'MANUAL',
    NOW() - INTERVAL '2 days',
    'Tag: PHASE1_STUDENT1_DEV',
    1
) ON CONFLICT (id) DO NOTHING;

-- ==============================================================================
-- ROLLBACK SCRIPT FOR PHASE 1 LINKAGE & ATTENDANCE SET
-- ==============================================================================
/*
DELETE FROM acd.attendance_records WHERE id IN (
    '81dd67d3-1d5f-4d8e-8061-a67648610011',
    '81dd67d3-1d5f-4d8e-8061-a67648610012',
    '81dd67d3-1d5f-4d8e-8061-a67648610013',
    '81dd67d3-1d5f-4d8e-8061-a67648610014',
    '81dd67d3-1d5f-4d8e-8061-a67648610015'
);
DELETE FROM acd.attendance_sessions WHERE id IN (
    'a0000000-0000-0000-0000-000000000081',
    'a0000000-0000-0000-0000-000000000082',
    'a0000000-0000-0000-0000-000000000083',
    'a0000000-0000-0000-0000-000000000084',
    'a0000000-0000-0000-0000-000000000085'
);
DELETE FROM acd.timetable_entries WHERE id IN (
    'a0000000-0000-0000-0000-000000000071',
    'a0000000-0000-0000-0000-000000000072'
);
DELETE FROM acd.subjects WHERE id IN (
    'a0000000-0000-0000-0000-000000000038',
    'a0000000-0000-0000-0000-000000000039'
);
DELETE FROM acd.timetable_versions WHERE id = 'a0000000-0000-0000-0000-000000000037';
DELETE FROM acd.timetables WHERE id = 'a0000000-0000-0000-0000-000000000036';
DELETE FROM acd.course_sections WHERE id = 'a0000000-0000-0000-0000-000000000035';
DELETE FROM acd.course_offerings WHERE id = 'a0000000-0000-0000-0000-000000000034';
DELETE FROM acd.courses WHERE id = 'a0000000-0000-0000-0000-000000000033';
DELETE FROM acd.batch_students WHERE id = '81dd67d3-1d5f-4d8e-8061-a67648610006';
DELETE FROM acd.batches WHERE id = 'a0000000-0000-0000-0000-000000000060';
DELETE FROM stu.admission_offers WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001' AND id = '81dd67d3-1d5f-4d8e-8061-a67648610004';
DELETE FROM stu.admission_decisions WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001' AND id = '81dd67d3-1d5f-4d8e-8061-a67648610007';
DELETE FROM stu.applications WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001' AND id = '81dd67d3-1d5f-4d8e-8061-a67648610003';
DELETE FROM stu.admission_intakes WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001' AND id = 'a0000000-0000-0000-0000-000000000050';
DELETE FROM stu.students WHERE id = '81dd67d3-1d5f-4d8e-8061-a67648610002';
DELETE FROM iam.role_assignments WHERE id = '81dd67d3-1d5f-4d8e-8061-a67648610005';
DELETE FROM iam.user_profiles WHERE id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
DELETE FROM core.persons WHERE id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
DELETE FROM acd.programs WHERE id = 'a0000000-0000-0000-0000-000000000040';
DELETE FROM acd.calendar_terms WHERE id = 'a0000000-0000-0000-0000-000000000032';
DELETE FROM core.calendars WHERE id = 'a0000000-0000-0000-0000-000000000031';
DELETE FROM core.academic_years WHERE id = 'a0000000-0000-0000-0000-000000000030';
*/
