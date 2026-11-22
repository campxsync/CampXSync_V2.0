import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const CreateSchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(200),
  campusCode: z.string().min(1).max(64),
  location: z.string().min(1).max(500),
});

/** US-019 list / US-006 register — institution-scoped colleges via IADM-01 gateway. */
export async function GET(req: NextRequest) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;

  const status = new URL(req.url).searchParams.get('status') || '';
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}/colleges${qs}`, { method: 'GET' });
}

export async function POST(req: NextRequest) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;

  const parsed = CreateSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'Invalid payload' }, { status: 400 });
  }
  const { code, name, campusCode, location } = parsed.data;
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}/colleges`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, name, campusCode, location }),
  });
}