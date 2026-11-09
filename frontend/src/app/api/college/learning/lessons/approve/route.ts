import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../guard';

const Schema = z.object({ planId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can approve a lesson plan.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'A lesson plan is required.');
    const current = await query(
      `SELECT id, status FROM lms.lesson_plans WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.planId, auth.tenantId]
    );
    const plan = current.rows[0];
    if (!plan) return problem(404, 'Not Found', 'That lesson plan is not in this institute.');
    if (plan.status !== 'DRAFT' && plan.status !== 'SUBMITTED') {
      return problem(409, 'Conflict', 'Only a draft lesson plan can be approved.');
    }
    await query(
      `UPDATE lms.lesson_plans SET status = 'APPROVED', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2`,
      [plan.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'LESSON_PLAN_APPROVED', 'COLLEGE_ADMIN', $3, 'LESSON_PLAN', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, plan.id]
    );
    return NextResponse.json({ success: true, message: 'Lesson plan approved.' });
  } catch (err) {
    console.error('POST /api/college/learning/lessons/approve', err);
    return problem(500, 'Server Error', 'The lesson plan could not be approved.');
  }
}
