// scripts/inspect_student1_full.mjs
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) {
    throw new Error('Not dev db');
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
    const s1Id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';

    const profiles = await pool.query(`SELECT * FROM iam.user_profiles WHERE id = $1`, [s1Id]);
    console.log('USER_PROFILES:', JSON.stringify(profiles.rows, null, 2));

    const roles = await pool.query(`
      SELECT ra.*, r.code as role_code, r.name as role_name 
      FROM iam.role_assignments ra 
      JOIN iam.roles r ON ra.role_id = r.id 
      WHERE ra.user_id = $1`, [s1Id]);
    console.log('ROLE_ASSIGNMENTS:', JSON.stringify(roles.rows, null, 2));

    const collegeUsers = await pool.query(`SELECT * FROM core.college_users WHERE user_id = $1`, [s1Id]).catch(e => ({ rows: e.message }));
    console.log('COLLEGE_USERS:', JSON.stringify(collegeUsers.rows, null, 2));

    const appDocs = await pool.query(`SELECT * FROM stu.application_documents WHERE application_id = '81dd67d3-1d5f-4d8e-8061-a67648610003'`);
    console.log('STUDENT1_APP_DOCS:', JSON.stringify(appDocs.rows, null, 2));
  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
