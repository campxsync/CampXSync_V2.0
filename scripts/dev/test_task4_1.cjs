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
const COLLEGE_ID = 'a0000000-0000-0000-0000-000000000002';
const R03_USER_ID = 'c03a0000-0000-4000-8000-000000000003'; // Dedicated Institution Admin
const R04_USER_ID = '70a8ffae-b5ad-4c61-a401-16f220893df4'; // College Admin
const TARGET_USER_STUDENT = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const TARGET_USER_PARENT = 'c05a0000-0000-4000-8000-000000000099';

async function testRoleAssignment(client, personaLabel, actorId, roleCode, targetUserId, targetRoleCode) {
  console.log(`\n--- Testing ${personaLabel} inserting ${targetRoleCode} role assignment ---`);
  await client.query('BEGIN');
  try {
    await client.query('SET LOCAL ROLE authenticated');
    const claims = JSON.stringify({
      sub: actorId,
      role: 'authenticated',
      tenant_id: TENANT_ID,
      app_metadata: {
        tenant_id: TENANT_ID,
        role: roleCode
      }
    });
    await client.query(`SELECT set_config('request.jwt.claims', $1, true)`, [claims]);

    // Resolve target role ID
    const roleRes = await client.query(
      `SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = $2 AND deleted_at IS NULL LIMIT 1`,
      [TENANT_ID, targetRoleCode]
    );
    if (roleRes.rows.length === 0) {
      throw new Error(`Role ${targetRoleCode} not found in tenant`);
    }
    const targetRoleId = roleRes.rows[0].id;

    // Check iam.can_grant_role directly
    const canGrantRes = await client.query(`SELECT iam.can_grant_role($1)`, [targetRoleId]);
    console.log(`iam.can_grant_role('${targetRoleCode}') returned:`, canGrantRes.rows[0].can_grant_role);

    // Try inserting into iam.role_assignments
    const insertRes = await client.query(`
      INSERT INTO iam.role_assignments (
        tenant_id, role_id, user_id, college_id, granted_by, created_by, updated_by
      ) VALUES (
        $1, $2, $3, $4, $5, $5, $5
      ) RETURNING id;
    `, [TENANT_ID, targetRoleId, targetUserId, COLLEGE_ID, actorId]);

    console.log(`SUCCESS: Inserted assignment ID ${insertRes.rows[0].id}`);
  } catch (err) {
    console.log(`BLOCKED: Code ${err.code} - ${err.message}`);
  } finally {
    await client.query('ROLLBACK');
    console.log('Transaction safely ROLLED BACK.');
  }
}

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  // Test 4.1: Student and Parent role assignment
  // 1. R03 inserting STUDENT
  await testRoleAssignment(client, 'R03 Institution Admin', R03_USER_ID, 'INSTITUTION_ADMIN', TARGET_USER_STUDENT, 'STUDENT');
  // 2. R03 inserting PARENT
  await testRoleAssignment(client, 'R03 Institution Admin', R03_USER_ID, 'INSTITUTION_ADMIN', TARGET_USER_PARENT, 'PARENT');
  // 3. R04 inserting STUDENT
  await testRoleAssignment(client, 'R04 College Admin', R04_USER_ID, 'COLLEGE_ADMIN', TARGET_USER_STUDENT, 'STUDENT');
  // 4. R04 inserting PARENT
  await testRoleAssignment(client, 'R04 College Admin', R04_USER_ID, 'COLLEGE_ADMIN', TARGET_USER_PARENT, 'PARENT');

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
