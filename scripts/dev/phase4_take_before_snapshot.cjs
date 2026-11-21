const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;
const fs = require('fs');
const path = require('path');

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  const grantRulesRes = await client.query(`
    SELECT grantor_role, grantable_role FROM iam.role_grant_rules
    ORDER BY grantor_role, grantable_role;
  `);

  const platformAdminsRes = await client.query(`
    SELECT id, user_id, role_code, status FROM plat.platform_admins
    WHERE deleted_at IS NULL
    ORDER BY role_code, user_id;
  `);

  const roleTemplatePermCounts = await client.query(`
    SELECT role_code, COUNT(*)::text as count
    FROM iam.role_template_permissions
    GROUP BY role_code
    ORDER BY role_code;
  `);

  const instAdminAssignments = await client.query(`
    SELECT ra.user_id, r.code as role_code, ra.tenant_id, ra.college_id
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id
    WHERE r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN') AND ra.deleted_at IS NULL
    ORDER BY ra.tenant_id, r.code, ra.user_id;
  `);

  const snapshot = {
    timestamp: new Date().toISOString(),
    grant_rules_count: grantRulesRes.rows.length,
    grant_rules: grantRulesRes.rows,
    platform_admins: platformAdminsRes.rows,
    role_template_permission_counts: roleTemplatePermCounts.rows,
    admin_assignments: instAdminAssignments.rows
  };

  const outPath = path.resolve(__dirname, '../../docs/audits/rbac-implementation/snapshots/before_phase4_live_snapshot.json');
  fs.writeFileSync(outPath, JSON.stringify(snapshot, null, 2), 'utf8');
  console.log('Saved before-snapshot to', outPath);

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
