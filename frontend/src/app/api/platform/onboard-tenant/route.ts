import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { inviteCollegeAdmin } from '@/lib/supabase/admin';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const OnboardTenantSchema = z.object({
  tenantName: z.string().min(3, 'Institute name must be at least 3 characters').max(100),
  tenantCode: z
    .string()
    .min(2, 'Institute code must be at least 2 characters')
    .max(30, 'Institute code must be at most 30 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Institute code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
  subdomain: z
    .string()
    .min(2, 'Subdomain must be at least 2 characters')
    .max(30, 'Subdomain must be at most 30 characters')
    .regex(/^[a-z0-9-]+$/, 'Subdomain must contain only lowercase alphanumeric characters and hyphens')
    .optional(),
  plan: z.enum(['ENTERPRISE', 'GROWTH', 'STANDARD', 'TRIAL']).default('ENTERPRISE'),
  collegeName: z.string().min(3, 'Primary college name must be at least 3 characters').max(100),
  collegeCode: z
    .string()
    .min(2, 'College code must be at least 2 characters')
    .max(30, 'College code must be at most 30 characters')
    .regex(/^[A-Z0-9_-]+$/, 'College code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
  adminName: z.string().min(2, 'First College Administrator name is required'),
  adminEmail: z.string().email('Valid administrator email is required'),
  adminPhone: z.string().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    // 1. Enforce Platform Admin authorization on the server
    if (!resolution.success || resolution.session.role !== 'platform_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only platform administrators are authorized to onboard new institutional tenants.',
        },
        { status: 403 }
      );
    }

    // 2. Validate request payload against schema
    const body = await req.json();
    const parseResult = OnboardTenantSchema.safeParse(body);

    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Error',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { tenantName, tenantCode, collegeName, collegeCode, adminName, adminEmail, plan } = parseResult.data;
    const subdomain = parseResult.data.subdomain || tenantCode.toLowerCase();

    // 3. Pre-flight Saga Check: Duplicate Tenant Code Conflict
    const existingTenant = await query(`
      SELECT id, code FROM core.tenants WHERE code = $1 AND deleted_at IS NULL LIMIT 1
    `, [tenantCode]);

    if (existingTenant.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Tenant Code Conflict',
          status: 409,
          failedStep: 1,
          detail: `Institutional tenant code '${tenantCode}' is already registered in the platform registry.`,
        },
        { status: 409 }
      );
    }

    // 4. Pre-flight Saga Check: Duplicate Administrator Email Conflict
    const existingAdmin = await query(`
      SELECT id, email FROM iam.user_profiles WHERE email = $1 AND deleted_at IS NULL LIMIT 1
    `, [adminEmail]);

    if (existingAdmin.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Administrator Identity Conflict',
          status: 409,
          failedStep: 3,
          detail: `User profile with email '${adminEmail}' already exists on the platform.`,
        },
        { status: 409 }
      );
    }

    // 5. Check Strict Staging Write Boundary
    // Gated database writes require explicit approval phrase: GO PROVISION-TENANT
    const allowStagingProvision =
      req.headers.get('x-provision-approval') === 'GO PROVISION-TENANT';

    if (!allowStagingProvision) {
      // In pre-approval mode, return validated saga simulation without permanent database mutations
      return NextResponse.json(
        {
          success: true,
          gated: true,
          message: "Pre-provisioning saga validated. Live staging write is safely gated awaiting user approval phrase 'GO PROVISION-TENANT'.",
          saga: [
            { step: 1, name: 'CREATE_TENANT_ONBOARDING', status: 'VALIDATED', title: 'Institute Initialized in Onboarding Status' },
            { step: 2, name: 'SEED_TENANT_CONFIG', status: 'VALIDATED', title: 'System Role Templates & Default Configuration Seeded' },
            { step: 3, name: 'PROVISION_ADMIN_IDENTITY', status: 'VALIDATED', title: `College Administrator Profile Validated (${adminEmail})` },
            { step: 4, name: 'GRANT_COLLEGE_ADMIN_ROLE', status: 'VALIDATED', title: 'IAM COLLEGE_ADMIN Role Assignment Staged' },
            { step: 5, name: 'ACTIVATE_INSTITUTE', status: 'GATED', title: "Awaiting Explicit Approval 'GO PROVISION-TENANT'" }
          ],
          tenant: {
            name: tenantName,
            code: tenantCode,
            subdomain: `${subdomain}.campxsync.edu`,
            plan,
            status: 'ONBOARDING',
            collegeName,
            collegeCode,
            adminEmail,
            adminName
          }
        },
        { status: 200 }
      );
    }

    // 6. Execute Live 5-Step ADM Saga with Idempotent Upserts
    // Step 1: Create institute in ONBOARDING (INACTIVE) status
    const tenantRes = await query(`
      INSERT INTO core.tenants (name, code, status)
      VALUES ($1, $2, 'INACTIVE')
      ON CONFLICT (code) WHERE deleted_at IS NULL DO UPDATE SET name = EXCLUDED.name
      RETURNING id, name, code, status, created_at
    `, [tenantName, tenantCode]);

    const tenant = tenantRes.rows[0];

    // Create primary college in core.colleges
    const collegeRes = await query(`
      INSERT INTO core.colleges (tenant_id, name, code, status, provisioning_status)
      VALUES ($1, $2, $3, 'ACTIVE', 'PROVISIONING')
      ON CONFLICT (tenant_id, code) WHERE deleted_at IS NULL DO UPDATE SET name = EXCLUDED.name
      RETURNING id, name, code, status, provisioning_status
    `, [tenant.id, collegeName, collegeCode]);

    const college = collegeRes.rows[0];

    // Step 2: Seed tenant configuration from platform defaults (30 system roles)
    await query(`SELECT iam.seed_system_roles($1)`, [tenant.id]);

    // Invite before the profile insert so the profile id can be the Auth user id.
    // Role resolution looks up iam.user_profiles.id and iam.role_assignments.user_id
    // by the authenticated user id. Invite failure does not roll back the tenant.
    const authInviteResult = await inviteCollegeAdmin(adminEmail);
    const authUserId = authInviteResult.authInvite === 'INVITED' ? authInviteResult.userId : null;

    // Step 3: Provision first College Admin in iam.user_profiles
    const profileRes = authUserId
      ? await query(`
          INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, status, row_version)
          VALUES ($1, $2, $3, $4, 'ACTIVE', 1)
          ON CONFLICT (tenant_id, email) WHERE deleted_at IS NULL DO UPDATE SET full_name = EXCLUDED.full_name
          RETURNING id, email, full_name, status
        `, [authUserId, tenant.id, adminEmail, adminName])
      : await query(`
          INSERT INTO iam.user_profiles (tenant_id, email, full_name, status, row_version)
          VALUES ($1, $2, $3, 'ACTIVE', 1)
          ON CONFLICT (tenant_id, email) WHERE deleted_at IS NULL DO UPDATE SET full_name = EXCLUDED.full_name
          RETURNING id, email, full_name, status
        `, [tenant.id, adminEmail, adminName]);

    const adminProfile = profileRes.rows[0];

    // Step 4: Grant default COLLEGE_ADMIN role through IAM path
    const roleRes = await query(`
      SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL LIMIT 1
    `, [tenant.id]);

    if (roleRes.rows.length > 0) {
      const collegeAdminRoleId = roleRes.rows[0].id;
      const existingAssignment = await query(`
        SELECT id FROM iam.role_assignments 
        WHERE tenant_id = $1 AND user_id = $2 AND role_id = $3 AND deleted_at IS NULL LIMIT 1
      `, [tenant.id, adminProfile.id, collegeAdminRoleId]);

      if (existingAssignment.rows.length === 0) {
        await query(`
          INSERT INTO iam.role_assignments (tenant_id, user_id, role_id, college_id, row_version)
          VALUES ($1, $2, $3, $4, 1)
        `, [tenant.id, adminProfile.id, collegeAdminRoleId, college.id]);
      }
    }

    // Step 5: Activate the institute after successful provisioning
    await query(`
      UPDATE core.tenants SET status = 'ACTIVE' WHERE id = $1
    `, [tenant.id]);

    await query(`
      UPDATE core.colleges SET provisioning_status = 'ACTIVE' WHERE id = $1
    `, [college.id]);

    // Step 6: Immutable Platform Audit Record in audit.events
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, resource_type, resource_id, status, severity, detail
      )
      VALUES (
        $1, $2, now(), 'TENANT_ONBOARDED', 'PLATFORM_ADMIN', 'TENANT', $1, 'SUCCESS', 'INFO', $3
      )
    `, [tenant.id, college.id, JSON.stringify({ tenantCode, tenantName, plan, adminEmail, subdomain })]);

    return NextResponse.json(
      {
        success: true,
        gated: false,
        ...(authInviteResult.authInvite === 'INVITED'
          ? { authInvite: 'INVITED' as const }
          : authInviteResult),
        message: `Institutional tenant '${tenantName}' (${tenantCode}) onboarded and activated successfully.`,
        saga: [
          { step: 1, name: 'CREATE_TENANT_ONBOARDING', status: 'COMPLETED', title: 'Institute Initialized in Onboarding Status' },
          { step: 2, name: 'SEED_TENANT_CONFIG', status: 'COMPLETED', title: 'System Role Templates & Default Configuration Seeded' },
          { step: 3, name: 'PROVISION_ADMIN_IDENTITY', status: 'COMPLETED', title: `College Administrator Profile Provisioned (${adminEmail})` },
          { step: 4, name: 'GRANT_COLLEGE_ADMIN_ROLE', status: 'COMPLETED', title: 'IAM COLLEGE_ADMIN Role Assignment Activated' },
          { step: 5, name: 'ACTIVATE_INSTITUTE', status: 'COMPLETED', title: 'Institutional Tenancy Node Activated (ACTIVE)' }
        ],
        tenant: {
          ...tenant,
          status: 'ACTIVE',
          college,
          adminProfile
        }
      },
      { status: 201 }
    );
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred during institutional onboarding saga execution.',
      },
      { status: 500 }
    );
  }
}
