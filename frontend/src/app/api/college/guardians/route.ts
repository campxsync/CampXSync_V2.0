import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

function staffRole(sessionRole?: string | null) {
  if (sessionRole === 'platform_admin') {
    return 'INSTITUTION_ADMIN';
  }
  return 'COLLEGE_ADMIN';
}

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const studentId = req.nextUrl.searchParams.get('studentId');
  if (!studentId) {
    return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/students/${encodeURIComponent(studentId)}`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  const body = await res.text();
  return new NextResponse(body, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  if (payload.action === 'save-contact' || payload.action === 'change-contact' || payload.action === 'remove-contact') {
    if (!payload.studentId) {
      return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
    }
    if (payload.action !== 'save-contact' && !payload.contactId) {
      return NextResponse.json({ title: 'Contact id is required', status: 422 }, { status: 422 });
    }
    const contactPath = payload.action === 'save-contact'
      ? `${GATEWAY_BASE_URL}/api/v1/guardians/students/${encodeURIComponent(payload.studentId)}/emergency-contacts`
      : `${GATEWAY_BASE_URL}/api/v1/guardians/emergency-contacts/${encodeURIComponent(payload.contactId)}${payload.action === 'remove-contact' ? '/remove' : ''}`;
    const contact = await fetch(contactPath, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        studentId: payload.studentId,
        name: payload.contactName,
        relationship: payload.contactRelationship,
        phone: payload.contactPhone,
      }),
      cache: 'no-store',
    });
    const contactText = await contact.text();
    return new NextResponse(contactText, { status: contact.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'load-history') {
    if (!payload.studentId) {
      return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
    }
    const history = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/students/${encodeURIComponent(payload.studentId)}/relationship-history`, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    const historyText = await history.text();
    return new NextResponse(historyText, { status: history.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'view-authorized') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const viewed = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/students`, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    const viewedText = await viewed.text();
    return new NextResponse(viewedText, { status: viewed.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'revoke-access') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const revoked = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/revoke`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
      cache: 'no-store',
    });
    const revokedText = await revoked.text();
    return new NextResponse(revokedText, { status: revoked.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'set-period' || payload.action === 'check-access') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const periodPath = payload.action === 'set-period' ? 'period' : 'access-check';
    const period = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/${periodPath}`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ validFrom: payload.validFrom, validTo: payload.validTo }),
      cache: 'no-store',
    });
    const periodText = await period.text();
    return new NextResponse(periodText, { status: period.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'grant-access') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const granted = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/access`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        categories: payload.categories,
        purpose: payload.purpose,
        validTo: payload.validTo,
      }),
      cache: 'no-store',
    });
    const grantedText = await granted.text();
    return new NextResponse(grantedText, { status: granted.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'mark-state') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const marked = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/state`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ state: payload.state }),
      cache: 'no-store',
    });
    const markedText = await marked.text();
    return new NextResponse(markedText, { status: marked.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'deactivate') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const ended = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/deactivate`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ reasonCode: 'DISPUTE' }),
      cache: 'no-store',
    });
    const endedText = await ended.text();
    return new NextResponse(endedText, { status: ended.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'review-evidence') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const review = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/review`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: payload.decision, reasonCode: payload.reasonCode }),
      cache: 'no-store',
    });
    const reviewText = await review.text();
    return new NextResponse(reviewText, { status: review.status, headers: { 'Content-Type': 'application/json' } });
  }
  if (payload.action === 'submit-evidence') {
    if (!payload.guardianId) {
      return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
    }
    const evidence = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}/evidence`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ documentType: payload.documentType }),
      cache: 'no-store',
    });
    const evidenceText = await evidence.text();
    return new NextResponse(evidenceText, { status: evidence.status, headers: { 'Content-Type': 'application/json' } });
  }
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: payload.studentId,
      firstName: payload.firstName,
      lastName: payload.lastName,
      relationship: payload.relationship,
      email: payload.email,
      phone: payload.phone,
      primary: payload.primary === true,
    }),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}

export async function PATCH(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  if (!payload.guardianId) {
    return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      relationship: payload.relationship,
      primary: payload.primary === true,
    }),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
