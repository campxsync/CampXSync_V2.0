import { NextResponse } from 'next/server';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

export async function POST() {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can reconcile an office receipt.');
    if ('error' in auth) return auth.error;
    const payment = await query(
      `SELECT p.id, p.amount, r.receipt_no, r.total_amount, s.college_id
       FROM fin.payments p
       JOIN fin.receipts r ON r.payment_id = p.id AND r.deleted_at IS NULL
       JOIN stu.students s ON s.id = p.student_id
       WHERE p.tenant_id = $1 AND p.deleted_at IS NULL AND p.status = 'SUCCESS'
         AND NOT EXISTS (SELECT 1 FROM fin.reconciliations rec WHERE rec.payment_id = p.id AND rec.deleted_at IS NULL)
       ORDER BY p.created_at DESC LIMIT 1`,
      [auth.tenantId]
    );
    if (!payment.rows[0]) return problem(404, 'Not Found', 'No unmatched office payment is waiting to be reconciled.');
    const amount = Number(payment.rows[0].amount);
    const receiptAmount = Number(payment.rows[0].total_amount);
    if (amount !== receiptAmount) return problem(409, 'Conflict', 'The payment amount and the receipt amount differ, so this was not marked matched.');

    const created = await withTransaction(async (client) => {
      const row = await client.query(
        `INSERT INTO fin.reconciliations (tenant_id, recon_type, payment_id, source_ref, expected_amount, actual_amount, variance, status, reconciled_by, reconciled_at, created_by)
         VALUES ($1, 'PAYMENT', $2, $3, $4, $4, 0, 'MATCHED', $5, now(), $5)
         RETURNING id, status`,
        [auth.tenantId, payment.rows[0].id, payment.rows[0].receipt_no, amount, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'OFFICE_RECEIPT_RECONCILED', 'COLLEGE_ADMIN', $3, 'RECONCILIATION', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, payment.rows[0].college_id, auth.userId, row.rows[0].id, JSON.stringify({ receipt: payment.rows[0].receipt_no, amount })]
      );
      return { ...row.rows[0], receipt: payment.rows[0].receipt_no, amount };
    });
    return NextResponse.json({
      success: true,
      message: `Receipt ${created.receipt} matches the office payment of INR ${created.amount}. This is not a gateway settlement.`,
      reconciliation: created,
    }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/finance/reconcile', err);
    return problem(500, 'Server Error', 'The receipt could not be reconciled.');
  }
}
