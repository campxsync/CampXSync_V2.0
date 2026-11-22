import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * Institution Admin (R03) desk summary — IADM01-US-005.
 * Plain language: returns own institute summary via API Gateway → IADM-01; never queries the database here.
 * Path is /api/institution/desk (not /home) because repo .gitignore ignores any `home/` directory.
 */
export async function GET() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return NextResponse.json(
      { errorCode: 'UNAUTHENTICATED', detail: 'Sign in required.' },
      { status: 401 }
    );
  }
  if (resolution.session.role !== 'institution_admin' && resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    return NextResponse.json(
      { errorCode: 'NO_INSTITUTE', detail: 'No institute is linked to this login.' },
      { status: 400 }
    );
  }
  try {
    const [instRes, collegesRes] = await Promise.all([
      fetchFromGateway(`/api/v1/iadm/01/institutes/${tenantId}`, { method: 'GET', requireAuth: true }),
      fetchFromGateway(`/api/v1/iadm/01/institutes/${tenantId}/colleges`, {
        method: 'GET',
        requireAuth: true,
      }),
    ]);
    const instText = await instRes.text();
    let institute: Record<string, unknown> = {};
    try {
      institute = JSON.parse(instText) as Record<string, unknown>;
    } catch {
      /* keep */
    }
    if (!instRes.ok) {
      return NextResponse.json(
        {
          errorCode: 'UNAVAILABLE',
          detail:
            typeof institute.detail === 'string'
              ? institute.detail
              : 'unavailable',
        },
        { status: instRes.status === 403 ? 403 : 503 }
      );
    }
    let collegeCount = 0;
    if (collegesRes.ok) {
      const cBody = await collegesRes.json().catch(() => ({}));
      collegeCount = Array.isArray(cBody.colleges)
        ? cBody.colleges.length
        : Number(cBody.count) || 0;
    }
    return NextResponse.json({
      institute: {
        instituteId: institute.tenantId || tenantId,
        name: institute.name,
        code: institute.code,
        status: institute.status,
        collegeCount,
      },
    });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
