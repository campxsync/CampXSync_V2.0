import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const PostSchema = z.object({
  email: z.string().email().optional(),
  displayName: z.string().min(1).max(200).optional(),
  correlationId: z.string().optional(),
  simulateInviteFailure: z.boolean().optional(),
});

/** US-007 — first College Admin (R04) onboarding. */
export async function GET(_req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const { id } = await context.params;
  return gatewayFetch(`/api/v1/iadm/01/colleges/${id}/onboarding`, { method: 'GET' });
}

export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const { id } = await context.params;
  const parsed = PostSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'Invalid payload' }, { status: 400 });
  }
  return gatewayFetch(`/api/v1/iadm/01/colleges/${id}/first-admin/onboarding`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parsed.data),
  });
}