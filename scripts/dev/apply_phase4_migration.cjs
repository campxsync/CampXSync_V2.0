const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;
const fs = require('fs');
const path = require('path');

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  console.log('--- Applying migration 20261120000400_rbac_phase4_verification_and_patch.sql ---');
  const migPath = path.resolve(__dirname, '../../database/migrations/20261120000400_rbac_phase4_verification_and_patch.sql');
  const migSql = fs.readFileSync(migPath, 'utf8');
  await client.query(migSql);
  console.log('Migration applied successfully.');

  console.log('--- Running verify_phase4_rbac.sql ---');
  const verifyPath = path.resolve(__dirname, '../../database/migrations/verify_phase4_rbac.sql');
  const verifySql = fs.readFileSync(verifyPath, 'utf8');
  await client.query(verifySql);
  console.log('Verification SQL passed successfully.');

  await client.end();
}

main().catch(err => {
  console.error('Migration / Verification FAILED:', err);
  process.exit(1);
});
