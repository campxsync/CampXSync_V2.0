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

  console.log('Connected to Dev Supabase DB.');
  const users = await client.query(`
    SELECT ra.user_id, r.code, ra.college_id, ra.tenant_id
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id
    WHERE r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN')
      AND ra.deleted_at IS NULL
    ORDER BY ra.tenant_id, r.code
    LIMIT 20;
  `);
  console.log('Sample Admin Personas:');
  console.log(users.rows);

  const grantRules = await client.query(`
    SELECT grantor_role, grantable_role FROM iam.role_grant_rules
    WHERE grantable_role IN ('STUDENT', 'PARENT')
    ORDER BY grantor_role, grantable_role;
  `);
  console.log('Grant rules for STUDENT and PARENT:');
  console.log(grantRules.rows);

  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
