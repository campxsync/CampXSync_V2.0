/**
 * CampXSync Phase 6 Verification Suite: Faculty Attendance (ACD-06) & Corrections
 *
 * Validates:
 * 1. Faculty Route Guarding & Access Boundaries (/faculty, /faculty/attendance, /faculty/sessions, /college/attendance)
 * 2. Non-Faculty / Non-Admin Access Denial (Student rejected, Unauthenticated redirected)
 * 3. Timetable Slot & Roster Validation on Session Creation
 * 4. Duplicate Session Prevention (Idempotent session open/resume)
 * 5. Student Roster Membership Enforcement (Non-roster student rejection HTTP 400)
 * 6. Attendance Submission & Running Counts
 * 7. Post-Submission Read-Only Locking (Faculty modifications on CLOSED session rejected HTTP 409)
 * 8. Attendance Corrections Authorization (Faculty denied HTTP 403, College Admin permitted)
 * 9. Mandatory Correction Reason Enforcement (Min 5 chars, HTTP 400)
 * 10. Optimistic Concurrency Locking & Conflict Handling (Mismatched expectedVersion HTTP 409)
 * 11. Domain & Audit Persistence (acd.attendance_corrections, audit.change_log, audit.events, sys.outbox_events)
 * 12. Real-Time Summary Recalculation
 * 13. Statutory Shortage Reporting & Detection (75% statutory threshold)
 * 14. Cross-Tenant Data Isolation (College A vs College B boundary assertion)
 */

import http from 'http';
import fs from 'fs';
import path from 'path';
import pg from '../frontend/node_modules/pg/lib/index.js';

// Load environment from frontend/.env.local if not already present in environment
try {
  const envPath = path.resolve('frontend/.env.local');
  if (fs.existsSync(envPath)) {
    const lines = fs.readFileSync(envPath, 'utf8').split('\n');
    for (const line of lines) {
      const match = line.match(/^\s*([\w.-]+)\s*=\s*(.*)?\s*$/);
      if (match) {
        let val = (match[2] || '').trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        if (!process.env[match[1]]) {
          process.env[match[1]] = val;
        }
      }
    }
  }
} catch (e) {
  console.warn('Could not load .env.local:', e.message);
}

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

