// scripts/inspect_intakes.mjs
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
    const intakes = await pool.query(`
      SELECT *
      FROM stu.admission_intakes
    `);
    console.log('INTAKES:', JSON.stringify(intakes.rows, null, 2));

    const perms = await pool.query(`
      SELECT code, module, action
      FROM iam.permissions
      WHERE code LIKE 'adm%' OR code LIKE 'stu%'
      ORDER BY code
    `);
    console.log('PERMISSIONS:', JSON.stringify(perms.rows, null, 2));
  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
