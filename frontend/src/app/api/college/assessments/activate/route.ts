import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { withTransaction } from '@/lib/db';

const ActivateSchema = z.object({
  structureId: z.string().uuid(),
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
          detail: 'Only college administrators can activate an assessment scheme.',
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

    const parsed = ActivateSchema.safeParse(await req.json());
    if (!parsed.success) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/validation-failed', title: 'Validation Failed', status: 400, detail: 'A valid scheme is required.' },
        { status: 400 }
      );
    }

    const activated = await withTransaction(async (client) => {
      const structureRes = await client.query(
        `SELECT s.id, s.status, s.total_marks, p.college_id
         FROM acd.assessment_structures s
         LEFT JOIN acd.curriculum_versions v ON v.id = s.curriculum_version_id AND v.tenant_id = s.tenant_id
         LEFT JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id
         LEFT JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
         WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
         FOR UPDATE OF s`,
        [parsed.data.structureId, tenantId]
      );
      const structure = structureRes.rows[0];
      if (!structure) return { error: 'not_found' as const };
      if (crossesCollege(resolution.session.assignedCollegeId, structure.college_id)) {
        return { error: 'other_college' as const };
      }
      if (structure.status !== 'DRAFT') return { error: 'locked' as const };

      const totals = await client.query(
        `SELECT count(*)::int AS component_count, coalesce(sum(max_marks), 0) AS marks
         FROM acd.assessment_components
         WHERE tenant_id = $1 AND structure_id = $2 AND deleted_at IS NULL`,
        [tenantId, structure.id]
      );
      if ((totals.rows[0].component_count as number) < 1) return { error: 'empty' as const };
      if (Number(totals.rows[0].marks) !== Number(structure.total_marks)) return { error: 'marks' as const };

      const updated = await client.query(
        `UPDATE acd.assessment_structures
         SET status = 'ACTIVE', updated_by = $3, updated_at = now(), row_version = row_version + 1
         WHERE id = $1 AND tenant_id = $2
         RETURNING id, status`,
        [structure.id, tenantId, userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'ASSESSMENT_SCHEME_ACTIVATED', 'COLLEGE_ADMIN', $3, 'ASSESSMENT_STRUCTURE', $4, 'SUCCESS', 'INFO', $5)`,
        [tenantId, structure.college_id, userId, structure.id, JSON.stringify({ componentCount: totals.rows[0].component_count })]
      );
      return { structure: updated.rows[0] };
    });

    if ('error' in activated && activated.error) {
      const errors: Record<string, { title: string; status: number; detail: string }> = {
        not_found: { title: 'Scheme Not Found', status: 404, detail: 'That assessment scheme does not exist in this institute.' },
        other_college: { title: 'Forbidden', status: 403, detail: 'That scheme belongs to another college.' },
        locked: { title: 'Scheme Locked', status: 409, detail: 'Only a draft scheme can be activated.' },
        empty: { title: 'Empty Scheme', status: 400, detail: 'Add at least one component before activating the scheme.' },
        marks: { title: 'Marks Do Not Match', status: 400, detail: 'The component marks must add up to the scheme total before it can be activated.' },
      };
      const match = errors[activated.error];
      if (!match) {
        return NextResponse.json(
          { type: 'https://campxsync.edu/errors/assessment', title: 'Server Error', status: 500, detail: 'The scheme could not be activated.' },
          { status: 500 }
        );
      }
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/assessment', title: match.title, status: match.status, detail: match.detail },
        { status: match.status }
      );
    }

    return NextResponse.json({
      success: true,
      message: 'Assessment scheme activated. Components are now fixed.',
      structure: activated.structure,
    });
  } catch (err: any) {
    console.error('POST /api/college/assessments/activate error:', err);
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
