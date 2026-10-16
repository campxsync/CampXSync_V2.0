import { NextRequest, NextResponse } from 'next/server';

export async function POST(req: NextRequest) {
  try {
    // Security Gate: disable test session impersonation in production unless explicitly permitted
    if (process.env.NODE_ENV === 'production' && process.env.ENABLE_TEST_SESSION !== 'true') {
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
