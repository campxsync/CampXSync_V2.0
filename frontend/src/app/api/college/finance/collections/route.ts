import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  studentId: z.string().uuid(),
  structureId: z.string().uuid(),
  amount: z.number().positive(),
  referenceNo: z.string().min(2).max(64),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a fee collection.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const student = await query(
      `SELECT id FROM stu.students WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR college_id = $3)`,
      [data.studentId, auth.tenantId, auth.collegeId]
    );
    if (!student.rows[0]) return problem(404, 'Not Found', 'That student is not in this institute.');
    const structure = await query(
      `SELECT s.id, s.college_id, s.status, v.id AS version_id, v.total_amount
       FROM fin.fee_structures s
       JOIN fin.fee_structure_versions v ON v.structure_id = s.id AND v.tenant_id = s.tenant_id AND v.deleted_at IS NULL AND v.version_no = 1
       WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL`,
      [data.structureId, auth.tenantId]
    );
    const fee = structure.rows[0];
    if (!fee || fee.status !== 'ACTIVE') return problem(409, 'Conflict', 'Collection is recorded only against an active fee structure.');
    if (data.amount > Number(fee.total_amount)) return problem(400, 'Validation Failed', 'The collection cannot exceed the fee total.');

    const created = await withTransaction(async (client) => {
      const stamp = Date.now().toString().slice(-8);
      const obligation = await client.query(
        `INSERT INTO fin.fee_obligations (
           tenant_id, student_id, structure_version_id, obligation_no, due_date,
           gross_amount, net_amount, paid_amount, tax_amount, status, created_by
         ) VALUES ($1, $2, $3, $4, CURRENT_DATE, $5, $5, $6, 0, $7, $8)
         RETURNING id`,
        [auth.tenantId, data.studentId, fee.version_id, `OBL-${stamp}`, fee.total_amount, data.amount, data.amount === Number(fee.total_amount) ? 'PAID' : 'PARTIALLY_PAID', auth.userId]
      );
      const payment = await client.query(
        `INSERT INTO fin.payments (tenant_id, student_id, payment_no, amount, currency_code, paid_on, reference_no, status, idempotency_key, created_by)
         VALUES ($1, $2, $3, $4, 'INR', now(), $5, 'SUCCESS', $6, $7)
         RETURNING id`,
        [auth.tenantId, data.studentId, `PAY-${stamp}`, data.amount, data.referenceNo, `office-${stamp}`, auth.userId]
      );
      const receipt = await client.query(
        `INSERT INTO fin.receipts (tenant_id, payment_id, student_id, receipt_no, receipt_date, total_amount, tax_amount, status, created_by)
         VALUES ($1, $2, $3, $4, CURRENT_DATE, $5, 0, 'ISSUED', $6)
         RETURNING id, receipt_no`,
        [auth.tenantId, payment.rows[0].id, data.studentId, `RCP-${stamp}`, data.amount, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'FEE_COLLECTED', 'COLLEGE_ADMIN', $3, 'PAYMENT', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, fee.college_id, auth.userId, payment.rows[0].id, JSON.stringify({ receiptNo: receipt.rows[0].receipt_no, amount: data.amount })]
      );
      return receipt.rows[0];
    });
    return NextResponse.json({ success: true, message: `Receipt ${created.receipt_no} issued. This is an office record, not a payment-gateway settlement.`, receipt: created }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/finance/collections', err);
    return problem(500, 'Server Error', 'The collection could not be recorded.');
  }
}
