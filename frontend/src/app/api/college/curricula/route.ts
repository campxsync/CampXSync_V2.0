import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query, withTransaction } from '@/lib/db';

const CreateCurriculumSchema = z.object({
  programId: z.string().uuid('Valid program ID is required'),
  code: z
    .string()
    .min(2)
    .max(20)
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase letters, numbers, underscores, or hyphens'),
  name: z.string().min(2).max(160),
  regulationYear: z.number().int().min(1990).max(2100).optional(),
  effectiveFrom: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Effective date is required'),
});

function forbidden() {
  return NextResponse.json(
    {
      type: 'https://campxsync.edu/errors/forbidden',
      title: 'Forbidden',
      status: 403,
      detail: 'Only college administrators can maintain a curriculum.',
    },
    { status: 403 }
  );
}

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);
    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return forbidden();
    }

    const tenantId = resolution.session.tenantId;
    const userId = resolution.session.userId;
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
    const parsed = CreateCurriculumSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parsed.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { programId, code, name, regulationYear, effectiveFrom } = parsed.data;
    const programRes = await query(
      `SELECT id, college_id
       FROM acd.programs
       WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
       LIMIT 1`,
      [programId, tenantId]
    );
    const program = programRes.rows[0];
    if (!program) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Program Not Found',
          status: 404,
          detail: 'The selected program does not exist in this institute.',
        },
        { status: 404 }
      );
    }
    if (crossesCollege(resolution.session.assignedCollegeId, program.college_id)) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'That program belongs to another college.',
        },
        { status: 403 }
      );
    }

    const created = await withTransaction(async (client) => {
      const curriculumRes = await client.query(
        `INSERT INTO acd.curricula (
           tenant_id, program_id, code, name, regulation_year, status, created_by, updated_by
         )
         VALUES ($1, $2, $3, $4, $5, 'DRAFT', $6, $6)
         RETURNING id, code, name, regulation_year, status`,
        [tenantId, programId, code, name, regulationYear ?? null, userId]
      );
      const curriculum = curriculumRes.rows[0];
      const versionRes = await client.query(
        `INSERT INTO acd.curriculum_versions (
           tenant_id, curriculum_id, version_no, effective_from, status, created_by, updated_by
         )
         VALUES ($1, $2, 1, $3, 'DRAFT', $4, $4)
         RETURNING id, version_no, status, effective_from`,
        [tenantId, curriculum.id, effectiveFrom, userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'CURRICULUM_CREATED', 'COLLEGE_ADMIN', $3, 'CURRICULUM', $4, 'SUCCESS', 'INFO', $5)`,
        [
          tenantId,
          program.college_id,
          userId,
          curriculum.id,
          JSON.stringify({ code, name, programId, effectiveFrom }),
        ]
      );
      return { curriculum, version: versionRes.rows[0] };
    });

    return NextResponse.json(
      {
        success: true,
        message: `Curriculum '${name}' (${code}) opened as draft version 1.`,
        ...created,
      },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Curriculum Code',
          status: 409,
          detail: 'A curriculum with that code already exists in this institute.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/curricula error:', err);
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
