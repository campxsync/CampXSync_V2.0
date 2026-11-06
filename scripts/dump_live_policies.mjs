// scripts/dump_live_policies.mjs
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) throw new Error('Not dev db');

  let connString = jdbcUrl.replace(/^jdbc:/, '');
  if (connString.includes('ap-southeast-1')) connString = connString.replace('ap-southeast-1', 'ap-south-1');

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
    const fnDef = await pool.query(`SELECT pg_get_functiondef('core.guard_student_person_update'::regproc) as def`);
    console.log('=== FUNCTION DEF: core.guard_student_person_update ===');
    console.log(fnDef.rows[0].def);

    const trgDef = await pool.query(`
      SELECT tgname, pg_get_triggerdef(oid) as def
      FROM pg_trigger
      WHERE tgname = 'trg_guard_student_person_update'
    `);
    console.log('\n=== TRIGGER DEF: trg_guard_student_person_update ===');
    console.log(trgDef.rows[0]?.def);

    const pols = await pool.query(`
      SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
      FROM pg_policies
      WHERE policyname LIKE '%student%'
      ORDER BY tablename, policyname
    `);
    console.log('\n=== LIVE STUDENT POLICIES IN PG_POLICIES ===');
    for (const p of pols.rows) {
      console.log(`Table: ${p.schemaname}.${p.tablename} | Policy: ${p.policyname} | Cmd: ${p.cmd}`);
      if (p.qual) console.log(`  USING: ${p.qual}`);
      if (p.with_check) console.log(`  WITH CHECK: ${p.with_check}`);
    }

  } finally {
    await pool.end();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});

