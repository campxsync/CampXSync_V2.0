import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import {
  PRODUCT_DEFAULTS,
  ROLE_DEFAULT_WIDGETS,
  sanitizePreferences,
  UserPreferences,
} from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

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

    const { userId, role } = resolution.session;
    const url = new URL(req.url);
    const requestedRole = url.searchParams.get('role') || role;

    // Load preferences from PostgreSQL iam.user_profiles
    const profileRes = await query(
      `SELECT preferences FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [userId]
    );

    if (profileRes.rows.length === 0 || !profileRes.rows[0].preferences) {
      // User has no saved preferences row; return product defaults
      return NextResponse.json({
        success: true,
        preferences: sanitizePreferences(PRODUCT_DEFAULTS, requestedRole, PRODUCT_DEFAULTS),
        isAccountSynced: profileRes.rows.length > 0,
      });
    }

    const dbPrefs = profileRes.rows[0].preferences;
    const effective = sanitizePreferences(dbPrefs, requestedRole, PRODUCT_DEFAULTS);

    return NextResponse.json({
      success: true,
      preferences: effective,
      isAccountSynced: true,
    });
  } catch (err: any) {
    console.error('GET /api/user/preferences error:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-error',
        title: 'Preferences Fetch Error',
        status: 500,
        detail: 'Failed to retrieve preferences from database.',
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

    const effectiveRole = parsed.data.role || role;
    const sanitized = sanitizePreferences(parsed.data.preferences, effectiveRole, PRODUCT_DEFAULTS);

    // Fetch existing preferences to perform deep merge of role settings
    const currentRes = await query(
      `SELECT preferences, row_version FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [userId]
    );

    if (currentRes.rows.length === 0) {
      // User has no persistent user_profile (e.g. pure platform service identity)
      return NextResponse.json({
        success: true,
        preferences: sanitized,
        isAccountSynced: false,
        message: 'Saved to local workspace context. No persistent user_profile record found for account sync.',
      });
    }

    const existingPrefs = currentRes.rows[0].preferences || {};
    const existingRoles = existingPrefs.roles || {};

    // Ensure role-scoped widget configs are preserved without clobbering each other
    const mergedRoles = {
      ...existingRoles,
      ...sanitized.roles,
    };

    const finalPrefsToStore = {
      ...existingPrefs,
      ...sanitized,
      roles: mergedRoles,
    };

    // Update PostgreSQL iam.user_profiles.preferences
    const updateRes = await query(
      `
      UPDATE iam.user_profiles
      SET 
        preferences = $1,
        row_version = row_version + 1,
        updated_at = NOW()
      WHERE id = $2 AND deleted_at IS NULL
      RETURNING id, row_version, preferences
      `,
      [JSON.stringify(finalPrefsToStore), userId]
    );

    if (updateRes.rows.length === 0) {
      return NextResponse.json({
        success: true,
        preferences: sanitized,
        isAccountSynced: false,
        message: 'Update touched 0 rows; local fallback active.',
      });
    }

    return NextResponse.json({
      success: true,
      preferences: sanitizePreferences(updateRes.rows[0].preferences, effectiveRole, PRODUCT_DEFAULTS),
      isAccountSynced: true,
      message: 'Preferences successfully synced to institutional profile in PostgreSQL.',
    });
  } catch (err: any) {
    console.error('PUT /api/user/preferences error:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-error',
        title: 'Preference Save Error',
        status: 500,
        detail: 'Failed to persist preferences to database.',
      },
      { status: 500 }
    );
  }
}
