import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  subjectId: z.string().uuid(),
  employeeId: z.string().uuid(),
  title: z.string().min(2).max(160),
  plannedHours: z.number().int().min(1).max(200),
  plannedDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a lesson plan.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const subject = await query(`SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.subjectId, auth.tenantId]);
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');
    const employee = await query(
      `SELECT id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         AND ($3::uuid IS NULL OR college_id = $3)`,
      [data.employeeId, auth.tenantId, auth.collegeId]
    );
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');

    const created = await withTransaction(async (client) => {
      const plan = await client.query(
        `INSERT INTO lms.lesson_plans (tenant_id, subject_id, employee_id, title, planned_hours, status, created_by)
         VALUES ($1, $2, $3, $4, $5, 'DRAFT', $6)
         RETURNING id, title, status`,
        [auth.tenantId, data.subjectId, data.employeeId, data.title, data.plannedHours, auth.userId]
      );
      await client.query(
        `INSERT INTO lms.lesson_sessions (tenant_id, plan_id, planned_date, status, created_by)
         VALUES ($1, $2, $3, 'PLANNED', $4)`,
        [auth.tenantId, plan.rows[0].id, data.plannedDate, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         ) VALUES ($1, $2, now(), 'LESSON_PLAN_RECORDED', 'COLLEGE_ADMIN', $3, 'LESSON_PLAN', $4, 'SUCCESS', 'INFO', '{}')`,
        [auth.tenantId, auth.collegeId, auth.userId, plan.rows[0].id]
      );
      return plan.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Lesson plan recorded as a draft.', plan: created }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/learning/lessons', err);
    return problem(500, 'Server Error', 'The lesson plan could not be recorded.');
  }
}
