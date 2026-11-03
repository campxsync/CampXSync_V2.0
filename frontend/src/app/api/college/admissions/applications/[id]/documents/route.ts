import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const AttachDocumentSchema = z.object({
  documentType: z.string().min(1, 'Document type is required'),
  documentRefUrl: z.string().min(1, 'Document reference is required'),
  fileName: z.string().min(1, 'File name is required'),
  fileSizeBytes: z.number().int().positive('File size must be positive'),
  mimeType: z.string().min(1, 'MIME type is required'),
});

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const { id: applicationId } = await params;
    const body = await req.json();
    const parsed = AttachDocumentSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Validation failed', details: parsed.error.format() }, { status: 422 });
    }

    const headers = await getGatewayHeaders(
      req,
      supabase,
      resolution.session,
      resolution.session.role === 'student' ? 'APPLICANT' : 'COLLEGE_ADMIN'
    );
    headers['Content-Type'] = 'application/json';

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents`;
    const res = await fetch(gatewayUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        documentType: parsed.data.documentType,
        storagePath: (parsed.data as any).storagePath || parsed.data.documentRefUrl,
        fileName: parsed.data.fileName,
        fileSizeBytes: parsed.data.fileSizeBytes,
        mimeType: parsed.data.mimeType,
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data, { status: 201 });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
