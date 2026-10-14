import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { getSupabase, isSupabaseConfigured } from '../services/supabaseClient';
import { isMockFallbackEnabled } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [authError, setAuthError] = useState(null);
  const isConfigured = isSupabaseConfigured();
  const mockFallback = isMockFallbackEnabled();

  // Initialize and restore session
  useEffect(() => {
    let mounted = true;
    const supabase = getSupabase();

    if (!supabase) {
      if (mounted) {
        setIsLoading(false);
      }
      return;
    }

    // 1. Get initial active session
    supabase.auth.getSession().then(({ data: { session: initialSession }, error }) => {
      if (!mounted) return;
      if (error) {
        setAuthError(error.message);
        setSession(null);
        setUser(null);
      } else {
        setSession(initialSession);
        setUser(initialSession?.user || null);
        setAuthError(null);
      }
      setIsLoading(false);
    }).catch(err => {
      if (!mounted) return;
      setAuthError(err.message || 'Failed to restore authentication session');
      setIsLoading(false);
    });

    // 2. Subscribe to auth state changes (TOKEN_REFRESHED, SIGNED_IN, SIGNED_OUT, etc.)
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, currentSession) => {
      if (!mounted) return;
      if (event === 'SIGNED_OUT') {
        setSession(null);
        setUser(null);
        setAuthError(null);
      } else if (currentSession) {
        setSession(currentSession);
        setUser(currentSession.user);
        setAuthError(null);
      } else {
        setSession(null);
        setUser(null);
      }
      setIsLoading(false);
    });

    return () => {
      mounted = false;
      subscription?.unsubscribe();
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const supabase = getSupabase();
    if (!supabase) {
      const err = 'Supabase client is not configured. Missing VITE_SUPABASE_URL or VITE_SUPABASE_ANON_KEY.';
      setAuthError(err);
      throw new Error(err);
    }
    setIsLoading(true);
    setAuthError(null);
    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password
      });
      if (error) {
        setAuthError(error.message);
        throw error;
      }
      setSession(data.session);
      setUser(data.user);
      return data;
    } catch (err) {
      setAuthError(err.message || 'Login failed');
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    const supabase = getSupabase();
    if (supabase) {
      try {
        await supabase.auth.signOut();
      } catch (err) {
        console.warn('Error during Supabase signOut:', err);
      }
    }
    setSession(null);
    setUser(null);
    setAuthError(null);
  }, []);

  // Extract trusted tenant and identity claims from session
  const accessToken = session?.access_token || null;
  const tenantId = user?.app_metadata?.tenant_id || user?.user_metadata?.tenant_id || null;
  const userRole = user?.app_metadata?.role || (user ? 'authenticated' : 'ANONYMOUS');
  const userId = user?.id || null;
  const userEmail = user?.email || null;

  // Context bundle passed to API requests
  const apiContext = useMemo(() => {
    if (!session) {
      return {
        accessToken: null,
        userId: null,
        tenantId: null,
        userRole: 'ANONYMOUS',
        userEmail: null,
        isAuthenticated: false
      };
    }
    return {
      accessToken,
      userId,
      tenantId,
      userRole,
      userEmail,
      isAuthenticated: true
    };
  }, [session, accessToken, userId, tenantId, userRole, userEmail]);

  const value = useMemo(() => ({
    session,
    user,
    accessToken,
    tenantId,
    userRole,
    userId,
    userEmail,
    isAuthenticated: Boolean(session && session.access_token),
    isLoading,
    authError,
    isConfigured,
    isMockFallback: mockFallback,
    apiContext,
    login,
    logout,
    clearError: () => setAuthError(null)
  }), [
    session,
    user,
    accessToken,
    tenantId,
    userRole,
    userId,
    userEmail,
    isLoading,
    authError,
    isConfigured,
    mockFallback,
    apiContext,
    login,
    logout
  ]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
