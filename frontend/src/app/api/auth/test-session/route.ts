import { NextRequest, NextResponse } from 'next/server';
import { isTestSessionAllowed } from '@/lib/auth/test-session-gate';

export async function POST(req: NextRequest) {
  try {
    if (!isTestSessionAllowed()) {
      return NextResponse.json(
        { error: 'Trial test session endpoint is disabled in production.' },
        { status: 403 }
      );
    }

    const { role } = await req.json();

    let userSession: any;
    let redirectTo = '/';

    if (role === 'college_admin') {
      userSession = {
        id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
        email: 'campxsync@gmail.com',
        app_metadata: {
          tenant_id: '11111111-1111-1111-1111-111111111111',
          role: 'COLLEGE_ADMIN',
        },
        user_metadata: {
          full_name: 'CampXSync Staging Admin',
        },
      };
      redirectTo = '/college';
    } else if (role === 'faculty') {
      userSession = {
        id: '11111111-9e05-0000-0000-000000000001',
        email: 'faculty.a1@demo.campxsync.test',
        app_metadata: {
          tenant_id: '11111111-1111-1111-1111-111111111111',
          role: 'FACULTY',
        },
        user_metadata: {
          full_name: 'Dr. Rajesh Sharma',
        },
      };
      redirectTo = '/faculty';
    } else if (role === 'student') {
      userSession = {
        id: '11111111-5700-0000-0000-000000000001',
        email: 'student.a1@demo.campxsync.test',
        app_metadata: {
          tenant_id: '11111111-1111-1111-1111-111111111111',
          role: 'STUDENT',
        },
        user_metadata: {
          full_name: 'Student 1 Kumar',
        },
      };
      redirectTo = '/student';
    } else if (role === 'platform_admin') {
      userSession = {
        id: '99999999-9999-9999-9999-999999999999',
        email: 'platform.admin@demo.campxsync.test',
        app_metadata: {
          role: 'SUPER_ADMIN',
        },
        user_metadata: {
          full_name: 'Platform Operator',
        },
      };
      redirectTo = '/platform';
    } else if (role === 'no_profile_user') {
      userSession = {
        id: 'e0000000-0000-0000-0000-000000000001',
        email: 'noprofile@demo.campxsync.test',
        no_profile: true,
        user_metadata: {
          full_name: 'Unassigned User',
        },
      };
      redirectTo = '/access-denied?reason=no_profile';
    } else if (role === 'inactive_membership_user') {
      userSession = {
        id: 'e0000000-0000-0000-0000-000000000002',
        email: 'inactive@demo.campxsync.test',
        status: 'INACTIVE',
        membership_status: 'INACTIVE',
        app_metadata: {
          tenant_id: '11111111-1111-1111-1111-111111111111',
          membership_status: 'INACTIVE',
          role: 'COLLEGE_ADMIN',
        },
        user_metadata: {
          full_name: 'Inactive Staff Member',
        },
      };
      redirectTo = '/access-denied?reason=inactive_membership';
    } else if (role === 'suspended_tenant_user') {
      userSession = {
        id: 'e0000000-0000-0000-0000-000000000003',
        email: 'suspended@demo.campxsync.test',
        tenantStatus: 'SUSPENDED',
        app_metadata: {
          tenant_id: '33333333-3333-3333-3333-333333333333',
          tenant_status: 'SUSPENDED',
          role: 'COLLEGE_ADMIN',
        },
        user_metadata: {
          full_name: 'Suspended Tenant Admin',
        },
      };
      redirectTo = '/access-denied?reason=tenant_suspended';
    } else {
      return NextResponse.json({ error: 'Invalid role requested' }, { status: 400 });
    }

    const res = NextResponse.json({ success: true, redirectTo, session: userSession });
    res.cookies.set('campx_test_session', JSON.stringify(userSession), {
      path: '/',
      httpOnly: true,
      sameSite: 'lax',
      secure: process.env.NODE_ENV === 'production',
      maxAge: 86400,
    });

    return res;
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function DELETE() {
  const res = NextResponse.json({ success: true });
  res.cookies.delete('campx_test_session');
  return res;
}
