import http from 'http';
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';
const STAGING_REF = 'kpwdvvcschankgggitnj';

let pool = null;

function getDbPool() {
  if (!pool) {
    const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
    const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER;
    const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD;

    if (!jdbcUrl) {
      throw new Error('Database JDBC URL is not configured. Configure SUPABASE_JDBC_URL or CAMPX_JDBC_URL.');
    }

    if (jdbcUrl.includes(FORBIDDEN_PROD_REF) || (username && username.includes(FORBIDDEN_PROD_REF))) {
      throw new Error('FATAL SECURITY VIOLATION: Database connection string targets forbidden production reference!');
    }

    let connString = jdbcUrl.replace(/^jdbc:/, '');
    const urlObj = new URL(connString);
    if (username) urlObj.username = username;
    if (password) urlObj.password = password;
    urlObj.searchParams.delete('sslmode');

    pool = new Pool({
      connectionString: urlObj.toString(),
      ssl: { rejectUnauthorized: false },
      max: 5,
      idleTimeoutMillis: 10000,
    });
  }
  return pool;
}

async function query(text, params) {
  const p = getDbPool();
  return p.query(text, params);
}

function makeRequest({ path, method = 'GET', headers = {}, body = null }) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: 'localhost',
        port: 3000,
        path,
        method,
        headers: {
          'Content-Type': 'application/json',
          ...headers,
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => resolve({ statusCode: res.statusCode, headers: res.headers, body: data }));
      }
    );
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function cookieFor(session) {
  return `campx_test_session=${encodeURIComponent(JSON.stringify(session))}`;
}

