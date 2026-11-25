import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const PLATFORM_ROLES = new Set(['platform_admin', 'system_admin', 'auditor']);

/**
 * IADM-04 BFF — platform billing reads (plans, subscriptions, trial policy).
 * UI → BFF → Gateway → IADM-04 (:8102). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !PLATFORM_ROLES.has(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'plans';
  const status = searchParams.get('status') || '';
  const tenantId = searchParams.get('tenantId') || '';
  let path = '/api/v1/iadm/04/plans';
  if (view === 'subscriptions') {
    path = '/api/v1/iadm/04/subscriptions';
    const q = new URLSearchParams();
    if (status) q.set('status', status);
    if (tenantId) q.set('tenantId', tenantId);
    const qs = q.toString();
    if (qs) path += `?${qs}`;
  } else if (view === 'trial-policy') {
    path = '/api/v1/iadm/04/trial-policy';
  } else if (view === 'feature-flags') {
    path = '/api/v1/iadm/04/feature-flags';
  } else if (view === 'invoices') {
    path = tenantId
      ? `/api/v1/iadm/04/invoices?tenantId=${encodeURIComponent(tenantId)}`
      : '/api/v1/iadm/04/invoices';
  } else if (view === 'plan-changes') {
    path = status
      ? `/api/v1/iadm/04/plan-changes?status=${encodeURIComponent(status)}`
      : '/api/v1/iadm/04/plan-changes';
  } else if (view === 'offline-payments') {
    const invoiceId = searchParams.get('invoiceId') || '';
    path = invoiceId
      ? `/api/v1/iadm/04/offline-payments?invoiceId=${encodeURIComponent(invoiceId)}`
      : '/api/v1/iadm/04/offline-payments';
  } else if (view === 'online-payments') {
    path = status
      ? `/api/v1/iadm/04/online-payments?status=${encodeURIComponent(status)}`
      : '/api/v1/iadm/04/online-payments';
  } else if (view === 'payment-callback-failures') {
    path = '/api/v1/iadm/04/payment-callbacks/failures';
  } else if (view === 'renewals') {
    const bucket = searchParams.get('bucket') || '';
    path = bucket
      ? `/api/v1/iadm/04/renewals?bucket=${encodeURIComponent(bucket)}`
      : '/api/v1/iadm/04/renewals';
  } else if (view === 'usage') {
    path = tenantId
      ? `/api/v1/iadm/04/usage?tenantId=${encodeURIComponent(tenantId)}`
      : '/api/v1/iadm/04/usage';
  } else if (view === 'reports') {
    path = tenantId
      ? `/api/v1/iadm/04/reports/billing?tenantId=${encodeURIComponent(tenantId)}`
      : '/api/v1/iadm/04/reports/billing';
  } else if (view === 'audit-evidence') {
    path = tenantId
      ? `/api/v1/iadm/04/audit-evidence?tenantId=${encodeURIComponent(tenantId)}`
      : '/api/v1/iadm/04/audit-evidence';
  } else if (view === 'billing-notices') {
    path = tenantId
      ? `/api/v1/iadm/04/billing-notices?tenantId=${encodeURIComponent(tenantId)}`
      : '/api/v1/iadm/04/billing-notices';
  } else if (view === 'outage-flags') {
    path = '/api/v1/iadm/04/dev/outage-flags';
  } else if (view === 'plans') {
    path = status
      ? `/api/v1/iadm/04/plans?status=${encodeURIComponent(status)}`
      : '/api/v1/iadm/04/plans';
  }
  try {
    const res = await fetchFromGateway(path, { method: 'GET', requireAuth: true });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(
      typeof body === 'object' && body ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'plans';
  const role = resolution.session.role;
  const isR01 = role === 'platform_admin';
  const isR02 = role === 'system_admin';
  // R02 may only retry failed callbacks (US-030); all other POSTs require R01.
  if (view === 'callback-retry') {
    if (!isR01 && !isR02) {
      return NextResponse.json(
        { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
        { status: 403 }
      );
    }
  } else if (!isR01) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const raw = await req.text();
  let path = '/api/v1/iadm/04/plans';
  if (view === 'plans') {
    path = '/api/v1/iadm/04/plans';
  } else if (view === 'entitlements-check') {
    path = '/api/v1/iadm/04/entitlements/check';
  } else if (view === 'invoices') {
    path = '/api/v1/iadm/04/invoices';
  } else if (view === 'invoice-void') {
    const invoiceId = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/invoices/${encodeURIComponent(invoiceId)}/void`;
  } else if (view === 'plan-changes-decide') {
    const id = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/plan-changes/${encodeURIComponent(id)}/decide`;
  } else if (view === 'offline-payments') {
    path = '/api/v1/iadm/04/offline-payments';
  } else if (view === 'offline-reconcile') {
    const id = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/offline-payments/${encodeURIComponent(id)}/reconcile`;
  } else if (view === 'convert-trial') {
    const id = searchParams.get('subscriptionId') || '';
    path = `/api/v1/iadm/04/subscriptions/${encodeURIComponent(id)}/convert-trial`;
  } else if (view === 'online-initiate') {
    path = '/api/v1/iadm/04/online-payments/initiate';
  } else if (view === 'online-reconcile') {
    const id = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/online-payments/${encodeURIComponent(id)}/reconcile`;
  } else if (view === 'callback-retry') {
    const id = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/payment-callbacks/${encodeURIComponent(id)}/retry`;
  } else if (view === 'jobs-run') {
    path = '/api/v1/iadm/04/jobs/run';
  } else if (view === 'renewal-act') {
    const id = searchParams.get('id') || '';
    path = `/api/v1/iadm/04/renewals/${encodeURIComponent(id)}/act`;
  } else if (view === 'usage') {
    path = '/api/v1/iadm/04/usage';
  } else if (view === 'outage-flags') {
    path = '/api/v1/iadm/04/dev/outage-flags';
  } else {
    return NextResponse.json({ errorCode: 'IADM04_INVALID', detail: 'Unknown view.' }, { status: 400 });
  }
  try {
    const res = await fetchFromGateway(path, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: raw,
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(
      typeof body === 'object' && body ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
