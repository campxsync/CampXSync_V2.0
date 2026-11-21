const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;

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

  const res = await client.query('SELECT * FROM plat.platform_admins WHERE deleted_at IS NULL');
  console.log('Platform Admins:');
  console.log(res.rows);

  const uRes = await client.query(`
    SELECT ra.user_id, r.code, ra.tenant_id, ra.college_id
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id
    WHERE ra.user_id = 'ba77b8f7-ab32-46bd-85d2-5aade3526880'
  `);
  console.log('ba77b8f7 assignments:');
  console.log(uRes.rows);

  const instAdmins = await client.query(`
    SELECT ra.user_id, r.code, ra.tenant_id
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id
    WHERE r.code = 'INSTITUTION_ADMIN' AND ra.deleted_at IS NULL
  `);
  console.log('Existing INSTITUTION_ADMIN users:');
  console.log(instAdmins.rows);

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
