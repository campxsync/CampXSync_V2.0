// scripts/inspect_student1.mjs
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
    const pRes = await pool.query(`
      SELECT *
      FROM core.persons
      WHERE primary_email = 'student1.campx@gmail.com' OR id = '81dd67d3-1d5f-4d8e-8061-a6764861f924'
    `);
    console.log('PERSON:', JSON.stringify(pRes.rows, null, 2));

    const aRes = await pool.query(`
      SELECT id, line1, postal_code, created_by, created_at
      FROM core.addresses
      WHERE created_by = '81dd67d3-1d5f-4d8e-8061-a6764861f924'
    `);
    console.log('ADDRESSES CREATED BY STUDENT1:', JSON.stringify(aRes.rows, null, 2));

    const lRes = await pool.query(`
      SELECT id, entity_id, request_type, status, reason, created_at
      FROM doc.lifecycle_requests
      WHERE entity_id = '81dd67d3-1d5f-4d8e-8061-a67648610002' OR requested_by = '81dd67d3-1d5f-4d8e-8061-a6764861f924'
    `);
    console.log('LIFECYCLE REQUESTS:', JSON.stringify(lRes.rows, null, 2));

    const oRes = await pool.query(`
      SELECT id, aggregate_id, event_type, created_at
      FROM sys.outbox_events
      WHERE aggregate_id = '81dd67d3-1d5f-4d8e-8061-a67648610002'
    `);
    console.log('OUTBOX EVENTS:', JSON.stringify(oRes.rows, null, 2));

    const audRes = await pool.query(`
      SELECT id, table_schema, table_name, record_id, action, created_at
      FROM audit.change_log
      WHERE record_id = '81dd67d3-1d5f-4d8e-8061-a67648610002' OR actor_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924'
    `);
    console.log('AUDIT LOG ROWS:', JSON.stringify(audRes.rows, null, 2));

  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});

