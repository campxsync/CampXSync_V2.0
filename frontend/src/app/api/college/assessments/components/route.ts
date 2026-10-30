import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query, withTransaction } from '@/lib/db';

const AddComponentSchema = z.object({
  structureId: z.string().uuid(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  componentType: z.enum(['INTERNAL_TEST', 'ASSIGNMENT', 'LAB', 'VIVA', 'PROJECT', 'ATTENDANCE', 'END_SEMESTER', 'OTHER']),
  maxMarks: z.number().min(0).max(1000),
  minMarks: z.number().min(0).max(1000).optional(),
  weightagePct: z.number().min(0).max(100).optional(),
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
          detail: 'Only college administrators can add an assessment component.',
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

    const parsed = AddComponentSchema.safeParse(await req.json());
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

    const { structureId, code, name, componentType, maxMarks, minMarks, weightagePct } = parsed.data;
    if (minMarks !== undefined && minMarks > maxMarks) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: 'The minimum mark cannot be higher than the maximum mark.',
        },
        { status: 400 }
      );
    }

    const inserted = await withTransaction(async (client) => {
      const structureRes = await client.query(
        `SELECT s.id, s.status, s.total_marks, p.college_id
         FROM acd.assessment_structures s
         LEFT JOIN acd.curriculum_versions v ON v.id = s.curriculum_version_id AND v.tenant_id = s.tenant_id
         LEFT JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id
         LEFT JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
         WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
         FOR UPDATE OF s`,
        [structureId, tenantId]
      );
      const structure = structureRes.rows[0];
      if (!structure) return { error: 'not_found' as const };
      if (crossesCollege(resolution.session.assignedCollegeId, structure.college_id)) {
        return { error: 'other_college' as const };
      }
      if (structure.status !== 'DRAFT') return { error: 'locked' as const };

      const totals = await client.query(
        `SELECT coalesce(sum(max_marks), 0) AS marks, coalesce(sum(weightage_pct), 0) AS weight
         FROM acd.assessment_components
         WHERE tenant_id = $1 AND structure_id = $2 AND deleted_at IS NULL`,
        [tenantId, structureId]
      );
      const nextMarks = Number(totals.rows[0].marks) + maxMarks;
      const nextWeight = Number(totals.rows[0].weight) + (weightagePct ?? 0);
      if (nextMarks > Number(structure.total_marks)) return { error: 'marks' as const };
      if (nextWeight > 100) return { error: 'weight' as const };

      const sequenceRes = await client.query(
        `SELECT coalesce(max(sequence), 0) + 1 AS next_sequence
         FROM acd.assessment_components
         WHERE tenant_id = $1 AND structure_id = $2 AND deleted_at IS NULL`,
        [tenantId, structureId]
      );
      const componentRes = await client.query(
        `INSERT INTO acd.assessment_components (
           tenant_id, structure_id, code, name, component_type, is_external, max_marks, min_marks, weightage_pct, sequence, created_by, updated_by
         )
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $11)
         RETURNING id, code, name`,
        [
          tenantId,
          structureId,
          code,
          name,
          componentType,
          componentType === 'END_SEMESTER',
          maxMarks,
          minMarks ?? null,
          weightagePct ?? null,
          sequenceRes.rows[0].next_sequence,
          userId,
        ]
      );
      return { component: componentRes.rows[0] };
    });

    if ('error' in inserted && inserted.error) {
      const errors: Record<string, { title: string; status: number; detail: string }> = {
        not_found: { title: 'Scheme Not Found', status: 404, detail: 'That assessment scheme does not exist in this institute.' },
        other_college: { title: 'Forbidden', status: 403, detail: 'That scheme belongs to another college.' },
        locked: { title: 'Scheme Locked', status: 409, detail: 'Components can be added only while the scheme is a draft.' },
        marks: { title: 'Marks Exceed Total', status: 400, detail: 'The component marks would go past the scheme total.' },
        weight: { title: 'Weight Exceeds 100', status: 400, detail: 'The component weights cannot add up to more than 100 percent.' },
      };
      const match = errors[inserted.error];
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/assessment', title: match.title, status: match.status, detail: match.detail },
        { status: match.status }
      );
    }

    return NextResponse.json(
      { success: true, message: `Component '${name}' added.`, component: inserted.component },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Component Code',
          status: 409,
          detail: 'That component code is already on this scheme.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/assessments/components error:', err);
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
