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
const res = await client.query(`
  SELECT student_id, status, effective_from 
  FROM stu.lifecycle_status 
  WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001' AND status = 'ACTIVE'
  LIMIT 5
`);
console.log('ACTIVE STUDENTS:', res.rows);
await client.end();

