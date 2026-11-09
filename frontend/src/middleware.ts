import { createServerClient } from '@supabase/ssr';
import { NextResponse, type NextRequest } from 'next/server';
import { isTestSessionAllowed } from '@/lib/auth/test-session-gate';

export async function middleware(request: NextRequest) {
  let response = NextResponse.next({
    request: {
      headers: request.headers,
    },
  });

  const pathname = request.nextUrl.pathname;

  // 1. Allow public routes
  if (
    pathname === '/login' ||
    pathname === '/reset-password' ||
    pathname.startsWith('/auth/callback') ||
    pathname.startsWith('/access-denied') ||
    pathname.startsWith('/_next') ||
    pathname.startsWith('/api/auth') ||
    pathname.includes('.')
  ) {
    return response;
  }

  // 2. Initialize Supabase SSR Client with cookies
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://kpwdvvcschankgggitnj.supabase.co';
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'placeholder_anon_key';

  const supabase = createServerClient(url, anonKey, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet: Array<{ name: string; value: string; options?: any }>) {
        cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
        response = NextResponse.next({
          request,
        });
        cookiesToSet.forEach(({ name, value, options }) =>
          response.cookies.set(name, value, options)
        );
      },
    },
  });

  // 3. Get session
  let user: any = null;
  const { data } = await supabase.auth.getUser();
  user = data?.user;

  // Fallback to test session cookie for local/dev and Vercel Preview only
  if (!user && isTestSessionAllowed()) {
    const testCookie = request.cookies.get('campx_test_session')?.value;
    if (testCookie) {
      try {
        user = JSON.parse(testCookie);
      } catch {}
    }
  }

  if (!user) {
    if (pathname.startsWith('/api/')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to access API endpoint.',
        },
        { status: 401 }
      );
    }

    // Preserve recovery parameters (code, error, error_code, type) if GoTrue redirected to Site URL
    const hasCode = request.nextUrl.searchParams.has('code');
    const isRecoveryType = request.nextUrl.searchParams.get('type') === 'recovery';
    const hasAuthError = request.nextUrl.searchParams.has('error') || request.nextUrl.searchParams.has('error_code');

    if (hasCode) {
      const callbackUrl = new URL('/auth/callback', request.url);
      callbackUrl.searchParams.set('next', '/reset-password');
      request.nextUrl.searchParams.forEach((val, key) => {
        if (key !== 'next') {
          callbackUrl.searchParams.set(key, val);
        }
      });
      return NextResponse.redirect(callbackUrl);
    }

    if (isRecoveryType || hasAuthError) {
      const resetUrl = new URL('/reset-password', request.url);
      request.nextUrl.searchParams.forEach((val, key) => {
        resetUrl.searchParams.set(key, val);
      });
      return NextResponse.redirect(resetUrl);
    }

    if (pathname === '/') {
      return response;
    }

    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('returnTo', pathname);
    return NextResponse.redirect(loginUrl);
  }

  const email = (user.email || '').toLowerCase();
  const rawRole = (user.app_metadata?.role || '').toUpperCase();

  const isPlatformUser = rawRole === 'SUPER_ADMIN' || rawRole === 'PLATFORM_ADMIN';
  const isCollegeAdminUser = (rawRole === 'COLLEGE_ADMIN' || rawRole === 'INSTITUTION_ADMIN' || rawRole === 'TENANT_ADMIN' || rawRole === 'ADMIN') && !isPlatformUser;
  const isFacultyUser = (rawRole === 'FACULTY' || rawRole === 'HOD' || rawRole === 'DEAN' || rawRole === 'TEACHER') && !isPlatformUser;
  const isStudentUser = rawRole === 'STUDENT' && !isPlatformUser;

  // Enforce inactive membership guard
  const isInactive = user.membership_status === 'INACTIVE' || user.status === 'INACTIVE' || user.app_metadata?.membership_status === 'INACTIVE';
  if (isInactive && !pathname.startsWith('/access-denied') && !pathname.startsWith('/login') && !pathname.startsWith('/api/auth')) {
    if (pathname.startsWith('/api/')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Account Membership Inactive',
          status: 403,
          detail: 'Your institutional membership has expired or has been deactivated.',
        },
        { status: 403 }
      );
    }
    return NextResponse.redirect(new URL('/access-denied?reason=inactive_membership', request.url));
  }

  // Enforce no-profile guard
  const isNoProfile = user.no_profile === true || user.app_metadata?.no_profile === true;
  if (isNoProfile && !pathname.startsWith('/access-denied') && !pathname.startsWith('/login') && !pathname.startsWith('/api/auth')) {
    if (pathname.startsWith('/api/')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Institutional Profile Not Found',
          status: 403,
          detail: 'No active organizational profile is attached to this account.',
        },
        { status: 403 }
      );
    }
    return NextResponse.redirect(new URL('/access-denied?reason=no_profile', request.url));
  }

  // Enforce institutional tenant suspension guard (denies access with reason=tenant_suspended)
  const isSuspendedTenant = (user.app_metadata?.tenant_status === 'SUSPENDED' || user.tenantStatus === 'SUSPENDED' || user.tenant_status === 'SUSPENDED') && !isPlatformUser;
  if (isSuspendedTenant && !pathname.startsWith('/access-denied') && !pathname.startsWith('/login') && !pathname.startsWith('/api/auth')) {
    if (pathname.startsWith('/api/')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Institution Workspace Suspended',
          status: 403,
          detail: 'Access for this educational institution is currently suspended.',
        },
        { status: 403 }
      );
    }
    return NextResponse.redirect(new URL('/access-denied?reason=tenant_suspended', request.url));
  }

  // 4. Role Guards and Root Routing
  // When an authoritative role claim is present in the JWT (e.g. via token hook or test session),
  // enforce fast Edge route guards.
  // When the JWT lacks an app_metadata.role claim (as on staging without token hooks),
  // allow requests to proceed to Server Components where resolveUserRole authoritatively queries PostgreSQL.
  if (rawRole) {
    // If visiting root '/', redirect based on user role (fail closed if unassigned)
    if (pathname === '/') {
      if (isPlatformUser) {
        return NextResponse.redirect(new URL('/platform', request.url));
      }
      if (isCollegeAdminUser) {
        return NextResponse.redirect(new URL('/college', request.url));
      }
      if (isFacultyUser) {
        return NextResponse.redirect(new URL('/faculty', request.url));
      }
      if (isStudentUser) {
        return NextResponse.redirect(new URL('/student', request.url));
      }
      return NextResponse.redirect(new URL('/access-denied?reason=no_role_assigned', request.url));
    }

    // Server-side Route Guards for role prefixes (Fail Closed)
    if (pathname.startsWith('/platform')) {
      if (!isPlatformUser) {
        return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
      }
    }

    if (pathname.startsWith('/college')) {
      if (!isCollegeAdminUser) {
        return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
      }
    }

    if (pathname.startsWith('/faculty')) {
      if (!isFacultyUser && !isCollegeAdminUser) {
        return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
      }
    }

    if (pathname.startsWith('/student')) {
      if (!isStudentUser) {
        return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
      }
    }
  }

  return response;
}

export const config = {
  matcher: [
    /*
     * Match all request paths except:
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - images - .svg, .png, .jpg, .jpeg, .gif, .webp
     */
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
