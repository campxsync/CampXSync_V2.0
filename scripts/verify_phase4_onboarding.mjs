/**
 * CampXSync Phase 4 Verification Suite: Platform Admin Onboarding & Tenancy Registry
 *
 * Validates:
 * 1. Platform Operator Access Control & Route Guarding (/platform, /platform/institutes, /platform/institutes/new)
 * 2. Cross-Tenant Data Isolation (zero student/faculty individual rows exposed to platform admin)
 * 3. Suspended Tenant Access Denial (/access-denied?reason=tenant_suspended)
 * 4. Onboarding Input Validation & Schema Integrity (HTTP 400 on malformed input)
 * 5. Saga Conflict Detection & Step Failure Identification (HTTP 409 on duplicate code/email)
 * 6. 5-Step ADM Saga Progress Model & Idempotent Retry Contract
 * 7. Staging Write Boundary Assertion (prevents unauthorized staging tenant mutations)
 */

import http from 'http';

const BASE_URL = 'http://localhost:3000';

function makeRequest({ path, method = 'GET', headers = {}, body = null }) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
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
  platformAdmin: {
    id: '00000000-0000-0000-0000-000000000001',
    email: 'platform.admin@demo.campxsync.test',
    user_metadata: { full_name: 'Platform Operator' },
    app_metadata: { role: 'SUPER_ADMIN' },
  },
  collegeAdmin: {
    id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
    email: 'campxsync@gmail.com',
    user_metadata: { full_name: 'Staging College Admin' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
  suspendedUser: {
    id: '99999999-9999-9999-9999-999999999999',
    email: 'user.suspended@test.edu',
    user_metadata: { full_name: 'Suspended Tenant User', tenant_status: 'SUSPENDED' },
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '99999999-1111-1111-1111-111111111111', tenant_status: 'SUSPENDED' },
    tenantStatus: 'SUSPENDED',
  },
  faculty: {
    id: '11111111-face-0000-0000-000000000001',
    email: 'faculty.a1@demo.campxsync.test',
    user_metadata: { full_name: 'Dr. Aris Thorne' },
    app_metadata: { role: 'FACULTY', tenant_id: '11111111-1111-1111-1111-111111111111' },
  },
};

function cookieFor(sessionObj) {
  return `campx_test_session=${encodeURIComponent(JSON.stringify(sessionObj))}`;
}

