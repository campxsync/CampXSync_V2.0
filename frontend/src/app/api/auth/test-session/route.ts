import { NextRequest, NextResponse } from 'next/server';
import { createServerClient } from '@supabase/ssr';
import { isTestSessionAllowed } from '@/lib/auth/test-session-gate';
import { loadAuditPersonaCredentials, AuditPersona } from '@/lib/auth/audit-credentials';

function buildLegacySession(role: string, authUserId?: string, email?: string) {
  if (role === 'college_admin') {
    return {
      id: authUserId || '70a8ffae-b5ad-4c61-a401-16f220893df4',
      email: email || 'campxsync@gmail.com',
      app_metadata: {
        tenant_id: 'a0000000-0000-0000-0000-000000000001',
        role: 'COLLEGE_ADMIN',
      },
      user_metadata: {
        full_name: 'CampX Dev College Administrator',
      },
    };
  }
  if (role === 'faculty') {
    return {
      id: authUserId || '11111111-9e05-0000-0000-000000000001',
      email: email || 'faculty.a1@demo.campxsync.test',
      app_metadata: {
        tenant_id: 'a0000000-0000-0000-0000-000000000001',
        role: 'FACULTY',
      },
      user_metadata: {
        full_name: 'Dr. Rajesh Sharma (Faculty Advisor)',
      },
    };
  }
  if (role === 'platform_admin') {
    return {
      id: authUserId || '54e8308c-ada3-4065-a9fe-51814af6b971',
      email: email || 'platformadmin.campx@gmail.com',
      app_metadata: {
        role: 'SUPER_ADMIN',
      },
      user_metadata: {
        full_name: 'Platform Operator',
      },
    };
  }
  return {
    id: authUserId || '81dd67d3-1d5f-4d8e-8061-a6764861f924',
    email: email || 'student1.campx@gmail.com',
    app_metadata: {
      tenant_id: 'a0000000-0000-0000-0000-000000000001',
      role: 'STUDENT',
    },
    user_metadata: {
      full_name: 'CampX Student 1',
    },
  };
}

async function establishJwtSession(
  req: NextRequest,
  res: NextResponse,
  persona: AuditPersona
): Promise<{ ok: true; userId: string; email: string } | { ok: false; error: string }> {
  const creds = loadAuditPersonaCredentials(persona);
  if (!creds) {
    return { ok: false, error: `Missing audit credentials for persona ${persona}` };
  }

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey || anonKey === 'placeholder_anon_key') {
    return { ok: false, error: 'Supabase URL/anon key not configured for JWT-backed test-session' };
  }

  const supabase = createServerClient(url, anonKey, {
    cookies: {
      getAll() {
        return req.cookies.getAll();
      },
      setAll(cookiesToSet: Array<{ name: string; value: string; options?: any }>) {
        cookiesToSet.forEach(({ name, value, options }) => {
          res.cookies.set(name, value, options);
        });
      },
    },
  });

  const { data, error } = await supabase.auth.signInWithPassword({
    email: creds.email,
    password: creds.password,
  });

  if (error || !data.session?.access_token || !data.user?.id) {
    return { ok: false, error: `Supabase sign-in failed for persona ${persona}` };
  }

  return { ok: true, userId: data.user.id, email: data.user.email || creds.email };
}

export async function POST(req: NextRequest) {
  try {
    if (!isTestSessionAllowed()) {
      return NextResponse.json(
        { error: 'Trial test session endpoint is disabled in production.' },
        { status: 403 }
      );
    }

    const { role } = await req.json();
    const persona = (role === 'platform_admin' || role === 'college_admin' || role === 'faculty' || role === 'student'
      ? role
      : null) as AuditPersona | null;
    if (!persona) {
      return NextResponse.json({ error: 'Invalid role requested' }, { status: 400 });
    }

    const redirectTo =
      persona === 'platform_admin'
        ? '/platform'
        : persona === 'faculty'
          ? '/faculty/advisees'
          : persona === 'student'
            ? '/student'
            : '/college';

    const res = NextResponse.json({ success: true, redirectTo });
    const jwt = await establishJwtSession(req, res, persona);
    if (!jwt.ok) {
      return NextResponse.json({ error: jwt.error }, { status: 503 });
    }

    const userSession = buildLegacySession(persona, jwt.userId, jwt.email);
    res.cookies.set('campx_test_session', JSON.stringify(userSession), {
      path: '/',
      httpOnly: true,
      sameSite: 'lax',
      secure: process.env.NODE_ENV === 'production',
      maxAge: 86400,
    });
    return NextResponse.json({ success: true, redirectTo, session: userSession }, { headers: res.headers });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function GET(req: NextRequest) {
  try {
    if (!isTestSessionAllowed()) {
      return NextResponse.json(
        { error: 'Trial test session endpoint is disabled in production.' },
        { status: 403 }
      );
    }

    const role = req.nextUrl.searchParams.get('role') || 'college_admin';
    const returnTo = req.nextUrl.searchParams.get('returnTo') || '/college/documents';
    const persona = (role === 'platform_admin' || role === 'college_admin' || role === 'faculty' || role === 'student'
      ? role
      : null) as AuditPersona | null;
    if (!persona) {
      return NextResponse.json({ error: 'Invalid role requested' }, { status: 400 });
    }

    const res = NextResponse.redirect(new URL(returnTo, req.url));
    const jwt = await establishJwtSession(req, res, persona);
    if (!jwt.ok) {
      return NextResponse.json(
        {
          error: jwt.error,
          hint: 'H-3 requires real Supabase JWT for personas. Ensure .env.audit credentials and NEXT_PUBLIC_SUPABASE_* are available to Next.js.',
        },
        { status: 503 }
      );
    }

    const userSession = buildLegacySession(persona, jwt.userId, jwt.email);
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
