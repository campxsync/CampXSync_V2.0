import { createServerClient } from '@supabase/ssr';
import { NextResponse, type NextRequest } from 'next/server';

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

  // Fallback to test session cookie for walkthrough only in development / non-production mode
  const allowTestSession = process.env.NODE_ENV !== 'production' || process.env.ENABLE_TEST_SESSION === 'true';
  if (!user && allowTestSession) {
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
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('returnTo', pathname);
    return NextResponse.redirect(loginUrl);
  }

  const email = (user.email || '').toLowerCase();
  const rawRole = (user.app_metadata?.role || user.user_metadata?.role || '').toUpperCase();

  const isPlatformUser = email.startsWith('platform.admin@') || rawRole === 'SUPER_ADMIN' || rawRole === 'PLATFORM_ADMIN';
  const isFacultyUser = rawRole.includes('FACULTY') || email.startsWith('faculty.');
  const isStudentUser = rawRole.includes('STUDENT') || email.startsWith('student.');
  const isCollegeAdminUser = (rawRole.includes('ADMIN') || email === 'campxsync@gmail.com') && !isPlatformUser;

  // Enforce institutional tenant suspension guard (denies access with reason=tenant_suspended)
  const isSuspendedTenant = (user.app_metadata?.tenant_status === 'SUSPENDED' || user.user_metadata?.tenant_status === 'SUSPENDED' || user.tenantStatus === 'SUSPENDED' || user.tenant_status === 'SUSPENDED') && !isPlatformUser;
  if (isSuspendedTenant && !pathname.startsWith('/access-denied') && !pathname.startsWith('/login') && !pathname.startsWith('/api/auth')) {
    return NextResponse.redirect(new URL('/access-denied?reason=tenant_suspended', request.url));
  }

  // If visiting root '/', redirect based on user role
  if (pathname === '/') {
    if (isPlatformUser) {
      return NextResponse.redirect(new URL('/platform', request.url));
    }
    if (isFacultyUser) {
      return NextResponse.redirect(new URL('/faculty', request.url));
    }
    if (isStudentUser) {
      return NextResponse.redirect(new URL('/student', request.url));
    }
    return NextResponse.redirect(new URL('/college', request.url));
  }

  // 4. Server-side Route Guards for role prefixes
  if (pathname.startsWith('/platform')) {
    if (!isPlatformUser) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
    }
  }

  if (pathname.startsWith('/college')) {
    if (isStudentUser || isFacultyUser || isPlatformUser) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
    }
  }

  if (pathname.startsWith('/faculty')) {
    if (isStudentUser || isPlatformUser) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
    }
  }

  if (pathname.startsWith('/student')) {
    if (isFacultyUser || isPlatformUser || isCollegeAdminUser) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
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