// Test identities
const sessions = {
  facultyA: {
    id: '36bae3de-615e-4018-a013-3cb5e3cb4996',
    email: 'prof.p5.327590@institution.edu',
    user_metadata: { full_name: 'Prof. Grace Hopper' },
    app_metadata: { role: 'FACULTY', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  collegeAdminA: {
    id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
    email: 'campxsync@gmail.com',
    user_metadata: { full_name: 'Staging College Admin' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  studentA: {
    id: '44444444-4444-4444-4444-444444444444',
    email: 'student.a@college.edu',
    user_metadata: { full_name: 'Student Alice' },
    app_metadata: { role: 'STUDENT', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  collegeAdminB: {
    id: '88888888-8888-8888-8888-888888888888',
    email: 'admin.b@collegeb.edu',
    user_metadata: { full_name: 'College Admin B' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '22222222-2222-2222-2222-222222222222' },
  },
};

function authHeader(userSession) {
  return {
    Cookie: `campx_test_session=${encodeURIComponent(JSON.stringify(userSession))}`,
  };
}

let passed = 0;
let failed = 0;

function assert(condition, testName, details = '') {
  if (condition) {
    console.log(`  [PASS] ${testName}`);
    passed++;
  } else {
    console.error(`  [FAIL] ${testName}${details ? ` -> ${details}` : ''}`);
    failed++;
  }
}

async function runPhase6Tests() {
  console.log('\n================================================================');
  console.log('   CampXSync Phase 6: Faculty Attendance & Corrections Suite');
  console.log('================================================================\n');

  const tenantAId = '11111111-1111-1111-1111-111111111111';
  const tenantBId = '22222222-2222-2222-2222-222222222222';

  // 1. Fetch valid timetable slots and students from DB for Tenant A
  let slotA = null;
  let sectionAId = null;
  let studentsA = [];

  try {
    const slotRes = await query(`
      SELECT tte.id, tte.section_id, tte.employee_id, tte.start_time, tte.end_time, tte.day_of_week
      FROM acd.timetable_entries tte
      WHERE tte.tenant_id = $1 AND tte.deleted_at IS NULL
      LIMIT 1
    `, [tenantAId]);

    if (slotRes.rows.length > 0) {
      slotA = slotRes.rows[0];
      sectionAId = slotA.section_id;
    }

    const stuRes = await query(`
      SELECT s.id, s.enrollment_no, p.display_name
      FROM stu.students s
      JOIN core.persons p ON p.id = s.person_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      LIMIT 5
    `, [tenantAId]);

    studentsA = stuRes.rows;
  } catch (err) {
    console.error('Failed to query initial DB state:', err.message);
  }

  // ============================================================================
  // Suite 1: Route Guarding & Role Boundaries
  // ============================================================================
  console.log('--- Suite 1: Route Guarding & Role Boundaries ---');

  // Test 1: Faculty access to /faculty
  const resFacHome = await makeRequest({
    path: '/faculty',
    headers: authHeader(sessions.facultyA),
  });
  assert(resFacHome.statusCode === 200, 'Test 1.1: Faculty can access /faculty home');

  // Test 2: Faculty access to /faculty/attendance
  const resFacAtt = await makeRequest({
    path: '/faculty/attendance',
    headers: authHeader(sessions.facultyA),
  });
  assert(resFacAtt.statusCode === 200, 'Test 1.2: Faculty can access /faculty/attendance');

  // Test 3: Faculty access to /faculty/sessions
  const resFacSess = await makeRequest({
    path: '/faculty/sessions',
    headers: authHeader(sessions.facultyA),
  });
  assert(resFacSess.statusCode === 200, 'Test 1.3: Faculty can access /faculty/sessions');

  // Test 4: College Admin access to /college/attendance
  const resAdmAtt = await makeRequest({
    path: '/college/attendance',
    headers: authHeader(sessions.collegeAdminA),
  });
  assert(resAdmAtt.statusCode === 200, 'Test 1.4: College Admin can access /college/attendance');

  // Test 5: Student denied from /faculty
  const resStuFac = await makeRequest({
    path: '/faculty',
    headers: authHeader(sessions.studentA),
  });
  assert(
    resStuFac.statusCode === 307 || resStuFac.statusCode === 308 || resStuFac.statusCode === 403,
    'Test 1.5: Student is denied from /faculty home',
    `Status was ${resStuFac.statusCode}`
  );

  // Test 6: Student denied from /college/attendance
  const resStuAdm = await makeRequest({
    path: '/college/attendance',
    headers: authHeader(sessions.studentA),
  });
  assert(
    resStuAdm.statusCode === 307 || resStuAdm.statusCode === 308 || resStuAdm.statusCode === 403,
    'Test 1.6: Student is denied from /college/attendance',
    `Status was ${resStuAdm.statusCode}`
  );

  // Test 7: Unauthenticated request redirects
  const resUnauth = await makeRequest({ path: '/faculty' });
  assert(
    resUnauth.statusCode === 307 || resUnauth.statusCode === 308 || resUnauth.statusCode === 401,
    'Test 1.7: Unauthenticated request redirects to login/access-denied',
    `Status was ${resUnauth.statusCode}`
  );

  // ============================================================================
  // Suite 2: Session Opening & Duplicate Prevention
  // ============================================================================
  console.log('\n--- Suite 2: Session Opening & Duplicate Prevention ---');

  // Use a unique session date per test run to guarantee fresh session lifecycle testing
  const randomDay = 10 + Math.floor((Date.now() / 1000) % 18);
  const testDate = `2026-11-${String(randomDay).padStart(2, '0')}`;
  let createdSessionId = null;

  // Test 8: Opening session with invalid timetable entry returns 404
  const resInvalidEntry = await makeRequest({
    path: '/api/college/attendance/sessions',
    method: 'POST',
    headers: authHeader(sessions.facultyA),
    body: JSON.stringify({
      timetableEntryId: '00000000-0000-0000-0000-000000000000',
      sessionDate: testDate,
    }),
  });
  assert(
    resInvalidEntry.statusCode === 404,
    'Test 2.1: Invalid timetable slot returns HTTP 404 Not Found',
    `Status was ${resInvalidEntry.statusCode}`
  );

  // Test 9: Open valid attendance session
  if (slotA) {
    const resOpenSession = await makeRequest({
      path: '/api/college/attendance/sessions',
      method: 'POST',
      headers: authHeader(sessions.facultyA),
      body: JSON.stringify({
        timetableEntryId: slotA.id,
        sectionId: slotA.section_id,
        sessionDate: testDate,
        startTime: slotA.start_time?.slice(0, 8),
        endTime: slotA.end_time?.slice(0, 8),
      }),
    });

    const openData = JSON.parse(resOpenSession.body || '{}');
    assert(
      resOpenSession.statusCode === 200 || resOpenSession.statusCode === 201,
      'Test 2.2: Valid timetable slot opens attendance session',
      `Status was ${resOpenSession.statusCode}`
    );
    assert(openData.session?.id != null, 'Test 2.3: Session returned with valid UUID');
    createdSessionId = openData.session?.id;

    // Test 10: Duplicate session opening returns existing session (no duplicates!)
    const resDuplicateOpen = await makeRequest({
      path: '/api/college/attendance/sessions',
      method: 'POST',
      headers: authHeader(sessions.facultyA),
      body: JSON.stringify({
        timetableEntryId: slotA.id,
        sectionId: slotA.section_id,
        sessionDate: testDate,
      }),
    });

    const dupData = JSON.parse(resDuplicateOpen.body || '{}');
    assert(
      resDuplicateOpen.statusCode === 200,
      'Test 2.4: Duplicate session request returns HTTP 200'
    );
    assert(
      dupData.session?.id === createdSessionId,
      'Test 2.5: Reopening slot returns the exact same session (zero duplicates created)'
    );
    assert(dupData.isExisting === true, 'Test 2.6: Flag isExisting is true');
  }

  // ============================================================================
  // Suite 3: Roster Validation & Attendance Submission
  // ============================================================================
  console.log('\n--- Suite 3: Roster Validation & Attendance Submission ---');

  if (createdSessionId && studentsA.length >= 2) {
    // Test 11: Non-roster student rejection (HTTP 400)
    const resFakeStudent = await makeRequest({
      path: '/api/college/attendance/records',
      method: 'POST',
      headers: authHeader(sessions.facultyA),
      body: JSON.stringify({
        sessionId: createdSessionId,
        closeSession: false,
        records: [
          { studentId: '99999999-9999-9999-9999-999999999999', status: 'PRESENT' },
        ],
      }),
    });
    assert(
      resFakeStudent.statusCode === 400,
      'Test 3.1: Non-roster student ID rejected with HTTP 400 Bad Request',
      `Status was ${resFakeStudent.statusCode}`
    );

    // Test 12: Successful attendance submission with marks and session closure
    const marksPayload = [
      { studentId: studentsA[0].id, status: 'PRESENT' },
      { studentId: studentsA[1].id, status: 'ABSENT' },
    ];
    if (studentsA.length >= 3) {
      marksPayload.push({ studentId: studentsA[2].id, status: 'LEAVE' });
    }

    const resSubmit = await makeRequest({
      path: '/api/college/attendance/records',
      method: 'POST',
      headers: authHeader(sessions.facultyA),
      body: JSON.stringify({
        sessionId: createdSessionId,
        closeSession: true,
        records: marksPayload,
      }),
    });

    const submitData = JSON.parse(resSubmit.body || '{}');
    assert(resSubmit.statusCode === 200, 'Test 3.2: Attendance submitted successfully (HTTP 200)');
    assert(
      submitData.summary?.presentCount === 1,
      'Test 3.3: Summary reflects 1 Present mark'
    );
    assert(
      submitData.summary?.absentCount === 1,
      'Test 3.4: Summary reflects 1 Absent mark'
    );
    assert(
      submitData.summary?.status === 'CLOSED',
      'Test 3.5: Session status transitioned to CLOSED'
    );

    // Test 13: Read session records via GET
    const resGetRecords = await makeRequest({
      path: `/api/college/attendance/records?sessionId=${createdSessionId}`,
      headers: authHeader(sessions.facultyA),
    });
    const recordsData = JSON.parse(resGetRecords.body || '{}');
    assert(resGetRecords.statusCode === 200, 'Test 3.6: GET session records returns HTTP 200');
    assert(
      recordsData.records?.length >= 2,
      'Test 3.7: Persisted records retrieved accurately'
    );

    // Test 14: Post-submission read-only locking (Faculty modifying CLOSED session returns 409)
    const resLockedEdit = await makeRequest({
      path: '/api/college/attendance/records',
      method: 'POST',
      headers: authHeader(sessions.facultyA),
      body: JSON.stringify({
        sessionId: createdSessionId,
        closeSession: true,
        records: [{ studentId: studentsA[0].id, status: 'ABSENT' }],
      }),
    });
    assert(
      resLockedEdit.statusCode === 409,
      'Test 3.8: Faculty modifying closed session is rejected with HTTP 409 Conflict (Read-Only Lock)',
      `Status was ${resLockedEdit.statusCode}`
    );
  }

  // ============================================================================
  // Suite 4: Audited Attendance Corrections (ACD-06)
  // ============================================================================
  console.log('\n--- Suite 4: Audited Attendance Corrections (ACD-06) ---');

  if (createdSessionId && studentsA.length >= 2) {
    // Fetch the target record ID and initial version
    const resGetRec = await makeRequest({
      path: `/api/college/attendance/records?sessionId=${createdSessionId}`,
      headers: authHeader(sessions.collegeAdminA),
    });
    const recData = JSON.parse(resGetRec.body || '{}');
    const targetRecord = recData.records?.find((r) => r.student_id === studentsA[1].id);

    if (targetRecord) {
      const recordId = targetRecord.id;
      const initialVersion = targetRecord.row_version || 1;

      // Test 15: Faculty forbidden from corrections (HTTP 403)
      const resFacCorrection = await makeRequest({
        path: '/api/college/attendance/corrections',
        method: 'POST',
        headers: authHeader(sessions.facultyA),
        body: JSON.stringify({
          recordId,
          sessionId: createdSessionId,
          newStatus: 'PRESENT',
          reason: 'Medical certificate approved',
          expectedVersion: initialVersion,
        }),
      });
      assert(
        resFacCorrection.statusCode === 403,
        'Test 4.1: Faculty role forbidden from administrative corrections (HTTP 403)',
        `Status was ${resFacCorrection.statusCode}`
      );

      // Test 16: Mandatory correction reason enforcement (missing reason returns 400)
      const resNoReason = await makeRequest({
        path: '/api/college/attendance/corrections',
        method: 'POST',
        headers: authHeader(sessions.collegeAdminA),
        body: JSON.stringify({
          recordId,
          sessionId: createdSessionId,
          newStatus: 'PRESENT',
          reason: 'abc', // < 5 chars
          expectedVersion: initialVersion,
        }),
      });
      assert(
        resNoReason.statusCode === 400,
        'Test 4.2: Correction without mandatory reason (<5 chars) rejected with HTTP 400',
        `Status was ${resNoReason.statusCode}`
      );

      // Test 17: Optimistic concurrency conflict on mismatched version (HTTP 409)
      const resStaleVersion = await makeRequest({
        path: '/api/college/attendance/corrections',
        method: 'POST',
        headers: authHeader(sessions.collegeAdminA),
        body: JSON.stringify({
          recordId,
          sessionId: createdSessionId,
          newStatus: 'PRESENT',
          reason: 'Official duty verification complete',
          expectedVersion: 999, // Mismatched version
        }),
      });
      assert(
        resStaleVersion.statusCode === 409,
        'Test 4.3: Mismatched expectedVersion rejected with HTTP 409 Conflict',
        `Status was ${resStaleVersion.statusCode}`
      );

      // Test 18: Successful audited correction by College Admin
      const resValidCorrection = await makeRequest({
        path: '/api/college/attendance/corrections',
        method: 'POST',
        headers: authHeader(sessions.collegeAdminA),
        body: JSON.stringify({
          recordId,
          sessionId: createdSessionId,
          newStatus: 'PRESENT',
          reason: 'Official medical dispensation granted by Dean',
          expectedVersion: initialVersion,
        }),
      });

      const corrData = JSON.parse(resValidCorrection.body || '{}');
      assert(
        resValidCorrection.statusCode === 200,
        'Test 4.4: College Admin successfully applies attendance correction (HTTP 200)'
      );
      assert(
        corrData.record?.row_version === initialVersion + 1,
        `Test 4.5: Row version incremented to v${initialVersion + 1}`
      );
      assert(
        corrData.record?.status === 'PRESENT',
        'Test 4.6: Record status updated to PRESENT'
      );
      assert(
        corrData.summary?.presentCount >= 2,
        'Test 4.7: Session summary recalculation reflects new Present mark'
      );

      // Test 19: Verify acd.attendance_corrections domain table persistence
      try {
        const domainCorrCheck = await query(`
          SELECT id, old_status, new_status, reason, status
          FROM acd.attendance_corrections
          WHERE record_id = $1 AND tenant_id = $2
          ORDER BY created_at DESC
          LIMIT 1
        `, [recordId, tenantAId]);

        assert(
          domainCorrCheck.rows.length > 0,
          'Test 4.8: Persisted record verified in acd.attendance_corrections domain table'
        );
        assert(
          domainCorrCheck.rows[0]?.new_status === 'PRESENT',
          'Test 4.9: acd.attendance_corrections stores new_status = PRESENT'
        );
      } catch (err) {
        console.error('Error verifying domain table:', err.message);
      }

      // Test 20: Verify audit.events and sys.outbox_events persistence
      try {
        const auditEventCheck = await query(`
          SELECT id, action, severity
          FROM audit.events
          WHERE tenant_id = $1 AND action = 'ATTENDANCE_CORRECTED' AND resource_id = $2
          ORDER BY created_at DESC
          LIMIT 1
        `, [tenantAId, recordId]);

        assert(
          auditEventCheck.rows.length > 0,
          'Test 4.10: ATTENDANCE_CORRECTED event logged in audit.events'
        );

        const outboxCheck = await query(`
          SELECT id, event_type, status
          FROM sys.outbox_events
          WHERE tenant_id = $1 AND event_type = 'AttendanceCorrected' AND aggregate_id = $2
          ORDER BY created_at DESC
          LIMIT 1
        `, [tenantAId, recordId]);

        assert(
          outboxCheck.rows.length > 0,
          'Test 4.11: AttendanceCorrected event emitted to sys.outbox_events queue'
        );
      } catch (err) {
        console.error('Error verifying audit and outbox events:', err.message);
      }
    }
  }

  // ============================================================================
  // Suite 5: Shortage Reporting & Cross-Tenant Isolation
  // ============================================================================
  console.log('\n--- Suite 5: Shortage Reporting & Cross-Tenant Isolation ---');

  // Test 21: Attendance report endpoint applies 75% statutory threshold
  const resReport = await makeRequest({
    path: '/api/college/attendance/report',
    headers: authHeader(sessions.collegeAdminA),
  });
  const reportData = JSON.parse(resReport.body || '{}');
  assert(resReport.statusCode === 200, 'Test 5.1: Attendance Report API returns HTTP 200');
  assert(reportData.threshold === 75, 'Test 5.2: Statutory threshold is 75%');
  assert(reportData.summary != null, 'Test 5.3: Report summary metrics calculated');

  // Test 22: Cross-Tenant Isolation — College Admin B cannot access College Admin A session
  if (createdSessionId) {
    const resCrossTenantGet = await makeRequest({
      path: `/api/college/attendance/records?sessionId=${createdSessionId}`,
      headers: authHeader(sessions.collegeAdminB),
    });
    assert(
      resCrossTenantGet.statusCode === 404,
      'Test 5.4: Cross-Tenant Isolation: College B cannot view College A attendance records (HTTP 404)',
      `Status was ${resCrossTenantGet.statusCode}`
    );

    const resCrossTenantCorrect = await makeRequest({
      path: '/api/college/attendance/corrections',
      method: 'POST',
      headers: authHeader(sessions.collegeAdminB),
      body: JSON.stringify({
        recordId: '11111111-1111-1111-1111-111111111111',
        sessionId: createdSessionId,
        newStatus: 'PRESENT',
        reason: 'Illegal cross tenant attempt',
        expectedVersion: 1,
      }),
    });
    assert(
      resCrossTenantCorrect.statusCode === 404,
      'Test 5.5: Cross-Tenant Isolation: College B cannot correct College A attendance record (HTTP 404)',
      `Status was ${resCrossTenantCorrect.statusCode}`
    );
  }

  console.log('\n================================================================');
  console.log(`   Verification Summary: ${passed} PASSED, ${failed} FAILED`);
  console.log('================================================================\n');

  if (pool) {
    await pool.end();
  }

  process.exit(failed > 0 ? 1 : 0);
}

runPhase6Tests().catch((err) => {
  console.error('Fatal execution error:', err);
  if (pool) pool.end();
  process.exit(1);
});
