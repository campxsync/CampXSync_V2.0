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
  SELECT id, email, raw_user_meta_data
  FROM auth.users 
  WHERE email = 'student1.campx@gmail.com' 
  LIMIT 1
`);
console.log('STUDENT INFO:', JSON.stringify(res.rows[0], null, 2));

const docs = await client.query(`
  SELECT id, title, status, expires_at, created_at
  FROM doc.documents
  WHERE owner_id = $1
  ORDER BY created_at DESC
  LIMIT 5
`, [res.rows[0].id]);
console.log('EXISTING DOCS:', JSON.stringify(docs.rows, null, 2));

await client.end();
