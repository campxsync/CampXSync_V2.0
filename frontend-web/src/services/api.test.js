import test from 'node:test';
import assert from 'node:assert/strict';
import {
  api,
  getHeaders,
  isMockFallbackEnabled,
  parseApiError,
  MOCK_FIXTURES
} from './api.js';

test('Test 1: Mock mode is disabled by default', () => {
  delete process.env.VITE_ENABLE_MOCK_FALLBACK;
  assert.equal(isMockFallbackEnabled(), false, 'Mock fallback should be false by default');
});

test('Test 2: API failure does not silently return mock data when mock mode is disabled', async () => {
  delete process.env.VITE_ENABLE_MOCK_FALLBACK;
  assert.equal(isMockFallbackEnabled(), false);

  const originalFetch = global.fetch;
  global.fetch = async () => {
    return {
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
      text: async () => JSON.stringify({
        type: 'https://campxsync.io/errors/service-unavailable',
        title: 'Service Unavailable',
        status: 503,
        detail: 'Database pooler unreachable in staging'
      })
    };
  };

  try {
    // getColleges must reject and NOT return MOCK_FIXTURES.colleges
    await assert.rejects(
      async () => {
        await api.getColleges({});
      },
      (err) => {
        assert.equal(err.status, 503);
        assert.match(err.message, /Database pooler unreachable in staging/);
        assert.equal(err.problemDetails.title, 'Service Unavailable');
        return true;
      },
      'getColleges should propagate RFC 7807 problem details instead of returning mock data'
    );

    // getInstitutes must reject and NOT return mock institutes
    await assert.rejects(
      async () => {
        await api.getInstitutes({});
      },
      (err) => {
        assert.equal(err.status, 503);
        return true;
      }
    );

    // getCourses must reject and NOT return mock courses
    await assert.rejects(
      async () => {
        await api.getCourses({});
      },
      (err) => {
        assert.equal(err.status, 503);
        return true;
      }
    );
  } finally {
    global.fetch = originalFetch;
  }
});

test('Test 3: Explicit developer mock mode behaves as documented when enabled', async () => {
  process.env.VITE_ENABLE_MOCK_FALLBACK = 'true';
  assert.equal(isMockFallbackEnabled(), true);

  const originalFetch = global.fetch;
  global.fetch = async () => {
    throw new Error('Network offline simulation');
  };

  try {
    const colData = await api.getColleges({});
    assert.ok(colData.colleges, 'Colleges array should be returned');
    assert.equal(colData.colleges.length, MOCK_FIXTURES.colleges.length);
    assert.equal(colData.colleges[0].code, 'SOET');

    const instData = await api.getInstitutes({});
    assert.ok(instData.institutes, 'Institutes array should be returned');
    assert.equal(instData.institutes[0].code, 'CAMPXSYNC');
  } finally {
    delete process.env.VITE_ENABLE_MOCK_FALLBACK;
    global.fetch = originalFetch;
  }
});

test('Test 4: Authentication tokens are attached to protected requests', async () => {
  const contextWithToken = {
    accessToken: 'test-real-supabase-jwt-token-12345',
    userId: 'ba77b8f7-ab32-46bd-85d2-5aade3526880',
    tenantId: '11111111-1111-1111-1111-111111111111',
    userRole: 'authenticated'
  };

  const headers = getHeaders(contextWithToken);
  assert.equal(headers['Authorization'], 'Bearer test-real-supabase-jwt-token-12345');
  assert.equal(headers['X-Tenant-Id'], '11111111-1111-1111-1111-111111111111');
  assert.equal(headers['X-User-Id'], 'ba77b8f7-ab32-46bd-85d2-5aade3526880');
  assert.ok(headers['X-Trace-Id'].startsWith('web-'));

  // Test actual call passes Authorization header to fetch
  const originalFetch = global.fetch;
  let capturedHeaders = null;
  global.fetch = async (url, options) => {
    capturedHeaders = options.headers;
    return {
      ok: true,
      status: 200,
      json: async () => ({ colleges: [] })
    };
  };

  try {
    await api.getColleges(contextWithToken);
    assert.ok(capturedHeaders, 'fetch should have been invoked with options');
    assert.equal(capturedHeaders['Authorization'], 'Bearer test-real-supabase-jwt-token-12345');
  } finally {
    global.fetch = originalFetch;
  }
});

test('Test 5: Missing session and authentication errors are handled correctly', async () => {
  // Unauthenticated context has no Authorization header
  const unauthHeaders = getHeaders({});
  assert.equal(unauthHeaders['Authorization'], undefined, 'No auth header without token');

  // HTTP 401 Unauthorized response from gateway / Supabase
  const res401 = {
    ok: false,
    status: 401,
    statusText: 'Unauthorized',
    text: async () => JSON.stringify({
      title: 'Unauthorized',
      status: 401,
      detail: 'JWT expired or signature verification failed'
    })
  };

  const parsed = await parseApiError(res401, 'getColleges');
  assert.equal(parsed.status, 401);
  assert.match(parsed.message, /JWT expired or signature verification failed/);
  assert.equal(parsed.operation, 'getColleges');
});

test('Test 6: Failed mutations are never presented as successful persistence', async () => {
  delete process.env.VITE_ENABLE_MOCK_FALLBACK;

  const originalFetch = global.fetch;
  global.fetch = async () => {
    return {
      ok: false,
      status: 409,
      statusText: 'Conflict',
      text: async () => JSON.stringify({
        title: 'Conflict',
        status: 409,
        detail: 'College with code SOET already exists in core.colleges'
      })
    };
  };

  try {
    await assert.rejects(
      async () => {
        await api.createCollege({ code: 'SOET', name: 'School of Eng' }, { accessToken: 'valid' });
      },
      (err) => {
        assert.equal(err.status, 409);
        assert.match(err.message, /College with code SOET already exists/);
        return true;
      },
      'Failed mutation must throw and never return synthetic success ID'
    );
  } finally {
    global.fetch = originalFetch;
  }
});
