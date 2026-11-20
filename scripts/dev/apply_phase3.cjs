const fs = require('fs');
const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;

const DB_CONFIG = {
  host: process.env.CAMPX_DB_HOST || 'aws-0-ap-south-1.pooler.supabase.com',
  port: parseInt(process.env.CAMPX_DB_PORT || '6543', 10),
  user: process.env.CAMPX_DB_USER || 'postgres.azulbaxszsqxymqmlwfe',
  password: process.env.CAMPX_DB_PASSWORD || 'Platform.Capmx@161221',
  database: process.env.CAMPX_DB_NAME || 'postgres',
  ssl: { rejectUnauthorized: false }
};

if (!DB_CONFIG.user.includes('azulbaxszsqxymqmlwfe')) {
  console.error('REFUSING TO RUN ON NON-DEV TARGET');
  process.exit(1);
}

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  try {
    console.log('Connected to Dev Supabase for Phase 3 migration');

    // 1. Apply Phase 3 migration
    const migrationSql = fs.readFileSync('database/migrations/20261120000300_rbac_phase3_code_alignment.sql', 'utf8');
    console.log('Applying 20261120000300_rbac_phase3_code_alignment.sql...');
    await client.query(migrationSql);
    console.log('[SUCCESS] Applied Phase 3 Migration SQL');

    // 2. Run verification assertions
    const verifySql = fs.readFileSync('database/migrations/verify_phase3_rbac.sql', 'utf8');
    console.log('Executing verify_phase3_rbac.sql assertions...');
    await client.query(verifySql);
    console.log('[SUCCESS] All Phase 3 automated assertions PASSED');

  } catch (err) {
    console.error('[ERROR] Phase 3 failed:', err);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();
