import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const OnboardTenantSchema = z.object({
  tenantName: z.string().min(3, 'Tenant name must be at least 3 characters').max(100),
  tenantCode: z
    .string()
    .min(2, 'Code must be at least 2 characters')
    .max(30, 'Code must be at most 30 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
  collegeName: z.string().min(3, 'College name must be at least 3 characters').max(100),
  collegeCode: z
    .string()
    .min(2, 'Code must be at least 2 characters')
    .max(30, 'Code must be at most 30 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
  adminName: z.string().min(2, 'Admin full name is required'),
  adminEmail: z.string().email('Valid administrator email is required'),
  adminPhone: z.string().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    // 1. Enforce Platform Admin authorization on the server
    if (!resolution.success || resolution.session.role !== 'platform_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only platform administrators are authorized to onboard new tenants.',
        },
        { status: 403 }
      );
    }

    // 2. Validate request payload
    const body = await req.json();
    const parseResult = OnboardTenantSchema.safeParse(body);

    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Error',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { tenantName, tenantCode, collegeName, collegeCode } = parseResult.data;

    // 3. Check for tenant code duplicate
    const existingRes = await query(`
      SELECT id, code FROM core.tenants WHERE code = $1 AND deleted_at IS NULL LIMIT 1
    `, [tenantCode]);

    if (existingRes.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Tenant Code Conflict',
          status: 409,
          detail: `Tenant code '${tenantCode}' is already registered on the platform.`,
        },
        { status: 409 }
      );
    }

    // 4. Create tenant in core.tenants
    const tenantRes = await query(`
      INSERT INTO core.tenants (name, code, status)
      VALUES ($1, $2, 'ACTIVE')
      RETURNING id, name, code, status, created_at
    `, [tenantName, tenantCode]);

    const newTenant = tenantRes.rows[0];

    // Seed system roles for new tenant
    await query(`SELECT iam.seed_system_roles($1)`, [newTenant.id]);

    // 5. Create primary college in core.colleges
    const collegeRes = await query(`
      INSERT INTO core.colleges (tenant_id, name, code, status, provisioning_status)
      VALUES ($1, $2, $3, 'ACTIVE', 'ACTIVE')
      RETURNING id, name, code, status
    `, [newTenant.id, collegeName, collegeCode]);

    const newCollege = collegeRes.rows[0];

    return NextResponse.json(
      {
        success: true,
        message: `Institutional tenant '${tenantName}' (${tenantCode}) onboarded successfully.`,
        tenant: newTenant,
        college: newCollege || null,
        collegeError: null,
      },
      { status: 201 }
    );
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred during institutional onboarding.',
      },
      { status: 500 }
    );
  }
}
