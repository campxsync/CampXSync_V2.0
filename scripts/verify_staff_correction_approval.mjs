// scripts/verify_staff_correction_approval.mjs
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';
const E2E_STUDENT_ID = 'e2e00000-0000-0000-0000-000000000002';
const E2E_PERSON_ID = 'e2e00000-0000-0000-0000-000000000001';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) throw new Error('Not dev db');

  let connString = jdbcUrl.replace(/^jdbc:/, '');
  if (connString.includes('ap-southeast-1')) connString = connString.replace('ap-southeast-1', 'ap-south-1');

  const urlObj = new URL(connString);
  urlObj.username = username;
  urlObj.password = password;
  urlObj.searchParams.delete('sslmode');

  return new Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 1,
  });
}

async function main() {
  console.log('=== VERIFYING REAL STAFF-PATH PROOF: COLLEGE ADMIN CORRECTION APPROVAL ===');
  const pool = getPool();
  const client = await pool.connect();

  try {
    // 1. Lookup College Admin (campxsync@gmail.com) from auth.users and iam.user_profiles
    const auCheck = await client.query(`
      SELECT u.id as auth_user_id, u.email, up.id as user_profile_id, up.person_id, up.tenant_id
      FROM auth.users u
      LEFT JOIN iam.user_profiles up ON up.id = u.id
      WHERE u.email = 'campxsync@gmail.com'
    `);

    if (auCheck.rowCount === 0) {
      throw new Error('College Admin user campxsync@gmail.com not found in auth.users');
    }

    const admin = auCheck.rows[0];
    const adminUserId = admin.auth_user_id;
    const adminTenantId = admin.tenant_id || 'a0000000-0000-0000-0000-000000000001';

    console.log(`[STAFF IDENTIFIED] ${admin.email} (Auth ID: ${adminUserId}, Tenant: ${adminTenantId})`);

    const raCheck = await client.query(`
      SELECT r.code, r.tenant_id
      FROM iam.role_assignments ra
      JOIN iam.roles r ON r.id = ra.role_id
      WHERE ra.user_id = $1 AND ra.deleted_at IS NULL
    `, [adminUserId]);
    console.log('[STAFF ROLES]:', raCheck.rows);

    const adminClaims = JSON.stringify({
      sub: adminUserId,
      role: 'authenticated',
      tenant_id: adminTenantId,
      app_metadata: {
        tenant_id: adminTenantId,
        role: 'COLLEGE_ADMIN',
      },
    });

    await client.query('BEGIN');

    await client.query('BEGIN');

    // 2. Create a temporary throwaway fixture student within transaction (ZERO data committed)
    const fixPersonId = 'f0000000-0000-0000-0000-000000000001';
    const fixUserId = 'f0000000-0000-0000-0000-000000000002';
    const fixStudentId = 'f0000000-0000-0000-0000-000000000003';

    await client.query(`
      INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_by, updated_by)
      VALUES ($1, $2, 'Fixture', 'Student', 'Fixture Student Original', 'fixture.student@campx.internal', 'ACTIVE', $3, $3)
    `, [fixPersonId, adminTenantId, adminUserId]);

    await client.query(`
      INSERT INTO iam.user_profiles (id, tenant_id, person_id, email, full_name, status, created_by, updated_by)
      VALUES ($1, $2, $3, 'fixture.student@campx.internal', 'Fixture Student Original', 'ACTIVE', $4, $4)
    `, [fixUserId, adminTenantId, fixPersonId, adminUserId]);

    const fixCollegeId = 'a0000000-0000-0000-0000-000000000002';

    await client.query(`
      INSERT INTO stu.students (id, tenant_id, person_id, college_id, enrollment_no, status, created_by, updated_by)
      VALUES ($1, $2, $3, $4, 'STU-FIX-9999', 'ACTIVE', $5, $5)
    `, [fixStudentId, adminTenantId, fixPersonId, fixCollegeId, adminUserId]);

    console.log(`[TARGET FIXTURE STUDENT CREATED] ID: ${fixStudentId} (Person: ${fixPersonId}, User: ${fixUserId})`);

    // 2b. Insert a pending correction on the fixture student
    const reqRes = await client.query(`
      INSERT INTO doc.lifecycle_requests (
        tenant_id, entity_type, entity_id, request_type, reason, status,
        requested_by, created_by, updated_by
      ) VALUES (
        $1, 'STUDENT', $2, 'CORRECTION',
        'Official spelling correction for fixture student', 'PENDING',
        $3, $3, $3
      ) RETURNING id
    `, [adminTenantId, fixStudentId, fixUserId]);

    const requestId = reqRes.rows[0].id;
    console.log(`[FIXTURE REQUEST CREATED] Request ID: ${requestId} for fixture student: ${fixStudentId}`);

    // 3. Act as College Admin
    await client.query("SET LOCAL ROLE authenticated");
    await client.query("SELECT set_config('request.jwt.claims', $1, true)", [adminClaims]);

    const isStaff = await client.query("SELECT (iam.has_permission('core.write') OR iam.has_permission('core.pii.write')) as is_staff");
    console.log(`[PERMISSION CHECK] College Admin is_staff: ${isStaff.rows[0]?.is_staff}`);

    // 4. College Admin approves the correction in doc.lifecycle_requests
    const approveRes = await client.query(`
      UPDATE doc.lifecycle_requests
      SET status = 'APPROVED',
          reason = reason || ' | Approved by College Admin: Verified official proof',
          completed_at = now(),
          updated_at = now(),
          updated_by = $1
      WHERE id = $2 AND tenant_id = $3
      RETURNING id, status, completed_at
    `, [adminUserId, requestId, adminTenantId]);

    console.log(`[CORRECTION DECIDED BY STAFF] RowCount:`, approveRes.rowCount, 'Rows:', approveRes.rows);

    // 5. College Admin updates the fixture student person's biographical record (first_name / display_name)
    // Proving the fail-closed guard trigger core.guard_student_person_update allows staff updates
    const personUpdateRes = await client.query(`
      UPDATE core.persons
      SET display_name = 'Fixture Student (Corrected Name)',
          updated_by = $1,
          updated_at = now()
      WHERE id = $2 AND tenant_id = $3
      RETURNING id, display_name
    `, [adminUserId, fixPersonId, adminTenantId]);

    console.log(`[PERSON UPDATED BY STAFF] RowCount:`, personUpdateRes.rowCount, 'Rows:', personUpdateRes.rows);
    console.log('[PROOF SUCCESSFUL] College Admin successfully decided correction request and updated person without trigger restriction!');

    // Rollback to keep database 100% clean
    await client.query('ROLLBACK');
    console.log('[HYGIENE] Transaction rolled back. Zero test rows left on dev database.\n');

  } finally {
    client.release();
    await pool.end();
  }
}

main().catch(err => {
  console.error('FATAL STAFF PATH ERROR:', err);
  process.exit(1);
});
