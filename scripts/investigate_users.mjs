import { query } from '../frontend/src/lib/db.ts';

async function investigate() {
  console.log('=== INVESTIGATING USER PROFILES & ROLE ASSIGNMENTS ===');

  // Check plat.platform_admins
  const paRes = await query('SELECT * FROM plat.platform_admins');
  console.log('plat.platform_admins rows:', paRes.rows);

  // Check iam.user_profiles
  const upRes = await query('SELECT id, tenant_id, status, deleted_at FROM iam.user_profiles');
  console.log('iam.user_profiles rows count:', upRes.rows.length);
  console.log('iam.user_profiles rows:', upRes.rows);

  // Check iam.role_assignments
  const raRes = await query(`
    SELECT ra.id, ra.user_id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
           r.code as role_code, r.name as role_name
    FROM iam.role_assignments ra
    JOIN iam.roles r ON ra.role_id = r.id
    WHERE ra.deleted_at IS NULL AND r.deleted_at IS NULL
  `);
  console.log('iam.role_assignments rows count:', raRes.rows.length);
  console.log('iam.role_assignments rows:', raRes.rows);

  // Check auth.users if accessible
  const uRes = await query('SELECT id, email FROM auth.users');
  console.log('auth.users count:', uRes.rows.length);
  for (const u of uRes.rows) {
    console.log(`  User: ${u.email} -> ${u.id}`);
  }

  process.exit(0);
}

investigate().catch(console.error);

