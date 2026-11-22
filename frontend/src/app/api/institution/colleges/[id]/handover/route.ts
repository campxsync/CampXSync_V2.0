import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { gatewayFetch, requireInstitutionAdmin } from '@/lib/api/institution-session';

export const dynamic = 'force-dynamic';

const Schema = z.object({
  notes: z.string().max(2000).optional(),
});

/** US-008 — CADM handover. */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const session = await requireInstitutionAdmin();
  if (!session.ok) return session.response;
  const { id } = await context.params;
  const parsed = Schema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'Invalid payload' }, { status: 400 });
  }
  return gatewayFetch(`/api/v1/iadm/01/colleges/${id}/handover`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ notes: parsed.data.notes ?? '' }),
  });
}