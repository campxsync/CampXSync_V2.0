const fs = require('fs');
const path = require('path');
const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;

const DB_CONFIG = {
  host: process.env.CAMPX_DB_HOST || 'aws-0-ap-south-1.pooler.supabase.com',
  port: parseInt(process.env.CAMPX_DB_PORT || '6543', 10),
  user: process.env.CAMPX_DB_USER || 'postgres.azulbaxszsqxymqmlwfe',
  password: process.env.CAMPX_DB_PASSWORD || 'Platform.Capmx@161221',
  database: process.env.CAMPX_DB_NAME || 'postgres',
  ssl: { rejectUnauthorized: false }
};

if (!DB_CONFIG.user.includes('azulbaxszsqxymqmlwfe')) {
  console.error('REFUSING TO RUN ON NON-DEV TARGET');
  process.exit(1);
}

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  try {
    console.log('Connected to Dev Supabase for Phase 2 migration');

    // 1. Apply Phase 2 migration
    const migrationSql = fs.readFileSync('database/migrations/20261120000200_rbac_phase2_narrow_r04_r03.sql', 'utf8');
    console.log('Applying 20261120000200_rbac_phase2_narrow_r04_r03.sql...');
    await client.query(migrationSql);
    console.log('[SUCCESS] Applied Phase 2 Migration SQL');

    // 2. Run verification assertions
    const verifySql = fs.readFileSync('database/migrations/verify_phase2_rbac.sql', 'utf8');
    console.log('Executing verify_phase2_rbac.sql assertions...');
    await client.query(verifySql);
    console.log('[SUCCESS] All Phase 2 automated assertions PASSED');

    // 3. Gather counts for reporting
    const rolesToCheck = ['INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN', 'AUDITOR', 'REGISTRAR', 'EXAM_CONTROLLER', 'FINANCE_MANAGER', 'HR_MANAGER', 'COMM_MANAGER'];
    const permsCountRes = await client.query(`
      SELECT role_code, count(*) as count
      FROM iam.role_template_permissions
      WHERE role_code = ANY($1) AND deleted_at IS NULL
      GROUP BY role_code
      ORDER BY role_code
    `, [rolesToCheck]);

    console.log('\n--- Template Permission Counts Post-Phase 2 ---');
    permsCountRes.rows.forEach(r => console.log(r.role_code.padEnd(22), r.count));

    // Save snapshot
    const usersCountRes = await client.query(`
      SELECT user_id, count(*) as active_roles
      FROM iam.role_assignments
      WHERE deleted_at IS NULL
      GROUP BY user_id
    `);

    const personasRes = await client.query(`
      SELECT up.id, up.full_name, up.email, r.code as role_code
      FROM iam.user_profiles up
      JOIN iam.role_assignments ra ON ra.user_id = up.id AND ra.deleted_at IS NULL
      JOIN iam.roles r ON ra.role_id = r.id
      WHERE up.id IN ('c05a0000-0000-4000-8000-000000000005', 'c07a0000-0000-4000-8000-000000000007')
    `);

    const afterSnapshot = {
      timestamp: new Date().toISOString(),
      role_template_permission_counts: permsCountRes.rows,
      dev_personas: personasRes.rows,
      active_users_count: usersCountRes.rows.length
    };

    fs.writeFileSync('docs/audits/rbac-implementation/snapshots/after_phase2_live_snapshot.json', JSON.stringify(afterSnapshot, null, 2));
    console.log('\n[SUCCESS] Wrote docs/audits/rbac-implementation/snapshots/after_phase2_live_snapshot.json');

  } catch (err) {
    console.error('[ERROR] Phase 2 failed:', err);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();
