import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const WidgetSchema = z.object({
  order: z.array(z.string().max(50)).max(20).default([]),
  hidden: z.array(z.string().max(50)).max(20).default([]),
});

const ShortcutSchema = z.object({
  id: z.string().max(64),
  label: z.string().max(80),
  path: z.string().max(200),
});

const DisplaySchema = z.object({
  theme: z.enum(['light', 'dark', 'system']).optional(),
  locale: z.string().max(10).optional(),
  timezone: z.string().max(50).optional(),
  tableDensity: z.enum(['comfortable', 'compact']).optional(),
  accessibility: z
    .object({
      highContrast: z.boolean().optional(),
      reducedMotion: z.boolean().optional(),
      largeFont: z.boolean().optional(),
    })
    .optional(),
});

const PortalPreferencesBodySchema = z.object({
  version: z.number().int().positive().optional(),
  widgets: WidgetSchema.optional(),
  shortcuts: z.array(ShortcutSchema).max(20).optional(),
  display: DisplaySchema.optional(),
});

async function resolveStudentId(
  req: NextRequest,
  headers: Record<string, string>
): Promise<{ id: string } | { error: NextResponse }> {
  const meRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  if (!meRes.ok) {
    const detail = await meRes.text();
    return {
      error: NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/student-not-found',
          title: 'Student profile unavailable',
          status: meRes.status,
          detail,
        },
        { status: meRes.status }
      ),
    };
  }
  const me = await meRes.json();
  if (!me?.id) {
    return {
      error: NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/student-not-found',
          title: 'Student id missing',
          status: 404,
        },
        { status: 404 }
      ),
    };
  }
  return { id: String(me.id) };
}

/**
 * BFF: STM-10-US-012/013/014 portal preferences via API Gateway → student-service.
 * Does not touch institute-admin /api/v1/user/preferences.
 */
export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);
    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required for portal preferences.',
        },
        { status: 401 }
      );
    }
    if (resolution.session.role !== 'student') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only students may use the student portal preferences store.',
        },
        { status: 403 }
      );
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
    const student = await resolveStudentId(req, headers);
    if ('error' in student) return student.error;

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/students/${encodeURIComponent(student.id)}/portal-preferences`;
    const res = await fetch(gatewayUrl, { method: 'GET', headers, cache: 'no-store' });
    if (!res.ok) {
      const detail = await res.text();
      return NextResponse.json({ error: detail }, { status: res.status });
    }
    const data = await res.json();
    return NextResponse.json({ success: true, isAccountSynced: true, preferences: data });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function PUT(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);
    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to save portal preferences.',
        },
        { status: 401 }
      );
    }
    if (resolution.session.role !== 'student') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only students may use the student portal preferences store.',
        },
        { status: 403 }
      );
    }

    let body: unknown;
    try {
      body = await req.json();
    } catch {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Invalid JSON',
          status: 400,
        },
        { status: 400 }
      );
    }

    const parsed = PortalPreferencesBodySchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-error',
          title: 'Invalid portal preferences',
          status: 422,
          errors: parsed.error.format(),
        },
        { status: 422 }
      );
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
    headers['Content-Type'] = 'application/json';
    const student = await resolveStudentId(req, headers);
    if ('error' in student) return student.error;

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/students/${encodeURIComponent(student.id)}/portal-preferences`;

    // Merge with existing row so widget-only saves do not wipe shortcuts/display.
    let merged: Record<string, unknown> = { version: 1, ...parsed.data };
    try {
      const existingRes = await fetch(gatewayUrl, { method: 'GET', headers, cache: 'no-store' });
      if (existingRes.ok) {
        const existing = await existingRes.json();
        merged = {
          version: parsed.data.version || existing.version || 1,
          widgets: parsed.data.widgets ?? existing.widgets,
          shortcuts: parsed.data.shortcuts ?? existing.shortcuts,
          display: parsed.data.display
            ? { ...(existing.display || {}), ...parsed.data.display }
            : existing.display,
        };
      }
    } catch {
      // proceed with payload only
    }

    const res = await fetch(gatewayUrl, {
      method: 'PUT',
      headers,
      body: JSON.stringify(merged),
      cache: 'no-store',
    });
    if (!res.ok) {
      const detail = await res.text();
      return NextResponse.json({ error: detail }, { status: res.status });
    }
    const data = await res.json();
    return NextResponse.json({ success: true, isAccountSynced: true, preferences: data });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