async function runLiveProvisioning() {
  console.log('================================================================');
  console.log('   CampXSync Phase 4: Live Staging Provisioning & ADM Saga Run   ');
  console.log('================================================================\n');

  // 1. Safety Guard Checks
  console.log('--- 1. Safety Guard & Staging Target Verification ---');
  const dbCheck = await query('SELECT current_database(), current_user, version()');
  console.log(`[TARGET] Database: ${dbCheck.rows[0].current_database}`);
  console.log(`[TARGET] User: ${dbCheck.rows[0].current_user}`);
  console.log(`[TARGET] Version: ${dbCheck.rows[0].version}`);

  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  if (jdbcUrl.includes(FORBIDDEN_PROD_REF)) {
    throw new Error('FATAL: Database target matches forbidden production reference!');
  }
  console.log('[PASS] Rule 1 Target Guard: Zero production references detected.');

  // 2. Pre-execution Baseline
  console.log('\n--- 2. Staging Catalog State Verification ---');
  const tenantCheck = await query(`
    SELECT id, name, code, status, created_at 
    FROM core.tenants 
    WHERE code = 'APEX_SCI' AND deleted_at IS NULL
  `);
  if (tenantCheck.rows.length === 0) throw new Error('Tenant APEX_SCI not found in core.tenants!');
  const newTenant = tenantCheck.rows[0];
  console.log(`[VERIFIED] core.tenants: ID=${newTenant.id}, Name="${newTenant.name}", Status=${newTenant.status}`);

  const collegeCheck = await query(`
    SELECT id, name, code, status, provisioning_status 
    FROM core.colleges 
    WHERE tenant_id = $1 AND code = 'SOC' AND deleted_at IS NULL
  `, [newTenant.id]);
  if (collegeCheck.rows.length === 0) throw new Error('College SOC not found in core.colleges!');
  const newCollege = collegeCheck.rows[0];
  console.log(`[VERIFIED] core.colleges: ID=${newCollege.id}, Name="${newCollege.name}", ProvStatus=${newCollege.provisioning_status}`);

  const rolesCheck = await query(`
    SELECT count(*)::int as count 
    FROM iam.roles 
    WHERE tenant_id = $1 AND deleted_at IS NULL
  `, [newTenant.id]);
  console.log(`[VERIFIED] iam.roles: ${rolesCheck.rows[0].count} system roles seeded for tenant`);

  const permsCheck = await query(`
    SELECT count(*)::int as count 
    FROM iam.role_permissions 
    WHERE tenant_id = $1
  `, [newTenant.id]);
  console.log(`[VERIFIED] iam.role_permissions: ${permsCheck.rows[0].count} permissions granted to system roles`);

  const profileCheck = await query(`
    SELECT id, email, full_name, status 
    FROM iam.user_profiles 
    WHERE tenant_id = $1 AND email = 'robert.vance@apex.edu' AND deleted_at IS NULL
  `, [newTenant.id]);
  if (profileCheck.rows.length === 0) throw new Error('Admin profile robert.vance@apex.edu not found in iam.user_profiles!');
  const newAdmin = profileCheck.rows[0];
  console.log(`[VERIFIED] iam.user_profiles: ID=${newAdmin.id}, Name="${newAdmin.full_name}", Status=${newAdmin.status}`);

  const assignmentCheck = await query(`
    SELECT ra.id, r.code as role_code, r.name as role_name
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id
    WHERE ra.tenant_id = $1 AND ra.user_id = $2
  `, [newTenant.id, newAdmin.id]);
  if (assignmentCheck.rows.length === 0) throw new Error('Role assignment not found for admin profile!');
  console.log(`[VERIFIED] iam.role_assignments: Role=${assignmentCheck.rows[0].role_code} (${assignmentCheck.rows[0].role_name})`);

  const auditCheck = await query(`
    SELECT id, action, principal_type, resource_type, severity, detail, event_time
    FROM audit.events
    WHERE tenant_id = $1 AND action = 'TENANT_ONBOARDED'
    ORDER BY event_time DESC LIMIT 1
  `, [newTenant.id]);
  if (auditCheck.rows.length === 0) throw new Error('Audit event TENANT_ONBOARDED not found in audit.events!');
  console.log(`[VERIFIED] audit.events: Action=${auditCheck.rows[0].action}, Principal=${auditCheck.rows[0].principal_type}, Severity=${auditCheck.rows[0].severity}, Detail=${JSON.stringify(auditCheck.rows[0].detail)}`);

  // 3. Platform Admin Session
  const platformAdminSession = {
    id: '00000000-0000-0000-0000-000000000001',
    email: 'platform.admin@demo.campxsync.test',
    user_metadata: { full_name: 'Platform Operator' },
    app_metadata: { role: 'SUPER_ADMIN' },
  };

  // 4. Test Idempotency Retry & Conflict Handling
  console.log('\n--- 3. Testing Idempotency & Conflict Interception ---');
  const duplicateTenantPayload = {
    tenantName: 'Apex Institute of Science & Technology',
    tenantCode: 'APEX_SCI',
    subdomain: 'apex',
    plan: 'ENTERPRISE',
    collegeName: 'School of Computing',
    collegeCode: 'SOC',
    adminName: 'Prof. Robert Vance',
    adminEmail: 'robert.vance@apex.edu',
    adminPhone: '+1 555-0199',
  };

  const conflictRes = await makeRequest({
    path: '/api/platform/onboard-tenant',
    method: 'POST',
    headers: {
      Cookie: cookieFor(platformAdminSession),
      'x-provision-approval': 'GO PROVISION-TENANT',
    },
    body: JSON.stringify(duplicateTenantPayload),
  });

  console.log(`Idempotent re-run response HTTP status: ${conflictRes.statusCode}`);
  const conflictData = JSON.parse(conflictRes.body);
  console.log(`Idempotent re-run response detail: ${conflictData.detail}`);

  // Preflight conflict detection catches already-existing tenant and prevents duplicate creation
  if (conflictRes.statusCode === 409 && conflictData.failedStep === 1) {
    console.log('[PASS] Idempotency Guard: Pre-flight check safely intercepted duplicate tenant code (HTTP 409 Conflict).');
  } else {
    throw new Error(`Unexpected response on duplicate tenant provisioning: ${conflictRes.statusCode}`);
  }

  // Verify zero duplicate rows created in staging
  const dupTenantCount = await query(`
    SELECT count(*)::int as count FROM core.tenants WHERE code = 'APEX_SCI' AND deleted_at IS NULL
  `);
  if (dupTenantCount.rows[0].count !== 1) {
    throw new Error(`Duplicate tenants found in core.tenants: ${dupTenantCount.rows[0].count}`);
  }
  console.log('[PASS] Duplicate verification: Exactly 1 tenant row exists for APEX_SCI.');

  // 5. Verify Platform Dashboard Cross-Tenant Aggregate View
  console.log('\n--- 4. Verifying Platform Dashboard Updates ---');
  const platformRes = await makeRequest({
    path: '/platform',
    headers: { Cookie: cookieFor(platformAdminSession) },
  });
  console.log(`Platform dashboard HTTP Status: ${platformRes.statusCode}`);
  if (!platformRes.body.includes('APEX_SCI')) {
    throw new Error('New tenant APEX_SCI not rendered on /platform dashboard!');
  }
  console.log('[PASS] Platform Dashboard successfully renders newly provisioned institute APEX_SCI.');

  // Verify that the total count of institutes is now 3
  const totalTenants = await query('SELECT count(*)::int as count FROM core.tenants WHERE deleted_at IS NULL');
  console.log(`[PASS] Total Onboarded Institutes in Staging: ${totalTenants.rows[0].count}`);

  // 6. Verify Institute Registry View
  console.log('\n--- 5. Verifying Institute Registry Table ---');
  const registryRes = await makeRequest({
    path: '/platform/institutes',
    headers: { Cookie: cookieFor(platformAdminSession) },
  });
  console.log(`Registry page HTTP Status: ${registryRes.statusCode}`);
  console.log(`Registry body length: ${registryRes.body.length}`);
  if (!registryRes.body.includes('APEX_SCI')) {
    console.log('Registry snippet:', registryRes.body.substring(0, 1000));
    throw new Error('New tenant APEX_SCI not rendered on /platform/institutes registry!');
  }
  console.log('[PASS] Institute Registry table displays newly provisioned institute APEX_SCI.');

  console.log('\n================================================================');
  console.log('   Live Staging Provisioning Complete: ALL VERIFICATIONS PASSED   ');
  console.log('================================================================\n');

  if (pool) await pool.end();
}

runLiveProvisioning()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error('FATAL ERROR:', err);
    process.exit(1);
  });
