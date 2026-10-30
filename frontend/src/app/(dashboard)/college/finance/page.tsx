import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { queryAll } from '@/lib/db';
import { FinanceManager } from './finance-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeFinancePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') redirect('/access-denied?reason=forbidden');
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  const [colleges, years, students, structures, receipts, expenses, taxes, scholarships, employees, budgets, audit] = await queryAll([
    { text: `SELECT id, code, name FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2) ORDER BY code`, params: [tenantId, collegeId] },
    { text: `SELECT id, code FROM core.academic_years WHERE tenant_id = $1 ORDER BY start_date DESC`, params: [tenantId] },
    { text: `SELECT s.id, s.enrollment_no, p.display_name FROM stu.students s LEFT JOIN core.persons p ON p.id = s.person_id
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL AND ($2::uuid IS NULL OR s.college_id = $2) ORDER BY s.enrollment_no`, params: [tenantId, collegeId] },
    { text: `SELECT s.id, s.code, s.name, s.status, v.total_amount
       FROM fin.fee_structures s
       LEFT JOIN fin.fee_structure_versions v ON v.structure_id = s.id AND v.version_no = 1 AND v.deleted_at IS NULL
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL ORDER BY s.code`, params: [tenantId] },
    { text: `SELECT r.receipt_no, r.total_amount, r.status, s.enrollment_no
       FROM fin.receipts r LEFT JOIN stu.students s ON s.id = r.student_id
       WHERE r.tenant_id = $1 AND r.deleted_at IS NULL ORDER BY r.created_at DESC LIMIT 20`, params: [tenantId] },
    { text: `SELECT id, expense_no, description, total_amount, tax_amount, status FROM fin.expenses WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY created_at DESC`, params: [tenantId] },
    { text: `SELECT c.code, c.name, r.rate_pct FROM tax.tax_codes c
       LEFT JOIN tax.tax_rates r ON r.tax_code_id = c.id AND r.deleted_at IS NULL
       WHERE c.tenant_id = $1 AND c.deleted_at IS NULL ORDER BY c.code`, params: [tenantId] },
    { text: `SELECT id, code, name, total_budget, status FROM fin.scholarship_schemes WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, params: [tenantId] },
    { text: `SELECT count(*)::int AS employees FROM hrm.employees WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR college_id = $2)`, params: [tenantId, collegeId] },
    { text: `SELECT id, code, name, total_amount, status FROM bud.budgets WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, params: [tenantId] },
    { text: `SELECT action, event_time FROM audit.events
       WHERE tenant_id = $1 AND action IN ('FEE_STRUCTURE_OPENED','FEE_STRUCTURE_ACTIVATED','FEE_COLLECTED','EXPENSE_RECORDED','TAX_CODE_RECORDED','SCHOLARSHIP_OPENED','BUDGET_OPENED','OFFICE_RECEIPT_RECONCILED')
       ORDER BY event_time DESC LIMIT 12`, params: [tenantId] },
  ]);

  const money = (rows: { total_amount?: string | number | null; amount?: string | number | null }[], key: 'total_amount' | 'amount') =>
    rows.reduce((sum, row) => sum + Number(row[key] || 0), 0);

  return (
    <FinanceManager
      colleges={colleges.rows}
      years={years.rows}
      students={students.rows.map((row) => ({ id: row.id, label: `${row.enrollment_no} · ${row.display_name || 'Student'}` }))}
      structures={structures.rows.map((row) => ({ ...row, total_amount: Number(row.total_amount || 0) }))}
      receipts={receipts.rows.map((row) => ({ ...row, total_amount: Number(row.total_amount) }))}
      expenses={expenses.rows.map((row) => ({ ...row, total_amount: Number(row.total_amount), tax_amount: Number(row.tax_amount) }))}
      taxes={taxes.rows.map((row) => ({ ...row, rate_pct: row.rate_pct === null ? null : Number(row.rate_pct) }))}
      scholarships={scholarships.rows.map((row) => ({ ...row, total_budget: Number(row.total_budget || 0) }))}
      employeeCount={employees.rows[0]?.employees || 0}
      budgets={budgets.rows.map((row) => ({ ...row, total_amount: Number(row.total_amount || 0) }))}
      audit={audit.rows}
      collected={money(receipts.rows, 'total_amount')}
      spent={money(expenses.rows, 'total_amount')}
    />
  );
}
