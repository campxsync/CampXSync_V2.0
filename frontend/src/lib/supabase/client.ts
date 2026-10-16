import { createBrowserClient } from '@supabase/ssr';

export function createClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

  if (!url || !anonKey) {
    // Provide a safe placeholder if not set to prevent build crashes
    if (typeof window !== 'undefined' && (!url || !anonKey)) {
      console.warn('Supabase URL or Anon Key is missing in environment variables.');
    }
  }

  return createBrowserClient(
    url || 'https://kpwdvvcschankgggitnj.supabase.co',
    anonKey || 'placeholder_anon_key'
  );
}
