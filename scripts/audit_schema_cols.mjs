import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || '';
  const password = process.env.SUPABASE_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) {
    throw new Error('Target guard failed: ' + username);
  }

  let connString = jdbcUrl.replace(/^jdbc:/, '').replace('ap-southeast-1', 'ap-south-1');
  const urlObj = new URL(connString);
  urlObj.username = username;
  urlObj.password = password;
  urlObj.searchParams.delete('sslmode');

  return new Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 2,
  });
}

async function main() {
  const pool = getPool();
  try {
    const tCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='core' AND table_name='tenants' ORDER BY ordinal_position"
    );
    console.log('core.tenants cols:\n', tCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

    const cCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='core' AND table_name='colleges' ORDER BY ordinal_position"
    );
    console.log('\ncore.colleges cols:\n', cCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

    const uCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='iam' AND table_name='user_profiles' ORDER BY ordinal_position"
    );
    console.log('\niam.user_profiles cols:\n', uCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

    const rCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='iam' AND table_name='role_assignments' ORDER BY ordinal_position"
    );
    console.log('\niam.role_assignments cols:\n', rCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

    const pCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='plat' AND table_name='tenant_provisioning' ORDER BY ordinal_position"
    );
    console.log('\nplat.tenant_provisioning cols:\n', pCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

    const outboxCols = await pool.query(
      "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='sys' AND table_name='outbox_events' ORDER BY ordinal_position"
    );
    console.log('\nsys.outbox_events cols:\n', outboxCols.rows.map(r => `  ${r.column_name} (${r.data_type})`).join('\n'));

  } finally {
    await pool.end();
  }
}

main().catch(console.error);

