import pg from '../../frontend/node_modules/pg/lib/index.js';
const client = new pg.Client({
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
});
await client.connect();

const tenantId = 'a0000000-0000-0000-0000-000000000001';
const studentId = '81dd67d3-1d5f-4d8e-8061-a67648610013';

await client.query(`
  INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at)
  VALUES ($1, $2, 'ACTIVE', clock_timestamp(), clock_timestamp())
  ON CONFLICT (tenant_id, student_id) DO UPDATE 
  SET status = 'ACTIVE', updated_at = clock_timestamp()
`, [tenantId, studentId]);

console.log('Seeded student for US-013:', studentId);
await client.end();

