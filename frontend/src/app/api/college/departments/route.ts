import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateDepartmentSchema = z.object({
  name: z.string().min(2, 'Department name must be at least 2 characters').max(100),
  code: z
    .string()
    .min(2, 'Department code must be at least 2 characters')
    .max(20, 'Department code must be at most 20 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    // 1. Enforce College Admin role
    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can configure academic departments.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
    if (!tenantId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing Tenant Context',
          status: 400,
          detail: 'No active tenant linked to user session.',
        },
        { status: 400 }
      );
    }

    const body = await req.json();
    const parseResult = CreateDepartmentSchema.safeParse(body);
    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { name, code } = parseResult.data;

    // 2. Fetch primary college id for tenant if available
    let collegeId = resolution.session.assignedCollegeId || resolution.session.collegeId;
    if (!collegeId) {
      const colRes = await query(`
        SELECT id FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL LIMIT 1
      `, [tenantId]);
      if (colRes.rows.length > 0) {
        collegeId = colRes.rows[0].id;
      } else {
        const createCol = await query(`
          INSERT INTO core.colleges (tenant_id, code, name, status, provisioning_status)
          VALUES ($1, 'MAIN', 'Main Campus', 'ACTIVE', 'ACTIVE')
          RETURNING id
        `, [tenantId]);
        collegeId = createCol.rows[0]?.id;
      }
    }

    // 3. Insert department strictly within tenant scope
    const deptRes = await query(`
      INSERT INTO core.departments (tenant_id, college_id, name, code, status)
      VALUES ($1, $2, $3, $4, 'ACTIVE')
      RETURNING id, name, code, status, created_at
    `, [tenantId, collegeId, name, code]);

    const dept = deptRes.rows[0];

    if (!dept) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/database-error',
          title: 'Department Creation Failed',
          status: 500,
          detail: 'Database error inserting department record.',
        },
        { status: 500 }
      );
    }

    return NextResponse.json(
      {
        success: true,
        message: `Department '${name}' (${code}) created successfully.`,
        department: dept,
      },
      { status: 201 }
    );
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred.',
      },
      { status: 500 }
    );
  }
}
