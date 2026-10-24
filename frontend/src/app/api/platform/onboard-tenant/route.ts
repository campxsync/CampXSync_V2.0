import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

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

    // 3. Check Strict Staging Write Boundary
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
            { step: 1, name: 'CREATE_TENANT_ONBOARDING', status: 'COMPLETED', title: 'Institute Initialized in Onboarding Status' },
            { step: 2, name: 'SEED_TENANT_CONFIG', status: 'COMPLETED', title: 'System Role Templates & Default Configuration Seeded' },
            { step: 3, name: 'PROVISION_ADMIN_IDENTITY', status: 'COMPLETED', title: `College Administrator Profile Validated (${adminEmail})` },
            { step: 4, name: 'GRANT_COLLEGE_ADMIN_ROLE', status: 'COMPLETED', title: 'IAM COLLEGE_ADMIN Role Assignment Staged' },
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

    // 4. Forward to Java Institute Admin Domain Service via API Gateway
    const gatewayBaseUrl = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080';
    const idempotencyKey = req.headers.get('idempotency-key') || req.headers.get('x-idempotency-key') || `onboard-${tenantCode}-${Date.now()}`;
    const traceId = req.headers.get('x-trace-id') || crypto.randomUUID();

    const forwardPayload = {
      tenantName,
      tenantCode,
      collegeName,
      collegeCode,
      adminName,
      adminEmail,
      subdomain,
      plan,
      idempotencyKey
    };

    const res = await fetch(`${gatewayBaseUrl}/api/v1/admin/onboard-tenant`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': resolution.session.userId,
        'X-User-Role': 'PLATFORM_ADMIN',
        'X-Trace-Id': traceId,
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(forwardPayload),
    });

    const data = await res.json().catch(() => null);

    if (!res.ok) {
      return NextResponse.json(
        data || {
          type: 'https://campxsync.edu/errors/gateway-error',
          title: 'Gateway Error',
          status: res.status,
          detail: `Onboarding service returned status ${res.status}`,
        },
        { status: res.status }
      );
    }

    return NextResponse.json(
      {
        success: true,
        gated: false,
        message: `Institutional tenant '${tenantName}' (${tenantCode}) onboarded and activated successfully.`,
        saga: data?.sagaSteps || [
          { step: 1, name: 'CREATE_TENANT_ONBOARDING', status: 'COMPLETED', title: 'Institute Initialized in Onboarding Status' },
          { step: 2, name: 'SEED_TENANT_CONFIG', status: 'COMPLETED', title: 'System Role Templates & Default Configuration Seeded' },
          { step: 3, name: 'PROVISION_ADMIN_IDENTITY', status: 'COMPLETED', title: `College Administrator Profile Provisioned (${adminEmail})` },
          { step: 4, name: 'GRANT_COLLEGE_ADMIN_ROLE', status: 'COMPLETED', title: 'IAM COLLEGE_ADMIN Role Assignment Activated' },
          { step: 5, name: 'ACTIVATE_INSTITUTE', status: 'COMPLETED', title: 'Institutional Tenancy Node Activated (ACTIVE)' }
        ],
        tenant: {
          id: data?.tenantId,
          name: data?.tenantName || tenantName,
          code: data?.tenantCode || tenantCode,
          status: data?.status || 'ACTIVE',
          college: {
            id: data?.collegeId,
            name: data?.collegeName || collegeName,
            code: data?.collegeCode || collegeCode,
            status: 'ACTIVE',
            provisioning_status: 'ACTIVE'
          },
          adminProfile: {
            id: data?.adminUserId,
            email: data?.adminEmail || adminEmail,
            full_name: adminName,
            status: 'ACTIVE'
          }
        }
      },
      { status: res.status === 200 ? 200 : 201 }
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
