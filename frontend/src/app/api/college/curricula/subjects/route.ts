import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query } from '@/lib/db';

const AddSubjectSchema = z.object({
  versionId: z.string().uuid('Valid curriculum version is required'),
  subjectId: z.string().uuid('Valid subject is required'),
  termNo: z.number().int().min(1).max(16),
  category: z.enum(['CORE', 'ELECTIVE', 'OPEN_ELECTIVE', 'MANDATORY_NON_CREDIT', 'PROJECT']),
  credits: z.number().min(0).max(40),
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
          detail: 'Only college administrators can place subjects on a curriculum.',
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

    const parsed = AddSubjectSchema.safeParse(await req.json());
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

    const { versionId, subjectId, termNo, category, credits } = parsed.data;
    const versionRes = await query(
      `SELECT v.id, v.status, p.college_id
       FROM acd.curriculum_versions v
       JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id AND c.deleted_at IS NULL
       JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
       WHERE v.id = $1 AND v.tenant_id = $2 AND v.deleted_at IS NULL
       LIMIT 1`,
      [versionId, tenantId]
    );
    const version = versionRes.rows[0];
    if (!version) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Version Not Found',
          status: 404,
          detail: 'That curriculum version does not exist in this institute.',
        },
        { status: 404 }
      );
    }
    if (crossesCollege(resolution.session.assignedCollegeId, version.college_id)) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'That curriculum belongs to another college.',
        },
        { status: 403 }
      );
    }
    if (version.status !== 'DRAFT') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Version Locked',
          status: 409,
          detail: 'A published curriculum version cannot be changed.',
        },
        { status: 409 }
      );
    }

    const subjectRes = await query(
      `SELECT id FROM acd.subjects
       WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
       LIMIT 1`,
      [subjectId, tenantId]
    );
    if (subjectRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Subject Not Found',
          status: 404,
          detail: 'That subject is not in this institute.',
        },
        { status: 404 }
      );
    }

    const inserted = await query(
      `INSERT INTO acd.curriculum_subjects (
         tenant_id, curriculum_version_id, subject_id, term_no, category, credits, created_by, updated_by
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $7)
       RETURNING id, term_no, category, credits`,
      [tenantId, versionId, subjectId, termNo, category, credits, userId]
    );

    return NextResponse.json(
      { success: true, subject: inserted.rows[0] },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Subject Already Placed',
          status: 409,
          detail: 'That subject is already on this curriculum version.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/curricula/subjects error:', err);
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
