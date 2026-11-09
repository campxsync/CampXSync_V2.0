import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query } from '@/lib/db';

const CreateStructureSchema = z.object({
  subjectId: z.string().uuid(),
  curriculumVersionId: z.string().uuid().optional(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  totalMarks: z.number().positive().max(1000),
  passMarks: z.number().min(0).max(1000),
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
          detail: 'Only college administrators can define an assessment scheme.',
        },
        { status: 403 }
      );
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

    const parsed = CreateStructureSchema.safeParse(await req.json());
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

    const { subjectId, curriculumVersionId, code, name, totalMarks, passMarks } = parsed.data;
    if (passMarks > totalMarks) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: 'The pass mark cannot be higher than the total marks.',
        },
        { status: 400 }
      );
    }

    const subjectRes = await query(
      `SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL LIMIT 1`,
      [subjectId, tenantId]
    );
    if (subjectRes.rows.length === 0) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/not-found', title: 'Subject Not Found', status: 404, detail: 'That subject is not in this institute.' },
        { status: 404 }
      );
    }

    let collegeId: string | null = null;
    if (curriculumVersionId) {
      const versionRes = await query(
        `SELECT v.id, p.college_id
         FROM acd.curriculum_versions v
         JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id AND c.deleted_at IS NULL
         JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
         JOIN acd.curriculum_subjects cs
           ON cs.curriculum_version_id = v.id AND cs.tenant_id = v.tenant_id AND cs.deleted_at IS NULL
         WHERE v.id = $1 AND v.tenant_id = $2 AND v.deleted_at IS NULL AND cs.subject_id = $3
         LIMIT 1`,
        [curriculumVersionId, tenantId, subjectId]
      );
      if (versionRes.rows.length === 0) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/not-found',
            title: 'Curriculum Subject Not Found',
            status: 404,
            detail: 'That subject is not on the selected curriculum version.',
          },
          { status: 404 }
        );
      }
      collegeId = versionRes.rows[0].college_id;
      if (crossesCollege(resolution.session.assignedCollegeId, collegeId)) {
        return NextResponse.json(
          { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'That curriculum belongs to another college.' },
          { status: 403 }
        );
      }
    }

    const inserted = await query(
      `INSERT INTO acd.assessment_structures (
         tenant_id, subject_id, curriculum_version_id, code, name, total_marks, pass_marks, status, created_by, updated_by
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, 'DRAFT', $8, $8)
       RETURNING id, code, name, status`,
      [tenantId, subjectId, curriculumVersionId ?? null, code, name, totalMarks, passMarks, userId]
    );

    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       )
       VALUES ($1, $2, now(), 'ASSESSMENT_SCHEME_CREATED', 'COLLEGE_ADMIN', $3, 'ASSESSMENT_STRUCTURE', $4, 'SUCCESS', 'INFO', $5)`,
      [tenantId, collegeId, userId, inserted.rows[0].id, JSON.stringify({ code, name, subjectId, totalMarks, passMarks })]
    );

    return NextResponse.json(
      { success: true, message: `Assessment scheme '${name}' opened as a draft.`, structure: inserted.rows[0] },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Scheme Code',
          status: 409,
          detail: 'An assessment scheme with that code already exists in this institute.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/assessments error:', err);
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