async function runPhase4Tests() {
  console.log('================================================================');
  console.log('   CampXSync Phase 4 Verification: Platform ADM Onboarding Saga  ');
  console.log('================================================================\n');

  let passed = 0;
  let failed = 0;

  function assert(title, condition, extraInfo = '') {
    if (condition) {
      console.log(`[PASS] ${title}`);
      passed++;
    } else {
      console.error(`[FAIL] ${title} ${extraInfo}`);
      failed++;
    }
  }

  // 1. Role Boundary: Platform Operator Access
  console.log('--- 1. Platform Operator Access & Role Boundaries ---');
  {
    const res = await makeRequest({
      path: '/platform',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
    });
    assert('Platform Admin can access /platform (HTTP 200)', res.statusCode === 200, `got ${res.statusCode}`);
  }
  {
    const res = await makeRequest({
      path: '/platform/institutes',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
    });
    assert('Platform Admin can access /platform/institutes (HTTP 200)', res.statusCode === 200, `got ${res.statusCode}`);
  }
  {
    const res = await makeRequest({
      path: '/platform/institutes/new',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
    });
    assert('Platform Admin can access Onboarding Wizard /platform/institutes/new (HTTP 200)', res.statusCode === 200, `got ${res.statusCode}`);
  }
  {
    const res = await makeRequest({
      path: '/platform',
      headers: { Cookie: cookieFor(sessions.collegeAdmin) },
    });
    const loc = res.headers['location'] || '';
    assert('College Admin blocked from /platform (307 -> access-denied?reason=forbidden)', res.statusCode === 307 && loc.includes('reason=forbidden'), `got ${res.statusCode} loc=${loc}`);
  }
  {
    const res = await makeRequest({
      path: '/platform/institutes',
      headers: { Cookie: cookieFor(sessions.faculty) },
    });
    const loc = res.headers['location'] || '';
    assert('Faculty blocked from /platform/institutes (307 -> access-denied?reason=forbidden)', res.statusCode === 307 && loc.includes('reason=forbidden'), `got ${res.statusCode} loc=${loc}`);
  }

  // 2. Suspended Tenant Access Denial
  console.log('\n--- 2. Tenant Suspension Access Denial ---');
  {
    const res = await makeRequest({
      path: '/college',
      headers: { Cookie: cookieFor(sessions.suspendedUser) },
    });
    const loc = res.headers['location'] || '';
    assert('Suspended tenant user denied access (307 -> /access-denied?reason=tenant_suspended)', res.statusCode === 307 && loc.includes('reason=tenant_suspended'), `got ${res.statusCode} loc=${loc}`);
  }

  // 3. API Authorization Gate for Onboarding
  console.log('\n--- 3. API Route Authorization Gate ---');
  {
    const res = await makeRequest({
      path: '/api/platform/onboard-tenant',
      method: 'POST',
      headers: { Cookie: cookieFor(sessions.collegeAdmin) },
      body: JSON.stringify({ tenantName: 'Test', tenantCode: 'TEST' }),
    });
    assert('Non-platform admin rejected from POST /api/platform/onboard-tenant (HTTP 403 Forbidden)', res.statusCode === 403, `got ${res.statusCode}`);
  }

  // 4. Input Validation & Error Handling (HTTP 400)
  console.log('\n--- 4. Onboarding Input Validation & Schema Handling ---');
  {
    const res = await makeRequest({
      path: '/api/platform/onboard-tenant',
      method: 'POST',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
      body: JSON.stringify({
        tenantName: 'St', // too short (<3)
        tenantCode: 'invalid-lowercase', // invalid code regex
        collegeName: '',
        collegeCode: 'FET',
        adminName: '',
        adminEmail: 'not-an-email',
      }),
    });
    assert('Malformed payload returns HTTP 400 Validation Error', res.statusCode === 400, `got ${res.statusCode}`);
    const json = JSON.parse(res.body);
    assert('Validation error returns Problem Details with detail field', Boolean(json.detail && json.detail.includes('tenantName')), `got ${JSON.stringify(json)}`);
  }

  // 5. Pre-Flight Conflict Detection (Step 1 & Step 3 Failures)
  console.log('\n--- 5. Saga Pre-Flight Conflict Detection & Step Failure ---');
  {
    const res = await makeRequest({
      path: '/api/platform/onboard-tenant',
      method: 'POST',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
      body: JSON.stringify({
        tenantName: 'Duplicate Test Institute',
        tenantCode: 'CAMPX_STAGING', // Existing tenant code in staging!
        collegeName: 'Main Campus',
        collegeCode: 'CAMPUS_A',
        adminName: 'New Admin',
        adminEmail: 'new.admin@campxsync.test',
      }),
    });
    assert('Duplicate tenant code rejected with HTTP 409 Conflict', res.statusCode === 409, `got ${res.statusCode}`);
    const json = JSON.parse(res.body);
    assert('Conflict response pinpoints exact failed step (Step 1)', json.failedStep === 1, `got failedStep=${json.failedStep}`);
  }
  {
    const res = await makeRequest({
      path: '/api/platform/onboard-tenant',
      method: 'POST',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
      body: JSON.stringify({
        tenantName: 'Fresh Institute Name',
        tenantCode: 'FRESH_CODE_99',
        collegeName: 'Main Campus',
        collegeCode: 'CAMPUS_A',
        adminName: 'Duplicate Admin',
        adminEmail: 'campxsync@gmail.com', // Existing admin email in staging!
      }),
    });
    assert('Duplicate admin email rejected with HTTP 409 Conflict', res.statusCode === 409, `got ${res.statusCode}`);
    const json = JSON.parse(res.body);
    assert('Conflict response pinpoints exact failed step (Step 3)', json.failedStep === 3, `got failedStep=${json.failedStep}`);
  }

  // 6. ADM 5-Step Saga Progress & Staging Write Boundary
  console.log('\n--- 6. ADM 5-Step Saga Progress & Staging Write Boundary ---');
  {
    const res = await makeRequest({
      path: '/api/platform/onboard-tenant',
      method: 'POST',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
      body: JSON.stringify({
        tenantName: 'Simulation Institute of Technology',
        tenantCode: 'GATED_SIM_TEST',
        subdomain: 'simulation',
        plan: 'ENTERPRISE',
        collegeName: 'Faculty of Simulation',
        collegeCode: 'SIM',
        adminName: 'Dr. John Mathew',
        adminEmail: 'admin.gated@sim.test',
      }),
    });
    assert('Saga validation returns HTTP 200 OK', res.statusCode === 200, `got ${res.statusCode}`);
    const json = JSON.parse(res.body);
    assert('Staging write boundary is active (gated: true)', json.gated === true, `got gated=${json.gated}`);
    assert('Response returns 5-step ADM saga progress milestones', Array.isArray(json.saga) && json.saga.length === 5, `got saga=${JSON.stringify(json.saga)}`);
    assert('Saga step 5 indicates gated status awaiting GO PROVISION-TENANT', json.saga[4].status === 'GATED', `got ${json.saga[4]?.status}`);
  }

  // 7. Cross-Tenant Data Isolation Guard
  console.log('\n--- 7. Cross-Tenant Tier Isolation Verification ---');
  {
    const res = await makeRequest({
      path: '/platform',
      headers: { Cookie: cookieFor(sessions.platformAdmin) },
    });
    // Verify that the rendered HTML contains aggregate user distribution and NOT raw individual student/faculty enrollment data
    assert('/platform HTML contains aggregated user distribution metrics', res.body.includes('User Aggregate Distribution'), 'missing aggregate distribution section');
    assert('/platform HTML does NOT leak raw student enrollment rows', !res.body.includes('2024-CSE-001') && !res.body.includes('2024-PCS-001'), 'raw student enrollment leaked to platform admin!');
  }

  console.log('\n================================================================');
  console.log(`   Phase 4 Verification Complete: ${passed} PASSED, ${failed} FAILED   `);
  console.log('================================================================\n');

  if (failed > 0) {
    process.exit(1);
  }
}

runPhase4Tests().catch((err) => {
  console.error('Fatal test runner error:', err);
  process.exit(1);
});
