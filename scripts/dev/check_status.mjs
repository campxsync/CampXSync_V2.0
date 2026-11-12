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
  SELECT conname, pg_get_constraintdef(c.oid) 
  FROM pg_constraint c 
  JOIN pg_namespace n ON n.oid = c.connamespace 
  WHERE n.nspname = 'stu' AND c.conrelid = 'stu.graduation_records'::regclass
`);
console.log('CONSTRAINTS ON stu.graduation_records:', res.rows);

await client.end();
