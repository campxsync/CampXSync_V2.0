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
  SELECT id, student_id, request_type, status, reason, decided_by, decided_at
  FROM stu.lifecycle_requests
  ORDER BY created_at
`);
console.log('ALL REQUESTS IN DB:');
console.log(JSON.stringify(res.rows, null, 2));

await client.end();
