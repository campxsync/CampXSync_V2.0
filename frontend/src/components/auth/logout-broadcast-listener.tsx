'use client';

import { useEffect } from 'react';
import {
  CAMPX_AUTH_BROADCAST,
  CAMPX_AUTH_LOGOUT_MESSAGE,
  clearPortalBrowserStorage,
} from '@/lib/auth/secure-logout';

/**
 * STM-10-US-020 — when another tab signs out, clear portal data and leave protected pages.
 */
export function LogoutBroadcastListener() {
  useEffect(() => {
    const leaveIfLoggedOut = () => {
      try {
        clearPortalBrowserStorage(localStorage);
        clearPortalBrowserStorage(sessionStorage);
      } catch {
        /* ignore */
      }
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login');
      }
    };

    let channel: BroadcastChannel | null = null;
    if (typeof BroadcastChannel !== 'undefined') {
      channel = new BroadcastChannel(CAMPX_AUTH_BROADCAST);
      channel.onmessage = (event) => {
        if (event?.data?.type === CAMPX_AUTH_LOGOUT_MESSAGE) {
          leaveIfLoggedOut();
        }
      };
    }

    // If the browser restores a protected page from the back-forward cache after logout, bounce.
    const onPageShow = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      // Cookie/session gone → middleware would redirect on a full load; force leave from bfcache.
      const hasSb = document.cookie.split(';').some((c) => c.trim().startsWith('sb-'));
      const hasTest = document.cookie.split(';').some((c) => c.trim().startsWith('campx_test_session='));
      if (!hasSb && !hasTest && !window.location.pathname.startsWith('/login')) {
        window.location.replace('/login');
      }
    };
    window.addEventListener('pageshow', onPageShow);

    return () => {
      channel?.close();
      window.removeEventListener('pageshow', onPageShow);
    };
  }, []);

  return null;
}
