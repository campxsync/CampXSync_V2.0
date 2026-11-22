import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const CreateSchema = z.object({
  requestType: z.enum(['SUSPEND', 'ARCHIVE', 'DECOMMISSION', 'FULL_EXPORT']),
  reason: z.string().min(3).max(2000),
  collegeId: z.string().uuid().optional(),
});

/** US-010 / US-022 / US-023 — Institution Admin lifecycle requests. */
export async function GET(req: NextRequest) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const status = new URL(req.url).searchParams.get('status');
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}/lifecycle-requests${qs}`, {
    method: 'GET',
  });
}

export async function POST(req: NextRequest) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const parsed = CreateSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'requestType and reason required' }, { status: 400 });
  }
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}/lifecycle-requests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parsed.data),
  });
}
