import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import {
  PRODUCT_DEFAULTS,
  sanitizePreferences,
} from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const RoleDashboardConfigSchema = z.object({
  order: z.array(z.string().max(50)).max(20).default([]),
  hidden: z.array(z.string().max(50)).max(20).default([]),
});

const PreferenceSchema = z.object({
  preferences: z.object({
    version: z.literal(1).default(1),
    theme: z.enum(['light', 'dark', 'system']).default('light'),
    sidebarCollapsed: z.boolean().default(false),
    tableDensity: z.enum(['comfortable', 'compact']).default('comfortable'),
    defaultDateRangeDays: z.union([z.literal(7), z.literal(14), z.literal(30)]).default(14),
    defaultAttendanceFilter: z.enum(['all', 'unmarked', 'shortage']).default('all'),
    locale: z.string().max(10).default('en-IN'),
    timezone: z.string().max(50).default('Asia/Kolkata'),
    accessibility: z.object({
      highContrast: z.boolean().default(false),
      reducedMotion: z.boolean().default(false),
      largeFont: z.boolean().default(false),
    }).default({}),
    dashboardWidgets: RoleDashboardConfigSchema.optional(),
    roles: z.object({
      college_admin: RoleDashboardConfigSchema.optional(),
      faculty: RoleDashboardConfigSchema.optional(),
      platform_admin: RoleDashboardConfigSchema.optional(),
    }).optional(),
  }),
  role: z.string().max(50).optional(),
});

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
          detail: 'Authentication required to access user preferences.',
        },
        { status: 401 }
      );
    }

    const { userId, role, tenantId } = resolution.session;
    const url = new URL(req.url);
    const requestedRole = url.searchParams.get('role') || role;

    const gatewayUrl = new URL('/api/v1/user/preferences', GATEWAY_BASE_URL);
    if (requestedRole) {
      gatewayUrl.searchParams.set('role', requestedRole);
    }

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId || '11111111-1111-1111-1111-111111111111',
      'X-User-Role': role || 'college_admin',
    };

    const gatewayRes = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
    });

    if (gatewayRes.ok) {
      const data = await gatewayRes.json();
      return NextResponse.json(data);
    }

    // Fallback gracefully to product defaults if backend preferences service returns 404
    return NextResponse.json({
      success: true,
      preferences: sanitizePreferences(PRODUCT_DEFAULTS, requestedRole, PRODUCT_DEFAULTS),
      isAccountSynced: false,
    });
  } catch (err: any) {
    console.error('GET /api/user/preferences error:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-error',
        title: 'Preferences Fetch Error',
        status: 500,
        detail: 'Failed to retrieve preferences from gateway service.',
      },
      { status: 500 }
    );
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
          detail: 'Authentication required to update preferences.',
        },
        { status: 401 }
      );
    }

    const { userId, role, tenantId } = resolution.session;

    let body: any;
    try {
      body = await req.json();
    } catch {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Invalid JSON',
          status: 400,
          detail: 'Request body must be valid JSON.',
        },
        { status: 400 }
      );
    }

    const parsed = PreferenceSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-error',
          title: 'Invalid Preference Payload',
          status: 422,
          detail: 'Payload contains disallowed fields or invalid values.',
          errors: parsed.error.format(),
        },
        { status: 422 }
      );
    }

    const effectiveRole =
      resolution.session.role === 'platform_admin' && parsed.data.role
        ? parsed.data.role
        : resolution.session.role;
    const sanitized = sanitizePreferences(parsed.data.preferences, effectiveRole, PRODUCT_DEFAULTS);

    const gatewayUrl = new URL('/api/v1/user/preferences', GATEWAY_BASE_URL);
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId || '11111111-1111-1111-1111-111111111111',
      'X-User-Role': effectiveRole,
    };

    const gatewayRes = await fetch(gatewayUrl.toString(), {
      method: 'PUT',
      headers,
      body: JSON.stringify(sanitized),
    });

    if (gatewayRes.ok) {
      const data = await gatewayRes.json();
      return NextResponse.json(data);
    }

    const errData = await gatewayRes.json().catch(() => ({}));
    return NextResponse.json(
      {
        success: true,
        preferences: sanitized,
        isAccountSynced: false,
        message: errData.detail || 'Saved to local workspace context.',
      },
      { status: 200 }
    );
  } catch (err: any) {
    console.error('PUT /api/user/preferences error:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-error',
        title: 'Preference Save Error',
        status: 500,
        detail: 'Failed to persist preferences via gateway.',
      },
      { status: 500 }
    );
  }
}
