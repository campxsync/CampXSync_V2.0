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

async function run() {
  const client = new Client(DB_CONFIG);
  await client.connect();
  try {
    console.log('Connected to Dev Supabase');

    // 1. Swapped catalogue IDs check
    const templatesRes = await client.query(`
      SELECT code, name, catalogue_id, kind, scope_note 
      FROM iam.role_templates 
      WHERE deleted_at IS NULL
      ORDER BY catalogue_id, code
    `);
    console.log('\n--- ROLE TEMPLATES (count: ' + templatesRes.rows.length + ') ---');
    templatesRes.rows.filter(r => ['APPLICANT', 'ADMISSIONS_OFFICER', 'REGISTRAR', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'TENANT_ADMIN'].includes(r.code))
      .forEach(r => console.log(r.code, '->', r.catalogue_id, '| kind:', r.kind));

    // 2. Tenants count in core.tenants and iam.roles
    const tenantCountRes = await client.query('SELECT count(*) FROM core.tenants WHERE deleted_at IS NULL');
    const tenantRolesCountRes = await client.query('SELECT count(distinct tenant_id) FROM iam.roles WHERE deleted_at IS NULL');
    console.log('\nActive Tenants in core.tenants:', tenantCountRes.rows[0].count);
    console.log('Distinct Tenants in iam.roles:', tenantRolesCountRes.rows[0].count);

    // 3. TENANT_ADMIN holders
    const taHoldersRes = await client.query(`
      SELECT ra.id, ra.tenant_id, ra.user_id, r.code as role_code, up.email, up.full_name
      FROM iam.role_assignments ra
      JOIN iam.roles r ON ra.role_id = r.id
      JOIN iam.user_profiles up ON ra.user_id = up.id
      WHERE r.code = 'TENANT_ADMIN' AND ra.deleted_at IS NULL
    `);
    console.log('\n--- TENANT_ADMIN HOLDERS (count: ' + taHoldersRes.rows.length + ') ---');
    taHoldersRes.rows.forEach(r => {
      const maskedEmail = r.email ? r.email.replace(/^(.)(.*)(@.*)$/, (_, a, b, c) => a + '***' + c) : 'null';
      console.log('User:', r.user_id.substring(0, 8) + '***', '| Tenant:', r.tenant_id.substring(0, 8) + '***', '| Email:', maskedEmail);
    });

    // 4. Wildcard permissions in template and role permissions
    const wildcardTplRes = await client.query(`
      SELECT role_code, permission_code 
      FROM iam.role_template_permissions 
      WHERE permission_code = '*' AND deleted_at IS NULL
    `);
    console.log('\nWildcard * in role_template_permissions:', wildcardTplRes.rows);

    const wildcardRoleRes = await client.query(`
      SELECT r.code as role_code, count(*) as tenant_row_count
      FROM iam.role_permissions rp
      JOIN iam.roles r ON rp.role_id = r.id
      JOIN iam.permissions p ON rp.permission_id = p.id
      WHERE p.code = '*' AND r.deleted_at IS NULL
      GROUP BY r.code
    `);
    console.log('Wildcard * in role_permissions:', wildcardRoleRes.rows);

    // 5. Grant rules
    const grantRulesRes = await client.query(`
      SELECT id, grantor_role, grantable_role
      FROM iam.role_grant_rules
      WHERE deleted_at IS NULL
      ORDER BY grantor_role, grantable_role
    `);
    console.log('\n--- GRANT RULES (distinct pairs, total: ' + grantRulesRes.rows.length + ') ---');
    const pairs = grantRulesRes.rows.map(r => r.grantor_role + ' -> ' + r.grantable_role);
    console.log(pairs);

    // 6. Auditor permissions count
    const auditorPermsRes = await client.query(`
      SELECT permission_code 
      FROM iam.role_template_permissions 
      WHERE role_code = 'AUDITOR' AND deleted_at IS NULL
      ORDER BY permission_code
    `);
    console.log('\nAuditor template permissions count:', auditorPermsRes.rows.length);
    const auditorWrites = auditorPermsRes.rows.filter(r => 
      r.permission_code.includes('write') || 
      r.permission_code.includes('manage') || 
      r.permission_code.includes('admin') || 
      r.permission_code.includes('approve') || 
      r.permission_code.includes('comment')
    );
    console.log('Auditor write/admin perms:', auditorWrites.map(r => r.permission_code));

    // 7. Role counts per role code in iam.roles
    const roleStatsRes = await client.query(`
      SELECT code, count(*) as count 
      FROM iam.roles 
      WHERE deleted_at IS NULL 
      GROUP BY code 
      ORDER BY code
    `);
    console.log('\nDistinct role codes in iam.roles:', roleStatsRes.rows.length);

    // 8. R03 vs Tenant Admin comparison: Does R03 have all permissions needed?
    const r03PermsRes = await client.query(`
      SELECT permission_code 
      FROM iam.role_template_permissions 
      WHERE role_code = 'INSTITUTION_ADMIN' AND deleted_at IS NULL
      ORDER BY permission_code
    `);
    console.log('R03 template permissions count:', r03PermsRes.rows.length);

    // Check what other roles DEV Tenant Admin holder has:
    if (taHoldersRes.rows.length > 0) {
      const taUser = taHoldersRes.rows[0].user_id;
      const userOtherRoles = await client.query(`
        SELECT ra.tenant_id, r.code as role_code
        FROM iam.role_assignments ra
        JOIN iam.roles r ON ra.role_id = r.id
        WHERE ra.user_id = $1 AND ra.deleted_at IS NULL
      `, [taUser]);
      console.log('DEV Tenant Admin user existing roles:', userOtherRoles.rows);
    }

    // 9. All users and their effective role counts
    const userRoleCountsRes = await client.query(`
      SELECT user_id, count(*) as active_role_count
      FROM iam.role_assignments
      WHERE deleted_at IS NULL
      GROUP BY user_id
    `);
    console.log('Total users with active roles:', userRoleCountsRes.rows.length);

    // Save full raw snapshot data to json
    const snapshot = {
      timestamp: new Date().toISOString(),
      tenants_count_core: tenantCountRes.rows[0].count,
      tenants_count_roles: tenantRolesCountRes.rows[0].count,
      role_templates: templatesRes.rows,
      grant_rules_distinct: pairs,
      grant_rules_total_count: grantRulesRes.rows.length,
      auditor_permissions: auditorPermsRes.rows.map(r => r.permission_code),
      roles_per_code: roleStatsRes.rows,
      wildcard_templates: wildcardTplRes.rows,
      wildcard_roles: wildcardRoleRes.rows,
      r03_permissions: r03PermsRes.rows.map(r => r.permission_code),
      users_with_roles_count: userRoleCountsRes.rows.length
    };

    fs.writeFileSync('docs/audits/rbac-implementation/snapshots/before_phase1_live_snapshot.json', JSON.stringify(snapshot, null, 2));
    console.log('\nWrote docs/audits/rbac-implementation/snapshots/before_phase1_live_snapshot.json');

  } finally {
    await client.end();
  }
}

run().catch(err => {
  console.error('Snapshot failed:', err);
  process.exit(1);
});
