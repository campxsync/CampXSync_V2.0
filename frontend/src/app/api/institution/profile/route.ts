import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const PatchSchema = z
  .object({
    name: z.string().min(1).max(200).optional(),
    contact: z.string().min(1).max(500).optional(),
    location: z.string().min(1).max(500).optional(),
  })
  .refine((v) => v.name !== undefined || v.contact !== undefined || v.location !== undefined, {
    message: 'At least one field required',
  });

/** US-018 — own institute profile. */
export async function GET() {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}`, { method: 'GET' });
}

export async function PATCH(req: NextRequest) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const parsed = PatchSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'Invalid payload' }, { status: 400 });
  }
  return gatewayFetch(`/api/v1/iadm/01/institutes/${session.tenantId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parsed.data),
  });
}