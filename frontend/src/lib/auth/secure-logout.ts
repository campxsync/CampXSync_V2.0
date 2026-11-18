/**
 * STM-10-US-020 — Secure logout helpers (existing Supabase sign-out path).
 * Clears portal browser data, broadcasts logout to other tabs, uses replace navigation.
 */

export const CAMPX_AUTH_BROADCAST = 'campx-auth';
export const CAMPX_AUTH_LOGOUT_MESSAGE = 'logout';

/** Keys that hold portal/session data in the browser. */
export function isPortalBrowserStorageKey(key: string): boolean {
  if (!key) return false;
  return (
    key.startsWith('campx_pref_') ||
    key.startsWith('campx_') ||
    key.startsWith('sb-') ||
    key.includes('supabase')
  );
}

/**
 * Clears portal-related localStorage / sessionStorage entries.
 * Safe to call in Node tests with injected storage maps.
 */
export function clearPortalBrowserStorage(storage: {
  length: number;
  key(index: number): string | null;
  removeItem(key: string): void;
}): number {
  const toRemove: string[] = [];
  for (let i = 0; i < storage.length; i += 1) {
    const key = storage.key(i);
    if (key && isPortalBrowserStorageKey(key)) {
      toRemove.push(key);
    }
  }
  for (const key of toRemove) {
    storage.removeItem(key);
  }
  return toRemove.length;
}

export function broadcastLogout(channelFactory?: () => BroadcastChannel | null): void {
  try {
    const channel =
      channelFactory?.() ??
      (typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CAMPX_AUTH_BROADCAST) : null);
    if (!channel) return;
    channel.postMessage({ type: CAMPX_AUTH_LOGOUT_MESSAGE, at: Date.now() });
    channel.close();
  } catch {
    // Ignore broadcast failures (unsupported browser).
  }
}

export interface SecureLogoutDeps {
  clearTestSession: () => Promise<void>;
  signOutSupabase: () => Promise<void>;
  clearLocalStorage?: () => void;
  clearSessionStorage?: () => void;
  broadcast?: () => void;
  navigateToLogin: () => void;
}

/**
 * Ends the session using the existing sign-out path, clears portal browser data,
 * notifies other tabs, and replaces the history entry with /login.
 */
export async function performSecureLogout(deps: SecureLogoutDeps): Promise<void> {
  try {
    await deps.clearTestSession();
  } catch {
    // continue — Supabase sign-out still required
  }
  try {
    await deps.signOutSupabase();
  } catch {
    // still clear local data and leave protected pages
  }
  try {
    deps.clearLocalStorage?.();
  } catch {
    /* ignore */
  }
  try {
    deps.clearSessionStorage?.();
  } catch {
    /* ignore */
  }
  try {
    deps.broadcast?.();
  } catch {
    /* ignore */
  }
  deps.navigateToLogin();
}

/** Browser wiring for Topbar / Access Denied. */
export async function secureLogoutInBrowser(supabase: {
  auth: {
    signOut: (opts?: { scope?: 'global' | 'local' | 'others' }) => Promise<unknown>;
  };
}): Promise<void> {
  await performSecureLogout({
    clearTestSession: async () => {
      await fetch('/api/auth/test-session', { method: 'DELETE' });
    },
    signOutSupabase: async () => {
      await supabase.auth.signOut({ scope: 'global' });
    },
    clearLocalStorage: () => {
      if (typeof localStorage !== 'undefined') {
        clearPortalBrowserStorage(localStorage);
      }
    },
    clearSessionStorage: () => {
      if (typeof sessionStorage !== 'undefined') {
        clearPortalBrowserStorage(sessionStorage);
      }
    },
    broadcast: () => broadcastLogout(),
    navigateToLogin: () => {
      // replace: back button should not revive the protected page from history
      window.location.replace('/login');
    },
  });
}
