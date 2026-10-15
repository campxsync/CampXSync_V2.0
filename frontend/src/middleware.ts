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
  const { data: { user } } = await supabase.auth.getUser();

  if (!user) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('returnTo', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // If visiting root '/', redirect based on user role
  if (pathname === '/') {
    const email = user.email || '';
    if (email.startsWith('platform.admin@') || user.app_metadata?.role === 'SUPER_ADMIN') {
      return NextResponse.redirect(new URL('/platform', request.url));
    }
    // Check metadata hints or default to college
    const role = (user.app_metadata?.role || user.user_metadata?.role || '').toLowerCase();
    if (role.includes('platform')) {
      return NextResponse.redirect(new URL('/platform', request.url));
    }
    if (role.includes('faculty') || email.startsWith('faculty.')) {
      return NextResponse.redirect(new URL('/faculty', request.url));
    }
    if (role.includes('student') || email.startsWith('student.')) {
      return NextResponse.redirect(new URL('/student', request.url));
    }
    return NextResponse.redirect(new URL('/college', request.url));
  }

  // 4. Server-side Route Guards for role prefixes
  const email = user.email || '';
  const isPlatformUser = email.startsWith('platform.admin@') || user.app_metadata?.role === 'SUPER_ADMIN';

  if (pathname.startsWith('/platform')) {
    if (!isPlatformUser) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
    }
  }

  if (pathname.startsWith('/college')) {
    if (email.startsWith('student.') || email.startsWith('faculty.')) {
      return NextResponse.redirect(new URL('/access-denied?reason=forbidden', request.url));
    }
  }

  if (pathname.startsWith('/faculty')) {
    if (email.startsWith('student.')) {
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
