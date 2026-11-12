const { Client } = require('../../frontend/node_modules/pg');

const client = new Client({
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
});

client.connect().then(async () => {
  const policies = await client.query("SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check FROM pg_policies WHERE schemaname = 'doc' AND tablename IN ('documents', 'document_versions', 'document_links') ORDER BY tablename, policyname");
  console.log('Policies count:', policies.rows.length);
  policies.rows.forEach(p => console.log(`[${p.tablename}] ${p.policyname} (${p.cmd})`));
  await client.end();
}).catch(e => {
  console.error('Error:', e.message);
  process.exit(1);
});

