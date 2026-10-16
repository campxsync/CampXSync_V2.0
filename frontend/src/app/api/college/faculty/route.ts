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

const DeleteFacultySchema = z.object({
  id: z.string().uuid('Valid employee ID is required'),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can inspect the faculty roster.',
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

    const res = await query(`
      SELECT 
        e.id,
        e.employee_no,
        e.employment_type,
        e.status,
        e.department_id,
        e.created_at,
        p.display_name,
        p.primary_email,
        d.name as department_name,
        d.code as department_code
      FROM hrm.employees e
      LEFT JOIN core.persons p ON p.id = e.person_id
      LEFT JOIN core.departments d ON d.id = e.department_id
      WHERE e.tenant_id = $1 AND e.deleted_at IS NULL
      ORDER BY e.created_at DESC
    `, [tenantId]);

    return NextResponse.json({
      success: true,
      faculty: res.rows.map((e) => ({
        id: e.id,
        employee_no: e.employee_no,
        employment_type: e.employment_type,
        status: e.status,
        department_id: e.department_id,
        department_name: e.department_name,
        department_code: e.department_code,
        display_name: e.display_name || 'Unnamed Faculty',
        primary_email: e.primary_email || 'No Email',
        created_at: e.created_at,
      })),
    });
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

    // 3. Check duplicate email in tenant
    const existingEmailRes = await query(`
      SELECT id FROM core.persons
      WHERE tenant_id = $1 AND primary_email = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [tenantId, email]);

    if (existingEmailRes.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Email Address',
          status: 409,
          detail: `A person with email '${email}' is already registered in your institution.`,
        },
        { status: 409 }
      );
    }

    // 4. Create person in core.persons
    const displayName = `${firstName} ${lastName}`.trim();
    const personRes = await query(`
      INSERT INTO core.persons (tenant_id, first_name, last_name, display_name, primary_email, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id
    `, [tenantId, firstName, lastName, displayName, email]);

    const person = personRes.rows[0];

    // 5. Create employee record in hrm.employees enforcing staff_category and ck_employees_employment_type
    const empRes = await query(`
      INSERT INTO hrm.employees (tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
      VALUES ($1, $2, $3, $4, $5, $6, 'TEACHING', 'ACTIVE')
      RETURNING id, employee_no, employment_type, status, created_at
    `, [tenantId, person.id, dept.college_id, departmentId, employeeNo, employmentType]);

    const employee = empRes.rows[0];

    // 6. Provision IAM User Profile for the Faculty Member
    const profileRes = await query(`
      INSERT INTO iam.user_profiles (tenant_id, person_id, email, full_name, status, row_version)
      VALUES ($1, $2, $3, $4, 'ACTIVE', 1)
      ON CONFLICT (tenant_id, email) WHERE deleted_at IS NULL DO UPDATE SET person_id = EXCLUDED.person_id
      RETURNING id
    `, [tenantId, person.id, email, displayName]);

    const userProfileId = profileRes.rows[0]?.id;

    // 7. Grant default FACULTY Role Assignment via IAM Path
    const roleRes = await query(`
      SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'FACULTY' AND deleted_at IS NULL LIMIT 1
    `, [tenantId]);

    if (roleRes.rows.length > 0 && userProfileId) {
      const facultyRoleId = roleRes.rows[0].id;
      const existingAssignment = await query(`
        SELECT id FROM iam.role_assignments 
        WHERE tenant_id = $1 AND user_id = $2 AND role_id = $3 AND deleted_at IS NULL LIMIT 1
      `, [tenantId, userProfileId, facultyRoleId]);

      if (existingAssignment.rows.length === 0) {
        await query(`
          INSERT INTO iam.role_assignments (tenant_id, user_id, role_id, college_id, row_version)
          VALUES ($1, $2, $3, $4, 1)
        `, [tenantId, userProfileId, facultyRoleId, dept.college_id]);
      }
    }

    // 8. Record Consequential Event in audit.events
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, $2, now(), 'FACULTY_CREATED', 'COLLEGE_ADMIN', $3, 'EMPLOYEE', $4, 'SUCCESS', 'INFO', $5)
    `, [
      tenantId,
      dept.college_id,
      resolution.session.userId || null,
      employee.id,
      JSON.stringify({ employeeNo, displayName, email, departmentId, employmentType }),
    ]);

    return NextResponse.json(
      {
        success: true,
        message: `Faculty member '${displayName}' (${employeeNo}) registered and provisioned successfully.`,
        employee: {
          ...employee,
          display_name: displayName,
          email,
          user_profile_id: userProfileId,
        },
        faculty: {
          ...employee,
          display_name: displayName,
          email,
          user_profile_id: userProfileId,
        },
      },
      { status: 201 }
    );
  } catch (err: any) {
    console.error('POST /api/college/faculty error:', err);
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

export async function DELETE(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can deactivate faculty members.',
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
    const parseResult = DeleteFacultySchema.safeParse(body);
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

    const { id } = parseResult.data;

    // 1. Verify employee belongs to tenant
    const empRes = await query(`
      SELECT e.id, e.person_id, e.college_id, e.employee_no, p.display_name
      FROM hrm.employees e
      LEFT JOIN core.persons p ON p.id = e.person_id
      WHERE e.id = $1 AND e.tenant_id = $2 AND e.deleted_at IS NULL
      LIMIT 1
    `, [id, tenantId]);

    if (empRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Faculty Not Found',
          status: 404,
          detail: 'Faculty member does not exist in your tenant workspace or is already deactivated.',
        },
        { status: 404 }
      );
    }
    const emp = empRes.rows[0];

    // 2. Perform audited soft delete
    const now = new Date().toISOString();
    await query(`
      UPDATE hrm.employees
      SET deleted_at = $1, status = 'EXITED', updated_at = $1
      WHERE id = $2 AND tenant_id = $3
    `, [now, id, tenantId]);

    if (emp.person_id) {
      await query(`
        UPDATE iam.user_profiles
        SET deleted_at = $1, status = 'INACTIVE', updated_at = $1
        WHERE person_id = $2 AND tenant_id = $3
      `, [now, emp.person_id, tenantId]);
    }

    // 3. Record Audit Event
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, $2, now(), 'FACULTY_DELETED', 'COLLEGE_ADMIN', $3, 'EMPLOYEE', $4, 'SUCCESS', 'WARNING', $5)
    `, [
      tenantId,
      emp.college_id,
      resolution.session.userId || null,
      emp.id,
      JSON.stringify({ employeeNo: emp.employee_no, displayName: emp.display_name }),
    ]);

    return NextResponse.json({
      success: true,
      message: `Faculty member '${emp.display_name || emp.employee_no}' has been deactivated successfully.`,
    });
  } catch (err: any) {
    console.error('DELETE /api/college/faculty error:', err);
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
