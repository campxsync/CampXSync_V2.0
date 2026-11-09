import { createServerClient } from '@supabase/ssr';
import { NextResponse, type NextRequest } from 'next/server';

function getSafeNextPath(rawNext: string | null, baseUrl: string): string {
  if (!rawNext) {
    return '/reset-password';
  }

  try {
    // Disallow control characters, backslashes, or leading protocol-relative slashes
    if (rawNext.includes('\\') || rawNext.startsWith('//')) {
      return '/reset-password';
    }

    const base = new URL(baseUrl);
    const resolved = new URL(rawNext, base);

    // Enforce strict origin matching (prevents https://evil.com, etc.)
    if (resolved.origin !== base.origin) {
      return '/reset-password';
    }

    // Enforce internal pathname
    if (!resolved.pathname.startsWith('/')) {
      return '/reset-password';
    }

    return resolved.pathname + resolved.search + resolved.hash;
  } catch {
    return '/reset-password';
  }
}

export async function GET(request: NextRequest) {
  const requestUrl = new URL(request.url);
  const code = requestUrl.searchParams.get('code');
  const error = requestUrl.searchParams.get('error');
  const errorDescription = requestUrl.searchParams.get('error_description');
  const next = getSafeNextPath(requestUrl.searchParams.get('next'), request.url);

  if (error) {
    const redirectUrl = new URL('/reset-password', request.url);
    redirectUrl.searchParams.set('error', error);
    if (errorDescription) {
      redirectUrl.searchParams.set('error_description', errorDescription);
    }
    return NextResponse.redirect(redirectUrl);
  }

  if (code) {
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://kpwdvvcschankgggitnj.supabase.co';
    const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'placeholder_anon_key';

    const targetUrl = new URL(next, request.url);
    const response = NextResponse.redirect(targetUrl);

    const supabase = createServerClient(url, anonKey, {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet: Array<{ name: string; value: string; options?: any }>) {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
          cookiesToSet.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options)
          );
        },
      },
    });

    const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
    if (!exchangeError) {
      return response;
    }

    const failureUrl = new URL('/reset-password', request.url);
    failureUrl.searchParams.set('error', 'auth_exchange_failed');
    failureUrl.searchParams.set('error_description', exchangeError.message);
    return NextResponse.redirect(failureUrl);
  }

  return NextResponse.redirect(new URL('/login', request.url));
}
