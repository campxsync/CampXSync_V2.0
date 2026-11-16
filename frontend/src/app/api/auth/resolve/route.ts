import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole, UserRole } from '@/lib/auth/resolve-role';
import { defaultDashboardForRole, isReturnToAuthorizedForRole } from '@/lib/auth/role-policy';

export const dynamic = 'force-dynamic';

function getAuthorizedDestination(role: string, returnTo: string | null): string {
  const uiRole = role as UserRole;
  const defaultDashboard = defaultDashboardForRole(uiRole);

  if (!returnTo || typeof returnTo !== 'string') {
    return defaultDashboard;
  }

  // Prevent open redirects
  if (!returnTo.startsWith('/') || returnTo.startsWith('//')) {
    return defaultDashboard;
  }

  return isReturnToAuthorizedForRole(uiRole, returnTo) ? returnTo : defaultDashboard;
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

