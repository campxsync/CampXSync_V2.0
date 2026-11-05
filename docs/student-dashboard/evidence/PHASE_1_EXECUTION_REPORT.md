# Phase 1 Execution Report: Link Existing Student Account & Seed Tagged Attendance Set

**Target Environment:** Dev Project `azulbaxszsqxymqmlwfe` [VERIFIED]  
**Auth User ID:** `81dd67d3-1d5f-4d8e-8061-a6764861f924` (Preserved without modification) [VERIFIED]  
**Email:** `student1.campx@gmail.com`  
**Execution Timestamp:** 2026-11-05  
**SQL Script Location:** [`docs/student-dashboard/evidence/PHASE_1_PROPOSED_LINKAGE.sql`](file:///d:/CampXSync/CampXSync_V2.0/docs/student-dashboard/evidence/PHASE_1_PROPOSED_LINKAGE.sql)  

---

## 1. Safety Compliance
- **Auth User Safety:** Zero auth users created, modified, renamed, deleted, or passwords reset. [VERIFIED]
- **Target Project:** Executed strictly against dev project `azulbaxszsqxymqmlwfe`. Production (`ekspahbsbwdpraaqfqui`) and Staging (`kpwdvvcschankgggitnj`) were NOT touched. [VERIFIED]
- **E2E Isolation:** All seeded rows are assigned to primary dev tenant `a0000000-0000-0000-0000-000000000001` and college `a0000000-0000-0000-0000-000000000002`. Zero interaction with `e2e00000-0000-0000-0000-000000000001` test fixtures. [VERIFIED]
- **Cleanup Tag:** Tagged with `Tag: PHASE1_STUDENT1_DEV` across applications and attendance records. Complete rollback script verified in `PHASE_1_PROPOSED_LINKAGE.sql`. [VERIFIED]

---

## 2. Seeded Rows Summary

| Domain / Schema | Table | Seeded Record / ID | Details |
|---|---|---|---|
| Academic Structure | `core.academic_years` | `a0000000-0000-0000-0000-000000000030` | `DEV-AY-2026-27` (Current: true) |
| Academic Structure | `core.calendars` | `a0000000-0000-0000-0000-000000000031` | `DEV-CAL-2026-27` (PUBLISHED) |
| Academic Structure | `acd.calendar_terms` | `a0000000-0000-0000-0000-000000000032` | `Term 1 - Fall 2026` (ACTIVE) |
| Academic Structure | `acd.programs` | `a0000000-0000-0000-0000-000000000040` | `DEV-BTECH-CSE` (UG, 4 Years) |
| Academic Structure | `acd.batches` | `a0000000-0000-0000-0000-000000000060` | `BATCH-2026-A` (Section A, Term 1) |
| Academic Structure | `acd.courses` | `a0000000-0000-0000-0000-000000000033` | `CSE-CORE-2026` (ACTIVE) |
| Academic Structure | `acd.course_offerings` | `a0000000-0000-0000-0000-000000000034` | Offering for Term 1 (OPEN) |
| Academic Structure | `acd.course_sections` | `a0000000-0000-0000-0000-000000000035` | `SEC-A` (ACTIVE) |
| Academic Structure | `acd.timetables` | `a0000000-0000-0000-0000-000000000036` | Fall 2026 Timetable (PUBLISHED) |
| Academic Structure | `acd.timetable_versions` | `a0000000-0000-0000-0000-000000000037` | Version 1 (PUBLISHED) |
| Academic Structure | `acd.subjects` | `a0000000-0000-0000-0000-000000000038` | `CS101` (Data Structures & Algorithms, 4 credits) |
| Academic Structure | `acd.subjects` | `a0000000-0000-0000-0000-000000000039` | `CS102` (Database Management Systems, 4 credits) |
| Academic Structure | `acd.timetable_entries` | `a0000000-0000-0000-0000-000000000071` | `CS101` Lecture, Mon 09:00–10:00 |
| Academic Structure | `acd.timetable_entries` | `a0000000-0000-0000-0000-000000000072` | `CS102` Lecture, Tue 10:00–11:00 |
| Identity & Profile | `core.persons` | `81dd67d3-1d5f-4d8e-8061-a6764861f924` | Student One (`student1.campx@gmail.com`) |
| IAM Profile | `iam.user_profiles` | `81dd67d3-1d5f-4d8e-8061-a6764861f924` | Full Name: Student One, Status: ACTIVE |
| IAM Role Assignment | `iam.role_assignments` | `81dd67d3-1d5f-4d8e-8061-a67648610005` | Role: `STUDENT` (`caa7f8a2-c3eb-46b3-bf2e-ec6640289ecb`) |
| Student Domain | `stu.students` | `81dd67d3-1d5f-4d8e-8061-a67648610002` | Enrollment: `STU-DEV-2026-0001` (ACTIVE) |
| Academic Enrollment | `acd.batch_students` | `81dd67d3-1d5f-4d8e-8061-a67648610006` | Roll No: `CSE-001`, Batch: `BATCH-2026-A` |
| Admissions (STM-02) | `stu.admission_intakes` | `a0000000-0000-0000-0000-000000000050` | `DEV-INTAKE-2026` (Dev Fall 2026 CSE Intake) |
| Admissions (STM-02) | `stu.applications` | `81dd67d3-1d5f-4d8e-8061-a67648610003` | `APP-DEV-2026-0001` (Status: HANDED_OVER) |
| Admissions (STM-02) | `stu.admission_decisions` | `81dd67d3-1d5f-4d8e-8061-a67648610007` | `OFFER_APPROVED` on MERIT |
| Admissions (STM-02) | `stu.admission_offers` | `81dd67d3-1d5f-4d8e-8061-a67648610004` | `OFF-DEV-2026-0001` (Status: ACCEPTED) |
| Tagged Attendance | `acd.attendance_sessions` | 5 Sessions (0081–0085) | CS101 (3 sessions) & CS102 (2 sessions), CLOSED |
| Tagged Attendance | `acd.attendance_records` | 5 Records (0011–0015) | 4 PRESENT, 1 ABSENT (CS101: 66.7% shortage alert, CS102: 100%, Overall: 80%) |

---

## 3. Read-Only Verification Evidence

### Verification 1: IAM Profile and Role Assignment Resolution [VERIFIED]
- **SQL Query:**
  ```sql
  SELECT up.id, up.tenant_id, up.person_id, up.college_id, up.full_name, up.email, up.status, r.code as role_code
  FROM iam.user_profiles up
  JOIN iam.role_assignments ra ON ra.user_id = up.id
  JOIN iam.roles r ON r.id = ra.role_id
  WHERE up.id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
  ```
- **Output:**
  ```json
  [
    {
      "id": "81dd67d3-1d5f-4d8e-8061-a6764861f924",
      "tenant_id": "a0000000-0000-0000-0000-000000000001",
      "person_id": "81dd67d3-1d5f-4d8e-8061-a6764861f924",
      "college_id": "a0000000-0000-0000-0000-000000000002",
      "full_name": "Student One",
      "email": "student1.campx@gmail.com",
      "status": "ACTIVE",
      "role_code": "STUDENT"
    }
  ]
  ```

### Verification 2: Student Record & Academic Enrollment [VERIFIED]
- **SQL Query:**
  ```sql
  SELECT s.id as student_id, s.enrollment_no, s.status, p.first_name, p.last_name, p.display_name, prog.name as program_name, b.name as batch_name
  FROM stu.students s
  JOIN core.persons p ON p.id = s.person_id
  JOIN acd.programs prog ON prog.id = s.program_id
  LEFT JOIN acd.batch_students bs ON bs.student_id = s.id
  LEFT JOIN acd.batches b ON b.id = bs.batch_id
  WHERE s.person_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
  ```
- **Output:**
  ```json
  [
    {
      "student_id": "81dd67d3-1d5f-4d8e-8061-a67648610002",
      "enrollment_no": "STU-DEV-2026-0001",
      "status": "ACTIVE",
      "first_name": "Student",
      "last_name": "One",
      "display_name": "Student One",
      "program_name": "B.Tech Computer Science & Engineering",
      "batch_name": "Section A (2026-2030)"
    }
  ]
  ```

### Verification 3: STM-02 Application & Offer State [VERIFIED]
- **SQL Query:**
  ```sql
  SELECT app.id as application_id, app.application_number, app.status as app_status, app.document_verification_status, app.payment_status, o.offer_code, o.status as offer_status, ai.intake_name
  FROM stu.applications app
  JOIN stu.admission_intakes ai ON ai.id = app.intake_id
  LEFT JOIN stu.admission_offers o ON o.application_id = app.id
  WHERE app.applicant_person_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
  ```
- **Output:**
  ```json
  [
    {
      "application_id": "81dd67d3-1d5f-4d8e-8061-a67648610003",
      "application_number": "APP-DEV-2026-0001",
      "app_status": "HANDED_OVER",
      "document_verification_status": "VERIFIED",
      "payment_status": "PAID",
      "offer_code": "OFF-DEV-2026-0001",
      "offer_status": "ACCEPTED",
      "intake_name": "Dev Fall 2026 CSE Intake"
    }
  ]
  ```

### Verification 4: Attendance Summary Calculation [VERIFIED]
- **SQL Query:**
  ```sql
  SELECT 
    sub.code as subject_code,
    sub.name as subject_name,
    count(DISTINCT sess.id)::int as total_conducted,
    count(DISTINCT CASE WHEN ar.status = 'PRESENT' OR ar.status = 'ON_DUTY' THEN sess.id END)::int as attended_count,
    count(DISTINCT CASE WHEN ar.status = 'ABSENT' THEN sess.id END)::int as absent_count
  FROM acd.subjects sub
  JOIN acd.timetable_entries te ON te.subject_id = sub.id AND te.deleted_at IS NULL
  LEFT JOIN acd.attendance_sessions sess ON sess.timetable_entry_id = te.id AND sess.status = 'CLOSED' AND sess.deleted_at IS NULL
  LEFT JOIN acd.attendance_records ar ON ar.session_id = sess.id AND ar.student_id = '81dd67d3-1d5f-4d8e-8061-a67648610002'
  WHERE sub.tenant_id = 'a0000000-0000-0000-0000-000000000001' AND sub.deleted_at IS NULL
  GROUP BY sub.id, sub.code, sub.name
  ORDER BY sub.code ASC;
  ```
- **Output:**
  ```json
  [
    {
      "subject_code": "CS101",
      "subject_name": "Data Structures & Algorithms",
      "total_conducted": 3,
      "attended_count": 2,
      "absent_count": 1
    },
    {
      "subject_code": "CS102",
      "subject_name": "Database Management Systems",
      "total_conducted": 2,
      "attended_count": 2,
      "absent_count": 0
    }
  ]
  ```
- **Result:**
  - `CS101`: 2 / 3 = 66.7% (triggers statutory shortage alert < 75%)
  - `CS102`: 2 / 2 = 100% (Satisfactory)
  - Overall attendance: 4 / 5 = 80.0% (Good standing overall)

