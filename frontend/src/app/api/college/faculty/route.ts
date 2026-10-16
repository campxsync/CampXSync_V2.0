import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateFacultySchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(50),
  lastName: z.string().min(1, 'Last name is required').max(50),
  email: z.string().email('Valid email address is required'),
  employeeNo: z.string().min(2, 'Employee ID is required').max(30),
  departmentId: z.string().uuid('Valid department ID is required'),
  employmentType: z.enum(['PERMANENT', 'CONTRACT', 'VISITING', 'PART_TIME', 'INTERN', 'DEPUTATION']).default('PERMANENT'),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can enroll faculty members.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
    if (!tenantId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing Tenant Context',
          status: 400,
          detail: 'No active tenant linked to user session.',
        },
        { status: 400 }
      );
    }

    const body = await req.json();
    const parseResult = CreateFacultySchema.safeParse(body);
    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { firstName, lastName, email, employeeNo, departmentId, employmentType } = parseResult.data;

    // 1. Verify Department exists in tenant and get college_id
    const deptRes = await query(`
      SELECT id, college_id FROM core.departments
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [departmentId, tenantId]);

    if (deptRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Department Not Found',
          status: 404,
          detail: 'The selected department does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }
    const dept = deptRes.rows[0];

    // 2. Check duplicate employeeNo in tenant
    const existingEmpRes = await query(`
      SELECT id FROM hrm.employees
      WHERE tenant_id = $1 AND employee_no = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [tenantId, employeeNo]);

    if (existingEmpRes.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Employee Number',
          status: 409,
          detail: `Faculty member with ID '${employeeNo}' already exists in your institution.`,
        },
        { status: 409 }
      );
    }

    // 3. Create person in core.persons
    const displayName = `${firstName} ${lastName}`.trim();
    const personRes = await query(`
      INSERT INTO core.persons (tenant_id, first_name, last_name, display_name, primary_email, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id
    `, [tenantId, firstName, lastName, displayName, email]);

    const person = personRes.rows[0];

    // 4. Create employee record in hrm.employees enforcing staff_category and ck_employees_employment_type
    const empRes = await query(`
      INSERT INTO hrm.employees (tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
      VALUES ($1, $2, $3, $4, $5, $6, 'TEACHING', 'ACTIVE')
      RETURNING id, employee_no, employment_type, status, created_at
    `, [tenantId, person.id, dept.college_id, departmentId, employeeNo, employmentType]);

    const employee = empRes.rows[0];

    return NextResponse.json(
      {
        success: true,
        message: `Faculty member '${displayName}' (${employeeNo}) registered successfully.`,
        employee: {
          ...employee,
          display_name: displayName,
          email,
        },
      },
      { status: 201 }
    );
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred.',
      },
      { status: 500 }
    );
  }
}
