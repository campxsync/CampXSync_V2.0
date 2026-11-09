import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { withTransaction } from '@/lib/db';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  subjectId: z.string().uuid(),
  title: z.string().min(2).max(160),
  moduleTitle: z.string().min(2).max(160),
  topicTitle: z.string().min(2).max(160),
  contentType: z.enum(['TEXT', 'LINK', 'VIDEO']),
  body: z.string().max(4000).optional(),
  externalUrl: z.string().url().max(500).optional(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record course content.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.contentType === 'LINK' || data.contentType === 'VIDEO') {
      if (!data.externalUrl) return problem(400, 'Validation Failed', 'A link or video needs an http or https address.');
      const protocol = new URL(data.externalUrl).protocol;
      if (protocol !== 'http:' && protocol !== 'https:') return problem(400, 'Validation Failed', 'The address must be http or https.');
    }

    const subject = await query(
      `SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [data.subjectId, auth.tenantId]
    );
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');

    const created = await withTransaction(async (client) => {
      const content = await client.query(
        `INSERT INTO lms.contents (tenant_id, subject_id, title, status, created_by)
         VALUES ($1, $2, $3, 'DRAFT', $4)
         RETURNING id, title, status`,
        [auth.tenantId, data.subjectId, data.title, auth.userId]
      );
      const module = await client.query(
        `INSERT INTO lms.content_modules (tenant_id, content_id, seq, title, created_by)
         VALUES ($1, $2, 1, $3, $4)
         RETURNING id`,
        [auth.tenantId, content.rows[0].id, data.moduleTitle, auth.userId]
      );
      await client.query(
        `INSERT INTO lms.content_topics (tenant_id, module_id, seq, title, content_type, body, external_url, created_by)
         VALUES ($1, $2, 1, $3, $4, $5, $6, $7)`,
        [auth.tenantId, module.rows[0].id, data.topicTitle, data.contentType, data.body || null, data.externalUrl || null, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         ) VALUES ($1, $2, now(), 'CONTENT_RECORDED', 'COLLEGE_ADMIN', $3, 'COURSE_CONTENT', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, auth.collegeId, auth.userId, content.rows[0].id, JSON.stringify({ subjectId: data.subjectId })]
      );
      return content.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Course content recorded as a draft.', content: created }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/learning/content', err);
    return problem(500, 'Server Error', 'The course content could not be recorded.');
  }
}
