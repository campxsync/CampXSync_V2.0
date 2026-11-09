import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  subjectId: z.string().uuid(),
  programId: z.string().uuid(),
  courseCode: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  courseStatement: z.string().min(2).max(400),
  programCode: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  programStatement: z.string().min(2).max(400),
  correlationLevel: z.number().int().min(1).max(3),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record outcomes.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const subject = await query(`SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.subjectId, auth.tenantId]);
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');
    const program = await query(
      `SELECT id FROM acd.programs WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         AND ($3::uuid IS NULL OR college_id = $3)`,
      [data.programId, auth.tenantId, auth.collegeId]
    );
    if (!program.rows[0]) return problem(404, 'Not Found', 'That program is not in this institute.');

    const created = await withTransaction(async (client) => {
      const courseOutcome = await client.query(
        `INSERT INTO obe.outcomes (tenant_id, outcome_type, code, statement, subject_id, bloom_level, status, created_by)
         VALUES ($1, 'CO', $2, $3, $4, 'APPLY', 'ACTIVE', $5)
         RETURNING id, code`,
        [auth.tenantId, data.courseCode, data.courseStatement, data.subjectId, auth.userId]
      );
      const programOutcome = await client.query(
        `INSERT INTO obe.outcomes (tenant_id, outcome_type, code, statement, program_id, status, created_by)
         VALUES ($1, 'PO', $2, $3, $4, 'ACTIVE', $5)
         RETURNING id, code`,
        [auth.tenantId, data.programCode, data.programStatement, data.programId, auth.userId]
      );
      await client.query(
        `INSERT INTO obe.outcome_mappings (tenant_id, from_outcome_id, to_outcome_id, correlation_level, created_by)
         VALUES ($1, $2, $3, $4, $5)`,
        [auth.tenantId, courseOutcome.rows[0].id, programOutcome.rows[0].id, data.correlationLevel, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         ) VALUES ($1, $2, now(), 'OUTCOME_MAPPED', 'COLLEGE_ADMIN', $3, 'OUTCOME', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, auth.collegeId, auth.userId, courseOutcome.rows[0].id, JSON.stringify({ correlationLevel: data.correlationLevel })]
      );
      return { courseOutcome: courseOutcome.rows[0], programOutcome: programOutcome.rows[0] };
    });
    return NextResponse.json({ success: true, message: 'Course and program outcomes recorded. Attainment is not calculated here.', outcomes: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'An outcome with that code already exists.');
    console.error('POST /api/college/learning/outcomes', err);
    return problem(500, 'Server Error', 'The outcomes could not be recorded.');
  }
}
