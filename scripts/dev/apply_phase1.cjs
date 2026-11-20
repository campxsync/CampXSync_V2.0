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
    console.log('Connected to Dev Supabase for Phase 1 migration');

    // Read migration SQL
    const migrationSql = fs.readFileSync('database/migrations/20261120000100_rbac_phase1_database_foundation.sql', 'utf8');
    console.log('Applying 20261120000100_rbac_phase1_database_foundation.sql...');
    await client.query(migrationSql);
    console.log('[SUCCESS] Applied Phase 1 Migration SQL');

    // Run verification SQL
    const verifySql = fs.readFileSync('database/migrations/verify_phase1_rbac.sql', 'utf8');
    console.log('Executing verify_phase1_rbac.sql assertions...');
    await client.query(verifySql);
    console.log('[SUCCESS] All Phase 1 automated assertions PASSED');

    // Gather after-snapshot
    const templatesRes = await client.query(`
      SELECT code, name, catalogue_id, kind, scope_note 
      FROM iam.role_templates 
      WHERE deleted_at IS NULL
      ORDER BY catalogue_id, code
    `);
    const tenantRolesCountRes = await client.query('SELECT count(distinct tenant_id) FROM iam.roles WHERE deleted_at IS NULL');
    const wildcardTplRes = await client.query(`
      SELECT role_code, permission_code 
      FROM iam.role_template_permissions 
      WHERE permission_code = '*' AND deleted_at IS NULL
    `);
    const wildcardRoleRes = await client.query(`
      SELECT r.code as role_code, count(*) as tenant_row_count
      FROM iam.role_permissions rp
      JOIN iam.roles r ON rp.role_id = r.id
      JOIN iam.permissions p ON rp.permission_id = p.id
      WHERE p.code = '*' AND r.deleted_at IS NULL
      GROUP BY r.code
    `);
    const grantRulesRes = await client.query(`
      SELECT id, grantor_role, grantable_role
      FROM iam.role_grant_rules
      WHERE deleted_at IS NULL
      ORDER BY grantor_role, grantable_role
    `);
    const auditorPermsRes = await client.query(`
      SELECT permission_code 
      FROM iam.role_template_permissions 
      WHERE role_code = 'AUDITOR' AND deleted_at IS NULL
      ORDER BY permission_code
    `);
    const roleStatsRes = await client.query(`
      SELECT code, count(*) as count 
      FROM iam.roles 
      WHERE deleted_at IS NULL 
      GROUP BY code 
      ORDER BY code
    `);
    const userRoleCountsRes = await client.query(`
      SELECT user_id, count(*) as active_role_count
      FROM iam.role_assignments
      WHERE deleted_at IS NULL
      GROUP BY user_id
    `);

    const afterSnapshot = {
      timestamp: new Date().toISOString(),
      tenants_count_roles: tenantRolesCountRes.rows[0].count,
      role_templates_count: templatesRes.rows.length,
      role_templates: templatesRes.rows,
      grant_rules_count: grantRulesRes.rows.length,
      grant_rules: grantRulesRes.rows.map(r => r.grantor_role + ' -> ' + r.grantable_role),
      auditor_permissions_count: auditorPermsRes.rows.length,
      auditor_permissions: auditorPermsRes.rows.map(r => r.permission_code),
      roles_per_code: roleStatsRes.rows,
      wildcard_templates: wildcardTplRes.rows,
      wildcard_roles: wildcardRoleRes.rows,
      users_with_roles_count: userRoleCountsRes.rows.length
    };

    fs.writeFileSync('docs/audits/rbac-implementation/snapshots/after_phase1_live_snapshot.json', JSON.stringify(afterSnapshot, null, 2));
    console.log('[SUCCESS] Wrote after_phase1_live_snapshot.json');

    console.log('\nSummary:');
    console.log('- Role templates:', templatesRes.rows.length);
    console.log('- Grant rules count:', grantRulesRes.rows.length);
    console.log('- Auditor perms count:', auditorPermsRes.rows.length);
    console.log('- Wildcards in templates:', wildcardTplRes.rows.length);
    console.log('- Wildcards in role perms:', wildcardRoleRes.rows.length);
    console.log('- Distinct role codes per tenant:', roleStatsRes.rows.length);
    console.log('- Users with active roles:', userRoleCountsRes.rows.length);

  } catch (err) {
    console.error('[ERROR] Phase 1 failed:', err);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();
