/**
 * CampXSync Phase 5 Verification Suite: College Admin Setup Views, People, and Assignments
 *
 * Validates:
 * 1. College Admin Access Control & Setup View Guarding (/college, /college/departments, /college/batches, /college/timetable, /college/faculty, /college/students)
 * 2. Non-Admin Access Denial (HTTP 403 / redirect to /access-denied for Student & Faculty)
 * 3. Faculty Provisioning & IAM Role Linking (core.persons + hrm.employees + iam.user_profiles + iam.role_assignments)
 * 4. Faculty Duplicate Detection (HTTP 409 on duplicate employee ID or email)
 * 5. Student Enrollment & Batch Membership (core.persons + stu.students + acd.batch_students)
 * 6. Student Duplicate Detection (HTTP 409 on duplicate enrollment number or email)
 * 7. Batch Assignment & Reassignment API (/api/college/students/assign-batch)
 * 8. Soft Deletion & Audit Trail Integration (FACULTY_DELETED, STUDENT_DELETED, audit.events logging)
 * 9. Cross-Tenant Data Isolation (College A vs College B visibility assertion)
 */

import http from 'http';
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';

let pool = null;

function getDbPool() {
  if (!pool) {
    const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
    const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER;
    const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD;

    if (!jdbcUrl) {
      throw new Error('Database JDBC URL is not configured.');
    }

    if (jdbcUrl.includes(FORBIDDEN_PROD_REF) || (username && username.includes(FORBIDDEN_PROD_REF))) {
      throw new Error('FATAL SECURITY VIOLATION: Database target matches forbidden production reference!');
    }

    let connString = jdbcUrl.replace(/^jdbc:/, '');
    const urlObj = new URL(connString);
    if (username) urlObj.username = username;
    if (password) urlObj.password = password;
    urlObj.searchParams.delete('sslmode');

    pool = new Pool({
      connectionString: urlObj.toString(),
      ssl: { rejectUnauthorized: false },
      max: 5,
      idleTimeoutMillis: 10000,
    });
  }
  return pool;
}

async function query(text, params) {
  return getDbPool().query(text, params);
}

const BASE_URL = 'http://localhost:3000';

function makeRequest({ path: reqPath, method = 'GET', headers = {}, body = null }) {
  return new Promise((resolve, reject) => {
    const url = new URL(reqPath, BASE_URL);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method,
      headers: { ...headers },
    };

    if (body) {
      options.headers['Content-Type'] = 'application/json';
      options.headers['Content-Length'] = Buffer.byteLength(body);
    }

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: data,
        });
      });
    });

    req.on('error', (err) => reject(err));
    if (body) req.write(body);
    req.end();
  });
}

