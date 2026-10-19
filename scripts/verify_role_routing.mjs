// Test script: verify_role_routing.mjs
// Validates V4 Phase 3 Gate B — Deferred role-routing check across all 4 seeded roles
// Tests access boundaries for /platform, /college, /faculty, /student, and root /

const BASE_URL = 'http://localhost:3000';

async function getRoleCookie(role) {
  const res = await fetch(`${BASE_URL}/api/auth/test-session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  });
  if (!res.ok) {
    throw new Error(`Failed to get session for role: ${role}`);
  }
  const setCookie = res.headers.get('set-cookie');
  if (!setCookie) {
    throw new Error(`No set-cookie returned for role: ${role}`);
  }
  return setCookie.split(';')[0];
}

async function testRoute(cookie, path) {
  const headers = {};
  if (cookie) {
    headers['Cookie'] = cookie;
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'GET',
    headers,
    redirect: 'manual', // do not follow redirects automatically so we inspect 307/302
  });
  return {
    status: res.status,
    location: res.headers.get('location'),
  };
}

async function main() {
  console.log('=== CAMPXSYNC V4 PHASE 3 — GATE B ROLE ROUTING VERIFICATION ===\n');

  const roles = ['platform_admin', 'college_admin', 'faculty', 'student'];
  const routes = ['/', '/platform', '/college', '/faculty', '/student'];

  // Expected outcomes:
  // For unauthenticated:
  // / -> 307 to /login?returnTo=/
  // /platform -> 307 to /login?returnTo=/platform
  // /college -> 307 to /login?returnTo=/college
  // /faculty -> 307 to /login?returnTo=/faculty
  // /student -> 307 to /login?returnTo=/student
  console.log('--- 1. Testing Unauthenticated Access Boundaries ---');
  let unauthPass = true;
  for (const route of routes) {
    const res = await testRoute(null, route);
    const expectedLocation = `/login?returnTo=${encodeURIComponent(route)}`;
    const pass = (res.status === 307 || res.status === 302) && (res.location?.includes('/login'));
    console.log(`[Unauthenticated] GET ${route.padEnd(10)} => Status: ${res.status}, Location: ${res.location || 'none'} | ${pass ? 'PASS' : 'FAIL'}`);
    if (!pass) unauthPass = false;
  }
  console.log(`Unauthenticated result: ${unauthPass ? 'ALL PASSED' : 'FAILED'}\n`);

  // For each authenticated role:
  const expectedMatrix = {
    platform_admin: {
      '/': { type: 'redirect', to: '/platform' },
      '/platform': { type: 'allow', status: 200 },
      '/college': { type: 'forbidden' },
      '/faculty': { type: 'forbidden' },
      '/student': { type: 'forbidden' },
    },
    college_admin: {
      '/': { type: 'redirect', to: '/college' },
      '/platform': { type: 'forbidden' },
      '/college': { type: 'allow', status: 200 },
      '/faculty': { type: 'allow', status: 200 },
      '/student': { type: 'forbidden' },
    },
    faculty: {
      '/': { type: 'redirect', to: '/faculty' },
      '/platform': { type: 'forbidden' },
      '/college': { type: 'forbidden' },
      '/faculty': { type: 'allow', status: 200 },
      '/student': { type: 'forbidden' },
    },
    student: {
      '/': { type: 'redirect', to: '/student' },
      '/platform': { type: 'forbidden' },
      '/college': { type: 'forbidden' },
      '/faculty': { type: 'forbidden' },
      '/student': { type: 'allow', status: 200 },
    },
  };

  let totalTests = 0;
  let passedTests = 0;

  for (const role of roles) {
    console.log(`--- Testing Role: ${role.toUpperCase()} ---`);
    const cookie = await getRoleCookie(role);
    const expectations = expectedMatrix[role];

    for (const route of routes) {
      totalTests++;
      const res = await testRoute(cookie, route);
      const expected = expectations[route];
      let pass = false;

      if (expected.type === 'allow') {
        pass = res.status === 200;
      } else if (expected.type === 'redirect') {
        pass = (res.status === 307 || res.status === 302) && res.location?.includes(expected.to);
      } else if (expected.type === 'forbidden') {
        pass = (res.status === 307 || res.status === 302) && res.location?.includes('/access-denied');
      }

      console.log(`[${role.padEnd(14)}] GET ${route.padEnd(10)} => Status: ${res.status}, Location: ${res.location || 'none'} | Expected: ${expected.type} | ${pass ? 'PASS' : 'FAIL'}`);
      if (pass) {
        passedTests++;
      }
    }
    console.log('');
  }

  // Negative test: User with valid session but no profile
  console.log('--- Testing Negative Case: Valid Session Without Profile ---');
  const noProfileCookie = 'campx_test_session=' + encodeURIComponent(JSON.stringify({
    id: '00000000-0000-0000-0000-000000000000',
    email: 'noprofile.user@demo.campxsync.test',
  }));
  const noProfileRes = await testRoute(noProfileCookie, '/college');
  const noProfilePass = (noProfileRes.status === 307 || noProfileRes.status === 302) &&
    noProfileRes.location?.includes('/access-denied');
  console.log(`[No-Profile User] GET /college    => Status: ${noProfileRes.status}, Location: ${noProfileRes.location || 'none'} | Expected: forbidden | ${noProfilePass ? 'PASS' : 'FAIL'}\n`);

  console.log(`=== SUMMARY: ${passedTests}/${totalTests} ROLE ROUTING TESTS PASSED ===`);
  if (passedTests === totalTests && unauthPass && noProfilePass) {
    console.log('GATE B VERDICT: PASS — All role routes and access boundaries conclusively verified.');
    process.exit(0);
  } else {
    console.error('GATE B VERDICT: FAIL — One or more access boundary checks failed.');
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Fatal error during role routing verification:', err);
  process.exit(1);
});
