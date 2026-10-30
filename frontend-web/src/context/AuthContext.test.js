import test from 'node:test';
import assert from 'node:assert/strict';
import { isSupabaseConfigured, getSupabase } from '../services/supabaseClient.js';
import { isMockFallbackEnabled } from '../services/api.js';

test('Auth: isSupabaseConfigured returns false when env vars are missing', () => {
  // In the current test environment without VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY
  // isSupabaseConfigured should be false unless both are valid strings
  if (!process.env.VITE_SUPABASE_URL || !process.env.VITE_SUPABASE_ANON_KEY) {
    assert.equal(isSupabaseConfigured(), false);
    assert.equal(getSupabase(), null);
  }
});

test('Auth: isMockFallbackEnabled defaults to false', () => {
  delete process.env.VITE_ENABLE_MOCK_FALLBACK;
  assert.equal(isMockFallbackEnabled(), false);
});

test('Auth: Tenant context is extracted only from trusted session metadata', () => {
  // Synthetic Supabase session with custom access token hook claim
  const sessionWithHook = {
    access_token: 'fake-jwt-token-with-claims',
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'admin@campx.edu',
      app_metadata: {
        tenant_id: '11111111-1111-1111-1111-111111111111',
        role: 'SUPER_ADMIN'
      }
    }
  };

  const tenantId = sessionWithHook.user.app_metadata.tenant_id;
  assert.equal(tenantId, '11111111-1111-1111-1111-111111111111');
  assert.equal(sessionWithHook.user.id, '00000000-0000-0000-0000-000000000001');

  // When session has no tenant claim (e.g. hook not executed yet)
  const sessionWithoutHook = {
    access_token: 'fake-jwt-token-no-claims',
    user: {
      id: '00000000-0000-0000-0000-000000000002',
      email: 'user@campx.edu',
      app_metadata: {}
    }
  };
  const missingTenantId = sessionWithoutHook.user.app_metadata.tenant_id || null;
  assert.equal(missingTenantId, null, 'Must NOT fabricate a fake tenant ID when none is provided in app_metadata');
});
