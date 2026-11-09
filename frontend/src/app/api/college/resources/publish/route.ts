import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { withTransaction } from '@/lib/db';

const PublishSchema = z.object({
  resourceId: z.string().uuid(),
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
          detail: 'Only college administrators can publish a reading.',
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
          detail: 'A valid reading is required.',
        },
        { status: 400 }
      );
    }

    const published = await withTransaction(async (client) => {
      const resourceRes = await client.query(
        `SELECT id, status, college_id, external_url
         FROM lms.resources
         WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         FOR UPDATE`,
        [parsed.data.resourceId, tenantId]
      );
      const resource = resourceRes.rows[0];
      if (!resource) return { error: 'not_found' as const };
      if (crossesCollege(resolution.session.assignedCollegeId, resource.college_id)) {
        return { error: 'other_college' as const };
      }
      if (resource.status !== 'DRAFT') return { error: 'locked' as const };
      if (!resource.external_url) return { error: 'no_link' as const };

      const updated = await client.query(
        `UPDATE lms.resources
         SET status = 'PUBLISHED', updated_by = $3, updated_at = now(), row_version = row_version + 1
         WHERE id = $1 AND tenant_id = $2
         RETURNING id, status`,
        [resource.id, tenantId, userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'READING_PUBLISHED', 'COLLEGE_ADMIN', $3, 'LEARNING_RESOURCE', $4, 'SUCCESS', 'INFO', '{}')`,
        [tenantId, resource.college_id, userId, resource.id]
      );
      return { resource: updated.rows[0] };
    });

    if ('error' in published && published.error) {
      const errors: Record<string, { title: string; status: number; detail: string }> = {
        not_found: { title: 'Reading Not Found', status: 404, detail: 'That reading does not exist in this institute.' },
        other_college: { title: 'Forbidden', status: 403, detail: 'That reading belongs to another college.' },
        locked: { title: 'Reading Locked', status: 409, detail: 'Only a draft reading can be published.' },
        no_link: { title: 'Missing Link', status: 400, detail: 'A reading needs an address before it can be published.' },
      };
      const match = errors[published.error];
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/resource', title: match.title, status: match.status, detail: match.detail },
        { status: match.status }
      );
    }

    return NextResponse.json({
      success: true,
      message: 'Reading published.',
      resource: published.resource,
    });
  } catch (err: any) {
    console.error('POST /api/college/resources/publish error:', err);
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
