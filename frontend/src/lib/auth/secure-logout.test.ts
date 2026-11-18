import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  clearPortalBrowserStorage,
  isPortalBrowserStorageKey,
  performSecureLogout,
} from './secure-logout.ts';

function memoryStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial));
  return {
    get length() {
      return map.size;
    },
    key(index: number) {
      return Array.from(map.keys())[index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    getItem(key: string) {
      return map.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      map.set(key, value);
    },
    _map: map,
  };
}

describe('secureLogout', () => {
  it('stm10Us020_clearsPortalPreferenceKeysOnly', () => {
    assert.equal(isPortalBrowserStorageKey('campx_pref_t_u_student'), true);
    assert.equal(isPortalBrowserStorageKey('unrelated-analytics'), false);
    const storage = memoryStorage({
      campx_pref_t_u_student: '{"theme":"dark"}',
      'sb-demo-auth-token': 'tok',
      keep_me: '1',
    });
    const removed = clearPortalBrowserStorage(storage);
    assert.equal(removed, 2);
    assert.equal(storage.getItem('keep_me'), '1');
    assert.equal(storage.getItem('campx_pref_t_u_student'), null);
  });

  it('stm10Us020_performSecureLogoutEndsSessionAndNavigatesReplace', async () => {
    const calls: string[] = [];
    await performSecureLogout({
      clearTestSession: async () => {
        calls.push('test-session');
      },
      signOutSupabase: async () => {
        calls.push('supabase');
      },
      clearLocalStorage: () => {
        calls.push('local');
      },
      clearSessionStorage: () => {
        calls.push('session');
      },
      broadcast: () => {
        calls.push('broadcast');
      },
      navigateToLogin: () => {
        calls.push('navigate');
      },
    });
    assert.deepEqual(calls, [
      'test-session',
      'supabase',
      'local',
      'session',
      'broadcast',
      'navigate',
    ]);
  });

  it('stm10Us020_continuesWhenTestSessionClearFails', async () => {
    let navigated = false;
    await performSecureLogout({
      clearTestSession: async () => {
        throw new Error('network');
      },
      signOutSupabase: async () => {},
      navigateToLogin: () => {
        navigated = true;
      },
    });
    assert.equal(navigated, true);
  });
});
