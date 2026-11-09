import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { withTransaction } from '@/lib/db';

const PublishSchema = z.object({
  versionId: z.string().uuid('Valid curriculum version is required'),
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
          detail: 'Only college administrators can publish a curriculum.',
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

    const parsed = PublishSchema.safeParse(await req.json());
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

    const { versionId } = parsed.data;
    const published = await withTransaction(async (client) => {
      const versionRes = await client.query(
        `SELECT v.id, v.status, v.curriculum_id, v.effective_from, p.college_id
         FROM acd.curriculum_versions v
         JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id AND c.deleted_at IS NULL
         JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
         WHERE v.id = $1 AND v.tenant_id = $2 AND v.deleted_at IS NULL
         FOR UPDATE OF v`,
        [versionId, tenantId]
      );
      const version = versionRes.rows[0];
      if (!version) {
        return { error: 'not_found' as const };
      }
      if (crossesCollege(resolution.session.assignedCollegeId, version.college_id)) {
        return { error: 'other_college' as const };
      }
      if (version.status !== 'DRAFT') {
        return { error: 'locked' as const };
      }

      const creditRes = await client.query(
        `SELECT count(*)::int AS subject_count, coalesce(sum(credits), 0) AS total_credits
         FROM acd.curriculum_subjects
         WHERE tenant_id = $1 AND curriculum_version_id = $2 AND deleted_at IS NULL`,
        [tenantId, versionId]
      );
      const subjectCount = creditRes.rows[0].subject_count as number;
      const totalCredits = creditRes.rows[0].total_credits;
      if (subjectCount < 1) {
        return { error: 'empty' as const };
      }

      await client.query(
        `UPDATE acd.curriculum_versions
         SET status = 'SUPERSEDED',
             effective_to = GREATEST(effective_from, $3::date - 1),
             updated_by = $4,
             updated_at = now(),
             row_version = row_version + 1
         WHERE tenant_id = $1
           AND curriculum_id = $2
           AND status = 'ACTIVE'
           AND deleted_at IS NULL
           AND id <> $5`,
        [tenantId, version.curriculum_id, version.effective_from, userId, versionId]
      );

      const activated = await client.query(
        `UPDATE acd.curriculum_versions
         SET status = 'ACTIVE',
             total_credits = $3,
             updated_by = $4,
             updated_at = now(),
             row_version = row_version + 1
         WHERE id = $1 AND tenant_id = $2
         RETURNING id, version_no, status, total_credits, effective_from`,
        [versionId, tenantId, totalCredits, userId]
      );

      await client.query(
        `UPDATE acd.curricula
         SET status = 'ACTIVE',
             updated_by = $3,
             updated_at = now(),
             row_version = row_version + 1
         WHERE id = $1 AND tenant_id = $2`,
        [version.curriculum_id, tenantId, userId]
      );

      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'CURRICULUM_PUBLISHED', 'COLLEGE_ADMIN', $3, 'CURRICULUM_VERSION', $4, 'SUCCESS', 'INFO', $5)`,
        [
          tenantId,
          version.college_id,
          userId,
          versionId,
          JSON.stringify({ subjectCount, totalCredits }),
        ]
      );

      return { version: activated.rows[0], subjectCount };
    });

    if ('error' in published) {
      if (published.error === 'not_found') {
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
      if (published.error === 'other_college') {
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
      if (published.error === 'locked') {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/conflict',
            title: 'Version Locked',
            status: 409,
            detail: 'Only a draft version can be published.',
          },
          { status: 409 }
        );
      }
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Empty Curriculum',
          status: 400,
          detail: 'Place at least one subject on the draft before publishing it.',
        },
        { status: 400 }
      );
    }

    return NextResponse.json({
      success: true,
      message: `Curriculum published with ${published.subjectCount} subjects.`,
      version: published.version,
    });
  } catch (err: any) {
    console.error('POST /api/college/curricula/publish error:', err);
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
