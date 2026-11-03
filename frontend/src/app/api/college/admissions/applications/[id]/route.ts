import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const { id: applicationId } = await params;
    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'COLLEGE_ADMIN');

    const [appRes, docsRes, revsRes, decRes, offRes, handRes] = await Promise.all([
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}`, { method: 'GET', headers, cache: 'no-store' }),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/reviews`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/decision`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/offer`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/handover`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
    ]);

    if (!appRes.ok) {
      const err = await appRes.text();
      return NextResponse.json({ error: err }, { status: appRes.status });
    }

    const application = await appRes.json();
    const documents = docsRes && docsRes.ok ? await docsRes.json() : [];
    const reviews = revsRes && revsRes.ok ? await revsRes.json() : [];
    const decision = decRes && decRes.ok ? await decRes.json() : null;
    const offer = offRes && offRes.ok ? await offRes.json() : null;
    const handover = handRes && handRes.ok ? await handRes.json() : null;

    return NextResponse.json({ application, documents, reviews, decision, offer, handover });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
