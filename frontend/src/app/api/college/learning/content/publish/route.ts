import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../guard';

const Schema = z.object({ contentId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can publish course content.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'Content is required.');

    const current = await query(
      `SELECT c.id, c.status,
              (SELECT count(*) FROM lms.content_topics t
               JOIN lms.content_modules m ON m.id = t.module_id AND m.tenant_id = t.tenant_id AND m.deleted_at IS NULL
               WHERE m.content_id = c.id AND t.tenant_id = c.tenant_id AND t.deleted_at IS NULL) AS topics
       FROM lms.contents c
       WHERE c.id = $1 AND c.tenant_id = $2 AND c.deleted_at IS NULL`,
      [parsed.data.contentId, auth.tenantId]
    );
    const content = current.rows[0];
    if (!content) return problem(404, 'Not Found', 'That content is not in this institute.');
    if (content.status !== 'DRAFT' && content.status !== 'IN_REVIEW') {
      return problem(409, 'Conflict', 'Only draft content can be published.');
    }
    if (Number(content.topics) < 1) return problem(409, 'Conflict', 'Add a topic before publishing.');

    await query(
      `UPDATE lms.contents SET status = 'PUBLISHED', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2`,
      [content.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'CONTENT_PUBLISHED', 'COLLEGE_ADMIN', $3, 'COURSE_CONTENT', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, content.id]
    );
    return NextResponse.json({ success: true, message: 'Course content published.' });
  } catch (err) {
    console.error('POST /api/college/learning/content/publish', err);
    return problem(500, 'Server Error', 'The course content could not be published.');
  }
}
