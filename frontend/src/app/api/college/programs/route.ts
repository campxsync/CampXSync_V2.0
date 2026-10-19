import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

import { query } from '@/lib/db';

const CreateProgramSchema = z.object({
  departmentId: z.string().uuid('Valid department ID is required'),
  name: z.string().min(2, 'Program name must be at least 2 characters').max(100),
  code: z
    .string()
    .min(2, 'Program code must be at least 2 characters')
    .max(20, 'Program code must be at most 20 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
  level: z.enum(['UG', 'PG', 'DIPLOMA', 'PHD']).default('UG'),
  durationYears: z.number().min(1).max(6).default(4),
  totalTerms: z.number().min(1).max(12).default(8),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can configure academic programs.',
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
    const parseResult = CreateProgramSchema.safeParse(body);
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

    const { departmentId, name, code, level, durationYears, totalTerms } = parseResult.data;

    // Verify department belongs to this tenant
    const deptRes = await query(`
      SELECT id, college_id
      FROM core.departments
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [departmentId, tenantId]);

    const dept = deptRes.rows[0];
    if (!dept) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Department Not Found',
          status: 404,
          detail: 'Selected department does not exist in your institutional tenant.',
        },
        { status: 404 }
      );
    }

    // Insert program strictly in tenant scope
    const progRes = await query(`
      INSERT INTO acd.programs (
        tenant_id, college_id, department_id, name, code, level, duration_years, total_terms, published, status
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, true, 'ACTIVE')
      RETURNING id, name, code, level, duration_years, total_terms, status, created_at
    `, [
      tenantId,
      dept.college_id,
      departmentId,
      name,
      code,
      level,
      durationYears,
      totalTerms,
    ]);

    const prog = progRes.rows[0];
    if (!prog) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/database-error',
          title: 'Program Creation Failed',
          status: 500,
          detail: 'Database error creating program record.',
        },
        { status: 500 }
      );
    }

    return NextResponse.json(
      {
        success: true,
        message: `Academic Program '${name}' (${code}) created successfully.`,
        program: prog,
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
