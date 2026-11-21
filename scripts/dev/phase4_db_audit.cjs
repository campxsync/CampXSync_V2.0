const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

const TENANT_ID = 'a0000000-0000-0000-0000-000000000001';

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  console.log('=== TASK 4.8: ROLE TEMPLATES AUDITOR CATALOGUE ID ===');
  const templates = await client.query(`
    SELECT code, name, catalogue_id, kind
    FROM iam.role_templates
    WHERE code IN ('AUDITOR', 'SYSTEM_ADMIN', 'PLATFORM_ADMIN', 'INSTITUTION_ADMIN')
    ORDER BY code;
  `);
  console.log(templates.rows);

  console.log('\n=== TASK 4.4: R03 INSTITUTION ADMIN CURRENT PERMISSIONS ===');
  const r03Perms = await client.query(`
    SELECT p.code
    FROM iam.role_permissions rp
    JOIN iam.roles r ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.tenant_id = $1 AND r.code = 'INSTITUTION_ADMIN'
    ORDER BY p.code;
  `, [TENANT_ID]);
  console.log(`R03 Total Keys: ${r03Perms.rows.length}`);
  const r03Codes = r03Perms.rows.map(r => r.code);
  console.log('R03 Permissions:', r03Codes);

  // Check audit and sys write/manage in R03
  const r03Forbidden = r03Codes.filter(c => 
    c.startsWith('audit.') || (c.startsWith('sys.') && (c.includes('write') || c.includes('manage') || c.includes('admin')))
  );
  console.log('R03 forbidden audit / sys keys:', r03Forbidden);

  console.log('\n=== TASK 4.5: R04 COLLEGE ADMIN CURRENT PERMISSIONS ===');
  const r04Perms = await client.query(`
    SELECT p.code
    FROM iam.role_permissions rp
    JOIN iam.roles r ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.tenant_id = $1 AND r.code = 'COLLEGE_ADMIN'
    ORDER BY p.code;
  `, [TENANT_ID]);
  console.log(`R04 Total Keys: ${r04Perms.rows.length}`);
  const r04Codes = r04Perms.rows.map(r => r.code);
  
  // Find write/manage/approve/review/admin/execute outside adm01-adm09
  const writeSuffixes = ['manage', 'write', 'approve', 'review', 'admin', 'execute'];
  const r04DomainActionKeys = r04Codes.filter(c => {
    const isAdm = /^adm0[1-9]/.test(c);
    const hasAction = writeSuffixes.some(s => c.endsWith('.' + s) || c.endsWith('.' + s + '.own'));
    return !isAdm && hasAction;
  });
  console.log('R04 domain action keys outside adm01-adm09:', r04DomainActionKeys);

  // Check R19 (COMM_MANAGER) and R15/R16 (FINANCE)
  const r19Perms = await client.query(`
    SELECT p.code
    FROM iam.role_permissions rp
    JOIN iam.roles r ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.tenant_id = $1 AND r.code IN ('COMMUNICATION_MANAGER', 'COMM_MANAGER')
    ORDER BY p.code;
  `, [TENANT_ID]);
  console.log(`R19 Total Keys: ${r19Perms.rows.length}`);
  console.log('R19 Keys:', r19Perms.rows.map(r => r.code));

  const financePerms = await client.query(`
    SELECT r.code as role_code, p.code as perm_code
    FROM iam.role_permissions rp
    JOIN iam.roles r ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.tenant_id = $1 AND r.code IN ('FINANCE_OFFICER', 'ACCOUNTANT', 'FINANCE_MANAGER')
      AND (p.code LIKE 'fin%' OR p.code LIKE 'bil%')
    ORDER BY r.code, p.code;
  `, [TENANT_ID]);
  console.log('Finance roles keys count:', financePerms.rows.length);

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
