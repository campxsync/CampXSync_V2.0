// scripts/apply_phase3b_migrations.mjs
import fs from 'node:fs';
import path from 'node:path';
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

async function main() {
  const pool = getPool();
  try {
    console.log(`Connected to target database reference: ${APPROVED_DEV_REF}`);

    const rlsSql = fs.readFileSync(path.resolve('database/migrations/20261105000200_stm01_applicant_documents_rls.sql'), 'utf8');
    console.log('Applying 20261105000200_stm01_applicant_documents_rls.sql...');
    await pool.query(rlsSql);
    console.log('[SUCCESS] Applied 20261105000200_stm01_applicant_documents_rls.sql');

    const seedSql = fs.readFileSync(path.resolve('database/migrations/20261105000300_seed_student2_applicant.sql'), 'utf8');
    console.log('Applying 20261105000300_seed_student2_applicant.sql...');
    await pool.query(seedSql);
    console.log('[SUCCESS] Applied 20261105000300_seed_student2_applicant.sql');

    // Verify policies
    const pRes = await pool.query(`
      SELECT tablename, policyname, cmd, roles
      FROM pg_policies
      WHERE schemaname = 'stu' AND tablename IN ('applications', 'application_documents')
      ORDER BY tablename, policyname
    `);
    console.log('APPLIED POLICIES:', JSON.stringify(pRes.rows, null, 2));

    // Verify student2 profile and application
    const s2Profile = await pool.query(`
      SELECT p.id, p.display_name, u.email, u.college_id, a.id as app_id, a.status as app_status, d.document_type, d.verification_status
      FROM core.persons p
      JOIN iam.user_profiles u ON u.person_id = p.id
      LEFT JOIN stu.applications a ON a.applicant_person_id = p.id
      LEFT JOIN stu.application_documents d ON d.application_id = a.id
      WHERE p.id = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae'
    `);
    console.log('STUDENT2 SEEDED RECORD:', JSON.stringify(s2Profile.rows, null, 2));
  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});

