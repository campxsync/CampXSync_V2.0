import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query, withTransaction } from '@/lib/db';

const CreateResourceSchema = z.object({
  collegeId: z.string().uuid(),
  title: z.string().min(2).max(200),
  resourceType: z.enum(['FILE', 'VIDEO', 'LINK', 'EMBED', 'BOOK', 'PAPER', 'DATASET', 'SLIDES']),
  externalUrl: z.string().url().max(500),
  author: z.string().max(160).optional(),
  subjectId: z.string().uuid(),
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
          detail: 'Only college administrators can record a reading.',
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

    const parsed = CreateResourceSchema.safeParse(await req.json());
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

    const { collegeId, title, resourceType, externalUrl, author, subjectId } = parsed.data;
    const protocol = new URL(externalUrl).protocol;
    if (protocol !== 'https:' && protocol !== 'http:') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: 'The reading address must be an http or https link.',
        },
        { status: 400 }
      );
    }
    if (crossesCollege(resolution.session.assignedCollegeId, collegeId)) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'That college is outside this office.',
        },
        { status: 403 }
      );
    }

    const collegeRes = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL LIMIT 1`,
      [collegeId, tenantId]
    );
    if (collegeRes.rows.length === 0) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/not-found', title: 'College Not Found', status: 404, detail: 'That college is not in this institute.' },
        { status: 404 }
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

    const created = await withTransaction(async (client) => {
      const resourceRes = await client.query(
        `INSERT INTO lms.resources (
           tenant_id, college_id, title, resource_type, external_url, author, status, created_by, updated_by
         )
         VALUES ($1, $2, $3, $4, $5, $6, 'DRAFT', $7, $7)
         RETURNING id, title, status`,
        [tenantId, collegeId, title, resourceType, externalUrl, author?.trim() || null, userId]
      );
      const resource = resourceRes.rows[0];
      await client.query(
        `INSERT INTO lms.resource_associations (
           tenant_id, resource_id, entity_type, entity_id, relation, created_by
         )
         VALUES ($1, $2, 'SUBJECT', $3, 'REFERENCE', $4)`,
        [tenantId, resource.id, subjectId, userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'READING_RECORDED', 'COLLEGE_ADMIN', $3, 'LEARNING_RESOURCE', $4, 'SUCCESS', 'INFO', $5)`,
        [tenantId, collegeId, userId, resource.id, JSON.stringify({ title, resourceType, subjectId })]
      );
      return resource;
    });

    return NextResponse.json(
      { success: true, message: `'${title}' is recorded as a draft reading.`, resource: created },
      { status: 201 }
    );
  } catch (err: any) {
    console.error('POST /api/college/resources error:', err);
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
