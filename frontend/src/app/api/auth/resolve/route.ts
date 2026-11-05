import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

export const dynamic = 'force-dynamic';

function getAuthorizedDestination(role: string, returnTo: string | null): string {
  let defaultDashboard = '/';
  if (role === 'platform_admin') defaultDashboard = '/platform';
  else if (role === 'college_admin') defaultDashboard = '/college';
  else if (role === 'faculty') defaultDashboard = '/faculty';
  else if (role === 'student') defaultDashboard = '/student';

  if (!returnTo || typeof returnTo !== 'string') {
    return defaultDashboard;
  }

  // Prevent open redirects
  if (!returnTo.startsWith('/') || returnTo.startsWith('//')) {
    return defaultDashboard;
  }

  // Check if returnTo is authorized for the resolved role
  let isAuthorized = false;
  if (role === 'platform_admin' && returnTo.startsWith('/platform')) {
    isAuthorized = true;
  } else if (role === 'college_admin' && returnTo.startsWith('/college')) {
    isAuthorized = true;
  } else if (
    role === 'faculty' &&
    (returnTo.startsWith('/faculty') || returnTo.startsWith('/college/admissions/applications'))
  ) {
    isAuthorized = true;
  } else if (role === 'student' && returnTo.startsWith('/student')) {
    isAuthorized = true;
  }

  return isAuthorized ? returnTo : defaultDashboard;
}

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({
        success: false,
        reason: resolution.reason,
      });
    }

    const { role } = resolution.session;
    const returnTo = req.nextUrl.searchParams.get('returnTo');
    const destination = getAuthorizedDestination(role, returnTo);

    return NextResponse.json({
      success: true,
      role,
      destination,
    });
  } catch (error: any) {
    console.error('[api/auth/resolve] Resolution error:', error?.message || error);
    return NextResponse.json(
      {
        success: false,
        reason: 'db_error',
      },
      { status: 500 }
    );
  }
}

export async function POST(req: NextRequest) {
  try {
    let returnTo: string | null = null;
    try {
      const body = await req.json();
      returnTo = body?.returnTo || null;
    } catch {
      returnTo = req.nextUrl.searchParams.get('returnTo');
    }

    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({
        success: false,
        reason: resolution.reason,
      });
    }

    const { role } = resolution.session;
    const destination = getAuthorizedDestination(role, returnTo);

    return NextResponse.json({
      success: true,
      role,
      destination,
    });
  } catch (error: any) {
    console.error('[api/auth/resolve] Resolution error:', error?.message || error);
    return NextResponse.json(
      {
        success: false,
        reason: 'db_error',
      },
      { status: 500 }
    );
  }
}

