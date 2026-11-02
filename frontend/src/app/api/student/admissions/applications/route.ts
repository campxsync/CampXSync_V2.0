import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateAndSubmitApplicationSchema = z.object({
  intakeId: z.string().uuid('Valid intake UUID is required'),
  declarationAccepted: z.boolean().refine(val => val === true, 'Declaration must be accepted'),
  applicantData: z.record(z.any()).default({}),
  candidateName: z.string().min(1, 'Candidate name is required'),
  email: z.string().email('Valid email is required'),
  phone: z.string().optional(),
  qualificationGrade: z.string().optional(),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 }, { status: 401 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications`;
    const res = await fetch(gatewayUrl, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        'X-Tenant-Id': tenantId,
        'X-User-Id': userId,
        'X-User-Role': 'APPLICANT',
        ...(collegeId ? { 'X-College-Id': collegeId } : {}),
      },
      cache: 'no-store',
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 }, { status: 401 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const body = await req.json();
    const parsed = CreateAndSubmitApplicationSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Validation failed', details: parsed.error.format() }, { status: 422 });
    }

    const applicantPayload = {
      intakeId: parsed.data.intakeId,
      applicantPersonId: userId,
      declarationAccepted: parsed.data.declarationAccepted,
      applicantData: JSON.stringify({
        candidateName: parsed.data.candidateName,
        email: parsed.data.email,
        phone: parsed.data.phone || '',
        qualificationGrade: parsed.data.qualificationGrade || 'Grade A',
        ...parsed.data.applicantData,
      }),
    };

    // 1. Create Draft Application
    const createUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications`;
    const createRes = await fetch(createUrl, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Tenant-Id': tenantId,
        'X-User-Id': userId,
        'X-User-Role': 'APPLICANT',
        ...(collegeId ? { 'X-College-Id': collegeId } : {}),
      },
      body: JSON.stringify(applicantPayload),
    });

    if (!createRes.ok) {
      const err = await createRes.text();
      return NextResponse.json({ error: err }, { status: createRes.status });
    }

    const createdApp = await createRes.json();
    const applicationId = createdApp.id;

    // 2. Submit Application immediately with declaration
    const submitUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/submit`;
    const submitRes = await fetch(submitUrl, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Tenant-Id': tenantId,
        'X-User-Id': userId,
        'X-User-Role': 'APPLICANT',
        ...(collegeId ? { 'X-College-Id': collegeId } : {}),
      },
      body: JSON.stringify({ declarationAccepted: true }),
    });

    if (!submitRes.ok) {
      const err = await submitRes.text();
      return NextResponse.json({ error: err, application: createdApp }, { status: submitRes.status });
    }

    const submittedApp = await submitRes.json();
    return NextResponse.json(submittedApp, { status: 201 });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
