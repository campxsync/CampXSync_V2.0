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
const COLLEGE_ID_OWN = 'a0000000-0000-0000-0000-000000000002';
const COLLEGE_ID_OTHER = 'b0000000-0000-0000-0000-000000000002'; // another college
const R04_USER_ID = '70a8ffae-b5ad-4c61-a401-16f220893df4'; // College Admin
const R03_USER_ID = 'ba77b8f7-ab32-46bd-85d2-5aade3526880'; // Institution Admin
const TARGET_USER = '81dd67d3-1d5f-4d8e-8061-a6764861f924';

async function runTest(client, personaLabel, actorId, roleCode, collegeScope) {
  console.log(`\n======================================================`);
  console.log(`RUNNING PRIVILEGE ESCALATION TESTS AS: ${personaLabel}`);
  console.log(`======================================================`);

  async function execCase(testCaseName, fn) {
    process.stdout.write(`[Test ${testCaseName}] `);
    await client.query('BEGIN');
    try {
      await client.query('SET LOCAL ROLE authenticated');
      const claims = JSON.stringify({
        sub: actorId,
        role: 'authenticated',
        tenant_id: TENANT_ID,
        app_metadata: {
          tenant_id: TENANT_ID,
          role: roleCode,
          college_id: collegeScope
        }
      });
      await client.query(`SELECT set_config('request.jwt.claims', $1, true)`, [claims]);

      await fn();
      console.log('UNEXPECTED SUCCESS (ESCALATION POSSIBLE!)');
    } catch (err) {
      console.log(`BLOCKED (${err.code || 'ERR'}: ${err.message})`);
    } finally {
      await client.query('ROLLBACK');
    }
  }

  // (a) inserting a permission into the role_permissions of COLLEGE_ADMIN itself (e.g. acd07.manage)
  await execCase('(a) Insert acd07.manage into COLLEGE_ADMIN role_permissions', async () => {
    const rRes = await client.query(`SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'COLLEGE_ADMIN'`, [TENANT_ID]);
    const pRes = await client.query(`SELECT id FROM iam.permissions WHERE code = 'acd07.manage'`);
    if (!rRes.rows.length || !pRes.rows.length) throw new Error('Role or permission not found');
    await client.query(`
      INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
      VALUES ($1, $2, $3);
    `, [TENANT_ID, rRes.rows[0].id, pRes.rows[0].id]);
  });

  // (b) adding a permission to a system role such as PRINCIPAL
  await execCase('(b) Add permission to system role PRINCIPAL', async () => {
    const rRes = await client.query(`SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'PRINCIPAL'`, [TENANT_ID]);
    const pRes = await client.query(`SELECT id FROM iam.permissions WHERE code = 'adm01.manage'`);
    if (!rRes.rows.length || !pRes.rows.length) throw new Error('Role or permission not found');
    await client.query(`
      INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
      VALUES ($1, $2, $3);
    `, [TENANT_ID, rRes.rows[0].id, pRes.rows[0].id]);
  });

  // (c) creating a custom role and giving it keys caller does not hold, then assigning it
  await execCase('(c) Create custom role with unheld permission and assign it', async () => {
    // 1. Create custom role
    const newRoleId = 'ffffffff-0000-0000-0000-000000000001';
    await client.query(`
      INSERT INTO iam.roles (id, tenant_id, code, name, is_system, college_id)
      VALUES ($1, $2, 'CUSTOM_TEST_ROLE', 'Custom Test Role', false, $3);
    `, [newRoleId, TENANT_ID, collegeScope]);

    // 2. Try giving it a key caller does not hold, e.g. acd07.manage
    const pRes = await client.query(`SELECT id FROM iam.permissions WHERE code = 'acd07.manage' LIMIT 1`);
    if (!pRes.rows.length) throw new Error('Permission acd07.manage not found');
    await client.query(`
      INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
      VALUES ($1, $2, $3);
    `, [TENANT_ID, newRoleId, pRes.rows[0].id]);

    // 3. Try assigning it
    await client.query(`
      INSERT INTO iam.role_assignments (tenant_id, role_id, user_id, college_id, granted_by, created_by, updated_by)
      VALUES ($1, $2, $3, $4, $5, $5, $5);
    `, [TENANT_ID, newRoleId, TARGET_USER, collegeScope, actorId]);
  });

  // (d) creating a delegation of a role caller does not hold
  await execCase('(d) Delegate role caller does not hold (e.g. REGISTRAR or PRINCIPAL)', async () => {
    const rRes = await client.query(`SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'REGISTRAR'`, [TENANT_ID]);
    if (!rRes.rows.length) throw new Error('Role REGISTRAR not found');
    await client.query(`
      INSERT INTO iam.delegations (
        tenant_id, role_id, from_user_id, to_user_id, valid_from, valid_to
      ) VALUES (
        $1, $2, $3, $4, CURRENT_DATE, CURRENT_DATE + interval '7 days'
      );
    `, [TENANT_ID, rRes.rows[0].id, actorId, TARGET_USER]);
  });

  // (e) assigning a role in another college
  await execCase('(e) Assign role in another college', async () => {
    const rRes = await client.query(`SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'FACULTY'`, [TENANT_ID]);
    if (!rRes.rows.length) throw new Error('Role FACULTY not found');
    await client.query(`
      INSERT INTO iam.role_assignments (tenant_id, role_id, user_id, college_id, granted_by, created_by, updated_by)
      VALUES ($1, $2, $3, $4, $5, $5, $5);
    `, [TENANT_ID, rRes.rows[0].id, TARGET_USER, COLLEGE_ID_OTHER, actorId]);
  });
}

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  await runTest(client, 'R04 College Admin (own college)', R04_USER_ID, 'COLLEGE_ADMIN', COLLEGE_ID_OWN);
  await runTest(client, 'R03 Institution Admin (institute scope)', R03_USER_ID, 'INSTITUTION_ADMIN', null);

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
