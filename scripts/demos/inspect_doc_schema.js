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
  const tables = await client.query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'doc' ORDER BY table_name");
  console.log('Tables in schema doc:', tables.rows.map(r => r.table_name));

  for (const t of tables.rows) {
    const cols = await client.query("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema = 'doc' AND table_name = $1 ORDER BY ordinal_position", [t.table_name]);
    console.log(`\nColumns for doc.${t.table_name}:`);
    cols.rows.forEach(c => console.log(`  ${c.column_name} (${c.data_type}, nullable: ${c.is_nullable})`));
  }

  await client.end();
}).catch(e => {
  console.error('Error:', e.message);
  process.exit(1);
});

