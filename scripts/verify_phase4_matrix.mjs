// scripts/verify_phase4_matrix.mjs
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) {
    throw new Error('Refusing non-dev target: ' + username);
  }

  let connString = jdbcUrl.replace(/^jdbc:/, '');
  if (connString.includes('ap-southeast-1')) {
    connString = connString.replace('ap-southeast-1', 'ap-south-1');
  }

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

async function runTestCase(client, name, fn) {
  await client.query('SAVEPOINT matrix_sp');
  try {
    await fn();
    console.log(`[PASS] ${name}`);
    await client.query('ROLLBACK TO SAVEPOINT matrix_sp');
  } catch (err) {
    await client.query('ROLLBACK TO SAVEPOINT matrix_sp');
    console.error(`[FAIL] ${name}: ${err.message}`);
    throw err;
  }
}

async function main() {
  const pool = getPool();
  const client = await pool.connect();

  try {
    const tenantA = 'a0000000-0000-0000-0000-000000000001';
    const tenantB = 'e2e00000-0000-0000-0000-000000000001';

    const s1Auth = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
    const s1Record = '81dd67d3-1d5f-4d8e-8061-a67648610002';
    const s1App = '81dd67d3-1d5f-4d8e-8061-a67648610003';

    const s2Auth = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
    const s2App = 'bdf7a805-ff27-4ecd-862e-4c0c7b700003';

    await client.query('BEGIN');

    async function setSession(userId, tenantId = tenantA) {
      await client.query('SET LOCAL ROLE authenticated');
      const claims = JSON.stringify({
        sub: userId,
        role: 'authenticated',
        tenant_id: tenantId,
        app_metadata: { tenant_id: tenantId }
      });
      await client.query(`SELECT set_config('request.jwt.claims', $1, true)`, [claims]);
    }

    console.log('=== RUNNING PHASE 4 CROSS-ROLE & TENANT ISOLATION MATRIX ===\n');

    // 1. Cross-student address access denial
    await runTestCase(client, 'Matrix-01: Student 2 cannot read Student 1 address', async () => {
      // Create a permanent address for student 1 in savepoint
      const addrId = '81dd67d3-1d5f-4d8e-8061-a67648610088';
      await setSession(s1Auth);
      await client.query(`
        INSERT INTO core.addresses (id, tenant_id, line1, city, state_code, postal_code, country_code, created_by, updated_by)
        VALUES ($1, $2, '108 Palm Avenue', 'Bengaluru', 'KA', '560002', 'IND', $3, $3)
      `, [addrId, tenantA, s1Auth]);

      // Switch to student 2
      await setSession(s2Auth);
      const res = await client.query('SELECT id FROM core.addresses WHERE id = $1', [addrId]);
      if (res.rows.length !== 0) throw new Error('Student 2 read Student 1 private address!');
    });

    // 2. Cross-student address write denial
    await runTestCase(client, 'Matrix-02: Student 2 cannot update Student 1 address', async () => {
      const addrId = '81dd67d3-1d5f-4d8e-8061-a67648610089';
      await setSession(s1Auth);
      await client.query(`
        INSERT INTO core.addresses (id, tenant_id, line1, city, state_code, postal_code, country_code, created_by, updated_by)
        VALUES ($1, $2, '109 Palm Avenue', 'Bengaluru', 'KA', '560002', 'IND', $3, $3)
      `, [addrId, tenantA, s1Auth]);

      // Switch to student 2 and attempt update
      await setSession(s2Auth);
      const res = await client.query('UPDATE core.addresses SET line1 = \'Hacked Address\' WHERE id = $1', [addrId]);
      if (res.rowCount !== 0) throw new Error('Student 2 was able to update Student 1 address!');
    });

    // 3. Cross-student correction request denial
    await runTestCase(client, 'Matrix-03: Student 2 cannot read Student 1 correction requests', async () => {
      const reqId = '81dd67d3-1d5f-4d8e-8061-a67648610090';
      await setSession(s1Auth);
      await client.query(`
        INSERT INTO doc.lifecycle_requests (
          id, tenant_id, entity_type, entity_id, request_type, reason, status, requested_by, created_at, updated_at, created_by, updated_by, row_version
        ) VALUES (
          $1, $2, 'STUDENT', $3, 'CORRECTION', '{"targetField":"displayName"}', 'PENDING', $4, now(), now(), $4, $4, 1
        )
      `, [reqId, tenantA, s1Record, s1Auth]);

      // Switch to student 2
      await setSession(s2Auth);
      const res = await client.query('SELECT id FROM doc.lifecycle_requests WHERE id = $1', [reqId]);
      if (res.rows.length !== 0) throw new Error('Student 2 read Student 1 correction request!');
    });

    // 4. Cross-student correction insert spoofing denial
    await runTestCase(client, 'Matrix-04: Student 2 cannot submit correction request targeting Student 1 record', async () => {
      await setSession(s2Auth);
      let failed = false;
      try {
        await client.query(`
          INSERT INTO doc.lifecycle_requests (
            id, tenant_id, entity_type, entity_id, request_type, reason, status, requested_by, created_at, updated_at, created_by, updated_by, row_version
          ) VALUES (
            gen_random_uuid(), $1, 'STUDENT', $2, 'CORRECTION', '{"targetField":"displayName"}', 'PENDING', $3, now(), now(), $3, $3, 1
          )
        `, [tenantA, s1Record, s2Auth]);
      } catch (e) {
        failed = true;
      }
      if (!failed) throw new Error('Student 2 submitted correction request against Student 1!');
    });

    // 5. Cross-tenant isolation (Tenant A student cannot see Tenant B records)
    await runTestCase(client, 'Matrix-05: Cross-tenant isolation (Student 1 in Tenant A cannot see Tenant B students)', async () => {
      await setSession(s1Auth, tenantA);
      const res = await client.query('SELECT id FROM stu.students WHERE tenant_id = $1', [tenantB]);
      if (res.rows.length !== 0) throw new Error(`Cross-tenant data leaked! Returned ${res.rows.length} rows`);
    });

    // 6. Cross-tenant application documents isolation
    await runTestCase(client, 'Matrix-06: Cross-tenant isolation (Student 2 in Tenant A cannot see Tenant B application documents)', async () => {
      await setSession(s2Auth, tenantA);
      const res = await client.query('SELECT id FROM stu.application_documents WHERE tenant_id = $1', [tenantB]);
      if (res.rows.length !== 0) throw new Error(`Cross-tenant documents leaked! Returned ${res.rows.length} rows`);
    });

    // 7. Student cannot read audit change logs for other entities / foreign students
    await runTestCase(client, 'Matrix-07: Student cannot read audit.change_log for other entities', async () => {
      await setSession(s1Auth);
      // Query for records NOT belonging to s1Record
      const res = await client.query('SELECT id FROM audit.change_log WHERE record_id::text != $1', [s1Record]);
      if (res.rows.length !== 0) throw new Error(`Student was able to read foreign audit change logs! (${res.rows.length} rows)`);
    });

    // 8. Student 1 cannot forge bio data on person update (guard trigger)
    await runTestCase(client, 'Matrix-08: Student cannot bypass guard trigger to change DOB or gender', async () => {
      await setSession(s1Auth);
      let caught = false;
      try {
        await client.query(`
          UPDATE core.persons
          SET date_of_birth = '1999-01-01', updated_at = now()
          WHERE id = $1 AND tenant_id = $2
        `, [s1Auth, tenantA]);
      } catch (e) {
        if (e.message.includes('Student self-service is restricted to residential address changes')) {
          caught = true;
        }
      }
      if (!caught) throw new Error('Student bypassed guard trigger to alter date_of_birth!');
    });

    console.log('\n=== ALL 8 MATRIX TEST CASES PASSED CONVINCINGLY ===');
  } finally {
    await client.query('ROLLBACK');
    client.release();
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
