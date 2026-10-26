import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  expenseNo: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  expenseDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  description: z.string().min(2).max(200),
  totalAmount: z.number().positive(),
  taxAmount: z.number().min(0),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record an expense.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.taxAmount > data.totalAmount) return problem(400, 'Validation Failed', 'Tax cannot exceed the expense total.');
    const college = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const inserted = await query(
      `INSERT INTO fin.expenses (tenant_id, college_id, expense_no, expense_date, expense_type, total_amount, tax_amount, description, status, created_by)
       VALUES ($1, $2, $3, $4, 'PURCHASE', $5, $6, $7, 'DRAFT', $8)
       RETURNING id, expense_no, status`,
      [auth.tenantId, data.collegeId, data.expenseNo, data.expenseDate, data.totalAmount, data.taxAmount, data.description, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
       VALUES ($1, $2, now(), 'EXPENSE_RECORDED', 'COLLEGE_ADMIN', $3, 'EXPENSE', $4, 'SUCCESS', 'INFO', $5)`,
      [auth.tenantId, data.collegeId, auth.userId, inserted.rows[0].id, JSON.stringify({ taxAmount: data.taxAmount })]
    );
    return NextResponse.json({ success: true, message: 'Expense recorded as a draft.', expense: inserted.rows[0] }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'An expense with that number already exists.');
    console.error('POST /api/college/finance/expenses', err);
    return problem(500, 'Server Error', 'The expense could not be recorded.');
  }
}
