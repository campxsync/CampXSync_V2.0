import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const PatchSchema = z
  .object({
    name: z.string().min(1).max(200).optional(),
    campusCode: z.string().min(1).max(64).optional(),
    location: z.string().min(1).max(500).optional(),
  })
  .refine((v) => v.name !== undefined || v.campusCode !== undefined || v.location !== undefined, {
    message: 'At least one field required',
  });

/** US-020 — college identity read/update. */
export async function GET(_req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const { id } = await context.params;
  return gatewayFetch(`/api/v1/iadm/01/colleges/${id}`, { method: 'GET' });
}

export async function PATCH(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const { id } = await context.params;
  const parsed = PatchSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'Invalid payload' }, { status: 400 });
  }
  return gatewayFetch(`/api/v1/iadm/01/colleges/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parsed.data),
  });
}