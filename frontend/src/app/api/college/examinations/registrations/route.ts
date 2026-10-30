import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  examinationId: z.string().uuid(),
  studentId: z.string().uuid(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can register a student for an examination.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const exam = await query(
      `SELECT id, status, college_id FROM exm.examinations WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [data.examinationId, auth.tenantId]
    );
    if (!exam.rows[0]) return problem(404, 'Not Found', 'That examination is not in this institute.');
    if (exam.rows[0].status !== 'REGISTRATION_OPEN') return problem(409, 'Conflict', 'Registration is not open for this examination.');
    const student = await query(
      `SELECT id, enrollment_no FROM stu.students WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR college_id = $3)`,
      [data.studentId, auth.tenantId, auth.collegeId]
    );
    if (!student.rows[0]) return problem(404, 'Not Found', 'That student is not in this institute.');

    const fees = await query(
      `SELECT coalesce(sum(net_amount), 0) AS net, coalesce(sum(paid_amount), 0) AS paid
       FROM fin.fee_obligations
       WHERE tenant_id = $1 AND student_id = $2 AND deleted_at IS NULL AND status NOT IN ('CANCELLED', 'WAIVED')`,
      [auth.tenantId, data.studentId]
    );
    const net = Number(fees.rows[0].net);
    const paid = Number(fees.rows[0].paid);
    const result = net > 0 && paid < net ? 'CONDITIONAL' : 'ELIGIBLE';

    const created = await withTransaction(async (client) => {
      const stamp = Date.now().toString().slice(-8);
      const registration = await client.query(
        `INSERT INTO exm.registrations (tenant_id, examination_id, student_id, status, registration_no, created_by)
         VALUES ($1, $2, $3, 'DRAFT', $4, $5)
         RETURNING id, registration_no, status`,
        [auth.tenantId, data.examinationId, data.studentId, `REG-${stamp}`, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.eligibility_snapshots (tenant_id, registration_id, rule_ref, result, detail, created_by)
         VALUES ($1, $2, 'FEE_BALANCE', $3, $4, $5)`,
        [auth.tenantId, registration.rows[0].id, result, JSON.stringify({ net, paid }), auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAM_REGISTRATION_RECORDED', 'COLLEGE_ADMIN', $3, 'EXAM_REGISTRATION', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, exam.rows[0].college_id, auth.userId, registration.rows[0].id, JSON.stringify({ result })]
      );
      return { ...registration.rows[0], eligibility: result };
    });
    return NextResponse.json({
      success: true,
      message: created.eligibility === 'CONDITIONAL'
        ? 'Registration saved as a draft. The fee balance makes eligibility conditional.'
        : 'Registration saved as a draft. The fee record shows the student as eligible.',
      registration: created,
    }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That student is already registered for this examination.');
    console.error('POST /api/college/examinations/registrations', err);
    return problem(500, 'Server Error', 'The registration could not be saved.');
  }
}
