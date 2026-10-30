import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../../learning/guard';

const Schema = z.object({ resultId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can publish a result.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));

    const row = await query(
      `SELECT res.id, res.status, res.examination_id, e.college_id
       FROM exm.results res JOIN exm.examinations e ON e.id = res.examination_id
       WHERE res.id = $1 AND res.tenant_id = $2 AND res.deleted_at IS NULL`,
      [parsed.data.resultId, auth.tenantId]
    );
    if (!row.rows[0]) return problem(404, 'Not Found', 'That result is not in this institute.');
    if (row.rows[0].status !== 'PROCESSED' && row.rows[0].status !== 'APPROVED') {
      return problem(409, 'Conflict', 'Only a processed result can be published.');
    }

    const published = await withTransaction(async (client) => {
      const result = await client.query(
        `UPDATE exm.results SET status = 'PUBLISHED', published_at = now(), updated_by = $2
         WHERE id = $1 RETURNING id, status`,
        [parsed.data.resultId, auth.userId]
      );
      await client.query(
        `UPDATE exm.examinations SET status = 'RESULTS_PUBLISHED', updated_by = $2 WHERE id = $1`,
        [row.rows[0].examination_id, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAM_RESULT_PUBLISHED', 'COLLEGE_ADMIN', $3, 'EXAM_RESULT', $4, 'SUCCESS', 'INFO', '{}')`,
        [auth.tenantId, row.rows[0].college_id, auth.userId, parsed.data.resultId]
      );
      return result.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Result published. No SGPA was calculated.', result: published });
  } catch (err) {
    console.error('POST /api/college/examinations/results/publish', err);
    return problem(500, 'Server Error', 'The result could not be published.');
  }
}
