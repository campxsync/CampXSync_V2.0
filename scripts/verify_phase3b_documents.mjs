// scripts/verify_phase3b_documents.mjs
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) {
    throw new Error('Refusing to execute on non-dev target: ' + username);
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

async function runTest(client, name, fn) {
  await client.query('SAVEPOINT tc_sp');
  try {
    await fn();
    console.log(`[PASS] ${name}`);
    await client.query('ROLLBACK TO SAVEPOINT tc_sp');
  } catch (err) {
    await client.query('ROLLBACK TO SAVEPOINT tc_sp');
    console.error(`[FAIL] ${name}: ${err.message}`);
    throw err;
  }
}

async function main() {
  const pool = getPool();
  const client = await pool.connect();
  try {
    const tenantId = 'a0000000-0000-0000-0000-000000000001';
    const student1AuthId = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
    const student1AppId = '81dd67d3-1d5f-4d8e-8061-a67648610003';
    const student2AuthId = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
    const student2AppId = 'bdf7a805-ff27-4ecd-862e-4c0c7b700003';
    const staffAuthId = '70a8ffae-b5ad-4c61-a401-16f220893df4'; // College Admin

    await client.query('BEGIN');

    // Helper to switch JWT claims
    async function switchSession(userId, role = 'authenticated') {
      await client.query('SET LOCAL ROLE authenticated');
      const claims = JSON.stringify({
        sub: userId,
        role: role,
        tenant_id: tenantId,
        app_metadata: { tenant_id: tenantId }
      });
      await client.query(`SELECT set_config('request.jwt.claims', $1, true)`, [claims]);
    }

    console.log('=== RUNNING PHASE 3b DOCUMENT RLS TEST MATRIX ===');

    // TC-01: Student 2 reads own application
    await runTest(client, 'TC-01: Student 2 reads own application', async () => {
      await switchSession(student2AuthId);
      const res = await client.query('SELECT id, application_number, status FROM stu.applications WHERE id = $1', [student2AppId]);
      if (res.rows.length !== 1) throw new Error(`Expected 1 application row, got ${res.rows.length}`);
      if (res.rows[0].application_number !== 'APP-DEV-2026-0002') throw new Error('Mismatched app number');
    });

    // TC-02: Student 2 reads own seeded document (REJECTED)
    await runTest(client, 'TC-02: Student 2 reads own seeded document', async () => {
      await switchSession(student2AuthId);
      const res = await client.query('SELECT id, document_type, verification_status, rejection_reason FROM stu.application_documents WHERE application_id = $1', [student2AppId]);
      if (res.rows.length !== 1) throw new Error(`Expected 1 doc row, got ${res.rows.length}`);
      if (res.rows[0].verification_status !== 'REJECTED') throw new Error('Expected REJECTED status');
    });

    // TC-03: Student 2 inserts new valid document for own application (re-upload)
    await runTest(client, 'TC-03: Student 2 re-uploads valid document for own application', async () => {
      await switchSession(student2AuthId);
      const docId = 'bdf7a805-ff27-4ecd-862e-4c0c7b700099';
      await client.query(`
        INSERT INTO stu.application_documents (
          tenant_id, id, application_id, document_id, document_type, storage_path,
          file_name, mime_type, file_size_bytes, verification_status, is_fraudulent, created_at, updated_at, row_version
        ) VALUES (
          $1, $2, $3, gen_random_uuid(), 'HIGH_SCHOOL_TRANSCRIPT', 'admissions/bdf7a805-ff27-4ecd-862e-4c0c7b700003/transcript_v2.pdf',
          'high_school_transcript_v2.pdf', 'application/pdf', 512000, 'PENDING', false, now(), now(), 1
        )
      `, [tenantId, docId, student2AppId]);

      const verify = await client.query('SELECT verification_status FROM stu.application_documents WHERE id = $1', [docId]);
      if (verify.rows.length !== 1 || verify.rows[0].verification_status !== 'PENDING') {
        throw new Error('Re-uploaded document verification status mismatch');
      }
    });

    // TC-04: Student 2 cannot read Student 1 application
    await runTest(client, 'TC-04: Student 2 cannot read Student 1 application (returns 0 rows)', async () => {
      await switchSession(student2AuthId);
      const res = await client.query('SELECT id FROM stu.applications WHERE id = $1', [student1AppId]);
      if (res.rows.length !== 0) throw new Error(`Foreign application leaked! Rows returned: ${res.rows.length}`);
    });

    // TC-05: Student 2 cannot read Student 1 documents
    await runTest(client, 'TC-05: Student 2 cannot read Student 1 documents (returns 0 rows)', async () => {
      await switchSession(student2AuthId);
      const res = await client.query('SELECT id FROM stu.application_documents WHERE application_id = $1', [student1AppId]);
      if (res.rows.length !== 0) throw new Error(`Foreign documents leaked! Rows returned: ${res.rows.length}`);
    });

    // TC-06: Student 2 cannot insert document for Student 1 application
    await runTest(client, 'TC-06: Student 2 cannot insert document for Student 1 application', async () => {
      await switchSession(student2AuthId);
      let failed = false;
      try {
        await client.query(`
          INSERT INTO stu.application_documents (
            tenant_id, id, application_id, document_id, document_type, storage_path,
            file_name, mime_type, file_size_bytes, verification_status, is_fraudulent, created_at, updated_at, row_version
          ) VALUES (
            $1, gen_random_uuid(), $2, gen_random_uuid(), 'GOVT_ID', 'admissions/attack.pdf',
            'attack.pdf', 'application/pdf', 1024, 'PENDING', false, now(), now(), 1
          )
        `, [tenantId, student1AppId]);
      } catch (e) {
        failed = true;
      }
      if (!failed) throw new Error('Attacker was able to insert document for foreign application!');
    });

    // TC-07: Student 2 cannot self-verify (forge status = VERIFIED on insert)
    await runTest(client, 'TC-07: Student 2 cannot self-verify (forging status = VERIFIED rejected by WITH CHECK)', async () => {
      await switchSession(student2AuthId);
      let failed = false;
      try {
        await client.query(`
          INSERT INTO stu.application_documents (
            tenant_id, id, application_id, document_id, document_type, storage_path,
            file_name, mime_type, file_size_bytes, verification_status, is_fraudulent, created_at, updated_at, row_version
          ) VALUES (
            $1, gen_random_uuid(), $2, gen_random_uuid(), 'GOVT_ID', 'admissions/attack.pdf',
            'attack.pdf', 'application/pdf', 1024, 'VERIFIED', false, now(), now(), 1
          )
        `, [tenantId, student2AppId]);
      } catch (e) {
        failed = true;
      }
      if (!failed) throw new Error('Student was able to self-verify document on insert!');
    });

    // TC-08: Student 1 (HANDED_OVER) cannot see Student 2 documents
    await runTest(client, 'TC-08: Student 1 cannot see Student 2 documents', async () => {
      await switchSession(student1AuthId);
      const res = await client.query('SELECT id FROM stu.application_documents WHERE application_id = $1', [student2AppId]);
      if (res.rows.length !== 0) throw new Error(`Cross-student document leakage! Rows returned: ${res.rows.length}`);
    });

    // TC-09: College Admin can verify Student 2 document
    await runTest(client, 'TC-09: College Admin can update verification status', async () => {
      await switchSession(staffAuthId);
      const res = await client.query(`
        UPDATE stu.application_documents
        SET verification_status = 'VERIFIED', verified_by = $1, verified_at = now()
        WHERE id = 'bdf7a805-ff27-4ecd-862e-4c0c7b700004'
        RETURNING verification_status
      `, [staffAuthId]);
      if (res.rows.length !== 1 || res.rows[0].verification_status !== 'VERIFIED') {
        throw new Error('College admin update failed');
      }
    });

    console.log('=== ALL 9 PHASE 3b TESTS PASSED ===');
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