const sessions = {
  collegeAdminA: {
    id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
    email: 'campxsync@gmail.com',
    user_metadata: { full_name: 'Staging College Admin' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  collegeAdminB: {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    email: 'admin@college-b.test',
    user_metadata: { full_name: 'College B Admin' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '22222222-2222-2222-2222-222222222222' },
  },
  facultyUser: {
    id: '11111111-fac0-0000-0000-000000000001',
    email: 'faculty.cse@institution.edu',
    user_metadata: { full_name: 'Dr. Alan Turing' },
    app_metadata: { role: 'FACULTY', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  studentUser: {
    id: '11111111-5700-0000-0000-000000000001',
    email: 'stu.2026.cse001@institution.edu',
    user_metadata: { full_name: 'Student 1' },
    app_metadata: { role: 'STUDENT', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
};

function authCookie(session) {
  return `campx_test_session=${encodeURIComponent(JSON.stringify(session))}`;
}

async function runPhase5Verification() {
  console.log('='.repeat(80));
  console.log('CAMPXSYNC MVP V4 — PHASE 5 VERIFICATION SUITE');
  console.log('Target: College Admin Setup Views, People, and Assignments');
  console.log('Staging Database: kpwdvvcschankgggitnj');
  console.log('='.repeat(80));

  let passed = 0;
  let failed = 0;

  function assert(condition, description) {
    if (condition) {
      console.log(`  [PASS] ${description}`);
      passed++;
    } else {
      console.error(`  [FAIL] ${description}`);
      failed++;
    }
  }

  try {
    // -------------------------------------------------------------------------
    // TEST GROUP 1: Route Access Control & Role Guarding
    // -------------------------------------------------------------------------
    console.log('\n--- 1. Route Access Control & Guarding ---');

    const adminRoutes = [
      '/college',
      '/college/departments',
      '/college/batches',
      '/college/timetable',
      '/college/faculty',
      '/college/students',
    ];

    for (const route of adminRoutes) {
      const resAdmin = await makeRequest({
        path: route,
        headers: { Cookie: authCookie(sessions.collegeAdminA) },
      });
      assert(
        resAdmin.statusCode === 200,
        `College Admin access to ${route} returns HTTP 200 OK (Got ${resAdmin.statusCode})`
      );
    }

    // Faculty & Student denial
    const resFaculty = await makeRequest({
      path: '/college',
      headers: { Cookie: authCookie(sessions.facultyUser) },
    });
    assert(
      resFaculty.statusCode === 307 || resFaculty.statusCode === 302 || resFaculty.statusCode === 403,
      `Faculty access to /college denied with redirect or 403 (Got ${resFaculty.statusCode})`
    );

    const resStudent = await makeRequest({
      path: '/college/students',
      headers: { Cookie: authCookie(sessions.studentUser) },
    });
    assert(
      resStudent.statusCode === 307 || resStudent.statusCode === 302 || resStudent.statusCode === 403,
      `Student access to /college/students denied with redirect or 403 (Got ${resStudent.statusCode})`
    );

    // -------------------------------------------------------------------------
    // TEST GROUP 2: Setup View API Listing
    // -------------------------------------------------------------------------
    console.log('\n--- 2. Roster API Endpoint Verification ---');

    const facultyListRes = await makeRequest({
      path: '/api/college/faculty',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
    });
    const facultyListData = JSON.parse(facultyListRes.body);
    assert(
      facultyListRes.statusCode === 200 && facultyListData.success === true && Array.isArray(facultyListData.faculty),
      `GET /api/college/faculty returns HTTP 200 with faculty array (Found ${facultyListData.faculty?.length || 0} members)`
    );

    const studentListRes = await makeRequest({
      path: '/api/college/students',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
    });
    const studentListData = JSON.parse(studentListRes.body);
    assert(
      studentListRes.statusCode === 200 && studentListData.success === true && Array.isArray(studentListData.students),
      `GET /api/college/students returns HTTP 200 with students array (Found ${studentListData.students?.length || 0} students)`
    );

    // -------------------------------------------------------------------------
    // TEST GROUP 3: Faculty Enrollment, IAM Provisioning & Conflict Handling
    // -------------------------------------------------------------------------
    console.log('\n--- 3. Faculty Lifecycle, IAM Provisioning & Conflicts ---');

    // Get a valid department from College A
    const deptQuery = await query(`
      SELECT id FROM core.departments WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL LIMIT 1
    `);
    const departmentId = deptQuery.rows[0]?.id;

    const testFacTimestamp = Date.now().toString().slice(-6);
    const testFacEmpNo = `FAC-P5-${testFacTimestamp}`;
    const testFacEmail = `prof.p5.${testFacTimestamp}@institution.edu`;

    // 3a: Successful Faculty Creation
    const createFacRes = await makeRequest({
      path: '/api/college/faculty',
      method: 'POST',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({
        firstName: 'Grace',
        lastName: 'Hopper',
        email: testFacEmail,
        employeeNo: testFacEmpNo,
        departmentId,
        employmentType: 'PERMANENT',
      }),
    });
    const createFacData = JSON.parse(createFacRes.body);
    if (createFacRes.statusCode !== 201) {
      console.error('Faculty creation failed:', createFacData);
    }
    assert(
      createFacRes.statusCode === 201 && createFacData.success === true,
      `POST /api/college/faculty creates faculty member (HTTP 201 Created)`
    );

    const createdFacultyId = createFacData.faculty?.id;

    // Verify DB state for faculty
    const facDbCheck = await query(`
      SELECT e.id, e.employee_no, e.status, p.display_name, p.primary_email, u.email as profile_email, r.role_id
      FROM hrm.employees e
      JOIN core.persons p ON p.id = e.person_id
      LEFT JOIN iam.user_profiles u ON u.person_id = p.id AND u.deleted_at IS NULL
      LEFT JOIN iam.role_assignments r ON r.user_id = u.id AND r.deleted_at IS NULL
      WHERE e.id = $1 AND e.tenant_id = '11111111-1111-1111-1111-111111111111'
    `, [createdFacultyId]);

    assert(
      facDbCheck.rows.length === 1 && facDbCheck.rows[0]?.employee_no === testFacEmpNo,
      `Faculty persisted in hrm.employees and core.persons correctly`
    );
    assert(
      facDbCheck.rows[0]?.profile_email === testFacEmail,
      `Faculty IAM user profile linked with email '${testFacEmail}'`
    );

    // Verify audit event for faculty creation
    const facAuditCheck = await query(`
      SELECT id, action, severity, status FROM audit.events
      WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND action = 'FACULTY_CREATED' AND resource_id = $1
    `, [createdFacultyId]);
    assert(
      facAuditCheck.rows.length >= 1 && facAuditCheck.rows[0].status === 'SUCCESS',
      `Audit event FACULTY_CREATED recorded in audit.events`
    );

    // 3b: Duplicate Employee Number Conflict
    const dupEmpRes = await makeRequest({
      path: '/api/college/faculty',
      method: 'POST',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({
        firstName: 'Another',
        lastName: 'Teacher',
        email: `another.${testFacTimestamp}@institution.edu`,
        employeeNo: testFacEmpNo, // DUPLICATE
        departmentId,
        employmentType: 'PERMANENT',
      }),
    });
    assert(
      dupEmpRes.statusCode === 409,
      `Duplicate faculty employee number rejected with HTTP 409 Conflict (Got ${dupEmpRes.statusCode})`
    );

    // -------------------------------------------------------------------------
    // TEST GROUP 4: Student Enrollment, Batch Linking & Conflicts
    // -------------------------------------------------------------------------
    console.log('\n--- 4. Student Lifecycle, Batch Membership & Conflicts ---');

    // Get a valid program and batch from College A
    const progQuery = await query(`
      SELECT id FROM acd.programs WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL LIMIT 1
    `);
    const programId = progQuery.rows[0]?.id;

    const batchQuery = await query(`
      SELECT id FROM acd.batches WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL LIMIT 2
    `);
    const batchId1 = batchQuery.rows[0]?.id;
    const batchId2 = batchQuery.rows[1]?.id;

    const testStuTimestamp = Date.now().toString().slice(-6);
    const testStuEnrollNo = `2026-P5-${testStuTimestamp}`;
    const testStuEmail = `student.p5.${testStuTimestamp}@student.institution.edu`;

    // 4a: Successful Student Enrollment with Initial Batch
    const createStuRes = await makeRequest({
      path: '/api/college/students',
      method: 'POST',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({
        firstName: 'Katherine',
        lastName: 'Johnson',
        email: testStuEmail,
        enrollmentNo: testStuEnrollNo,
        programId,
        batchId: batchId1,
      }),
    });
    const createStuData = JSON.parse(createStuRes.body);
    assert(
      createStuRes.statusCode === 201 && createStuData.success === true,
      `POST /api/college/students enrolls student and allocates batch (HTTP 201 Created)`
    );

    const createdStudentId = createStuData.student?.id;

    // Verify DB state for student
    const stuDbCheck = await query(`
      SELECT s.id, s.enrollment_no, s.status, p.display_name, bs.batch_id, bs.status as batch_status
      FROM stu.students s
      JOIN core.persons p ON p.id = s.person_id
      LEFT JOIN acd.batch_students bs ON bs.student_id = s.id AND bs.deleted_at IS NULL
      WHERE s.id = $1 AND s.tenant_id = '11111111-1111-1111-1111-111111111111'
    `, [createdStudentId]);

    assert(
      stuDbCheck.rows.length === 1 && stuDbCheck.rows[0].enrollment_no === testStuEnrollNo,
      `Student persisted in stu.students and core.persons correctly`
    );
    assert(
      stuDbCheck.rows[0].batch_id === batchId1 && stuDbCheck.rows[0].batch_status === 'ACTIVE',
      `Student allocated to initial batch in acd.batch_students with ACTIVE status`
    );

    // Verify audit event for student enrollment
    const stuAuditCheck = await query(`
      SELECT id, action, severity, status FROM audit.events
      WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND action = 'STUDENT_ENROLLED' AND resource_id = $1
    `, [createdStudentId]);
    assert(
      stuAuditCheck.rows.length >= 1 && stuAuditCheck.rows[0].status === 'SUCCESS',
      `Audit event STUDENT_ENROLLED recorded in audit.events`
    );

    // 4b: Duplicate Student Enrollment Number Conflict
    const dupStuRes = await makeRequest({
      path: '/api/college/students',
      method: 'POST',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({
        firstName: 'Another',
        lastName: 'Scholar',
        email: `another.stu.${testStuTimestamp}@student.institution.edu`,
        enrollmentNo: testStuEnrollNo, // DUPLICATE
        programId,
      }),
    });
    assert(
      dupStuRes.statusCode === 409,
      `Duplicate student enrollment number rejected with HTTP 409 Conflict (Got ${dupStuRes.statusCode})`
    );

    // -------------------------------------------------------------------------
    // TEST GROUP 5: Batch Reassignment API & Audit
    // -------------------------------------------------------------------------
    console.log('\n--- 5. Batch Reassignment API & Audit Event ---');

    if (batchId2) {
      const assignBatchRes = await makeRequest({
        path: '/api/college/students/assign-batch',
        method: 'POST',
        headers: { Cookie: authCookie(sessions.collegeAdminA) },
        body: JSON.stringify({
          studentId: createdStudentId,
          batchId: batchId2,
          rollNo: `${testStuEnrollNo}-B2`,
        }),
      });
      const assignBatchData = JSON.parse(assignBatchRes.body);
      assert(
        assignBatchRes.statusCode === 200 && assignBatchData.success === true,
        `POST /api/college/students/assign-batch reassigns student to second batch`
      );

      // Verify DB update
      const batchReassignCheck = await query(`
        SELECT batch_id, roll_no, status FROM acd.batch_students
        WHERE student_id = $1 AND batch_id = $2 AND tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL
      `, [createdStudentId, batchId2]);
      assert(
        batchReassignCheck.rows.length === 1 && batchReassignCheck.rows[0].roll_no === `${testStuEnrollNo}-B2`,
        `acd.batch_students reflects updated batch assignment and roll number`
      );

      // Verify audit event
      const reassignAuditCheck = await query(`
        SELECT id, action, severity, status FROM audit.events
        WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND action = 'STUDENT_BATCH_ASSIGNED' AND resource_id = $1
      `, [createdStudentId]);
      assert(
        reassignAuditCheck.rows.length >= 1 && reassignAuditCheck.rows[0].status === 'SUCCESS',
        `Audit event STUDENT_BATCH_ASSIGNED recorded in audit.events`
      );
    }

    // -------------------------------------------------------------------------
    // TEST GROUP 6: Audited Soft Deletion
    // -------------------------------------------------------------------------
    console.log('\n--- 6. Audited Soft Deletion for Faculty & Students ---');

    // 6a: Soft-Delete Faculty
    const delFacRes = await makeRequest({
      path: '/api/college/faculty',
      method: 'DELETE',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({ id: createdFacultyId }),
    });
    const delFacData = JSON.parse(delFacRes.body);
    if (delFacRes.statusCode !== 200) console.error('Faculty delete failed:', delFacData);
    assert(
      delFacRes.statusCode === 200 && delFacData.success === true,
      `DELETE /api/college/faculty deactivates faculty member (HTTP 200 OK)`
    );

    const facDelDbCheck = await query(`
      SELECT status, deleted_at FROM hrm.employees WHERE id = $1
    `, [createdFacultyId]);
    assert(
      facDelDbCheck.rows[0]?.status === 'EXITED' && facDelDbCheck.rows[0]?.deleted_at !== null,
      `hrm.employees record marked EXITED with non-null deleted_at timestamp`
    );

    const facDelAuditCheck = await query(`
      SELECT action, severity, status FROM audit.events
      WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND action = 'FACULTY_DELETED' AND resource_id = $1
    `, [createdFacultyId]);
    assert(
      facDelAuditCheck.rows.length >= 1 && facDelAuditCheck.rows[0].severity === 'WARNING',
      `Audit event FACULTY_DELETED recorded with WARNING severity`
    );

    // 6b: Soft-Delete Student
    const delStuRes = await makeRequest({
      path: '/api/college/students',
      method: 'DELETE',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
      body: JSON.stringify({ id: createdStudentId }),
    });
    const delStuData = JSON.parse(delStuRes.body);
    if (delStuRes.statusCode !== 200) console.error('Student delete failed:', delStuData);
    assert(
      delStuRes.statusCode === 200 && delStuData.success === true,
      `DELETE /api/college/students deactivates student (HTTP 200 OK)`
    );

    const stuDelDbCheck = await query(`
      SELECT status, deleted_at FROM stu.students WHERE id = $1
    `, [createdStudentId]);
    assert(
      stuDelDbCheck.rows[0]?.status === 'WITHDRAWN' && stuDelDbCheck.rows[0]?.deleted_at !== null,
      `stu.students record marked WITHDRAWN with non-null deleted_at timestamp`
    );

    const stuDelAuditCheck = await query(`
      SELECT action, severity, status FROM audit.events
      WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND action = 'STUDENT_DELETED' AND resource_id = $1
    `, [createdStudentId]);
    assert(
      stuDelAuditCheck.rows.length >= 1 && stuDelAuditCheck.rows[0].severity === 'WARNING',
      `Audit event STUDENT_DELETED recorded with WARNING severity`
    );

    // -------------------------------------------------------------------------
    // TEST GROUP 7: Cross-Tenant Data Isolation
    // -------------------------------------------------------------------------
    console.log('\n--- 7. Cross-Tenant Data Isolation (College A vs College B) ---');

    const collegeBStudentsRes = await makeRequest({
      path: '/api/college/students',
      headers: { Cookie: authCookie(sessions.collegeAdminB) },
    });
    const collegeBStudentsData = JSON.parse(collegeBStudentsRes.body);

    const collegeAStudentsRes = await makeRequest({
      path: '/api/college/students',
      headers: { Cookie: authCookie(sessions.collegeAdminA) },
    });
    const collegeAStudentsData = JSON.parse(collegeAStudentsRes.body);

    // Check for zero crossover in student lists
    const collegeAIds = new Set(collegeAStudentsData.students.map((s) => s.id));
    const collegeBIds = new Set(collegeBStudentsData.students.map((s) => s.id));
    const crossover = [...collegeAIds].filter((id) => collegeBIds.has(id));

    assert(
      crossover.length === 0,
      `Strict tenant isolation: 0 shared student records between College A and College B`
    );

    const collegeBEnrollNos = collegeBStudentsData.students.map((s) => s.enrollment_no);
    const hasCollegeAPrefixInB = collegeBEnrollNos.some((no) => no.includes('CSE') || no.includes('ECE'));
    assert(
      !hasCollegeAPrefixInB,
      `College B student roster contains only College B enrollment sequences (zero College A items)`
    );

    console.log('\n' + '='.repeat(80));
    console.log(`PHASE 5 VERIFICATION RESULT: ${passed} PASSED, ${failed} FAILED`);
    console.log('='.repeat(80));

    if (failed > 0) {
      process.exit(1);
    } else {
      process.exit(0);
    }
  } catch (err) {
    console.error('Fatal execution error during verification:', err);
    process.exit(1);
  } finally {
    if (pool) await pool.end();
  }
}

runPhase5Verification();
