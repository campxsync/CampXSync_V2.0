import fs from 'node:fs';
import pg from 'pg';

const text = fs.readFileSync(new URL('../.env.local', import.meta.url), 'utf8');
const env = Object.fromEntries(
  text.split(/\r?\n/).filter((line) => line.includes('=') && !line.startsWith('#')).map((line) => {
    const index = line.indexOf('=');
    return [line.slice(0, index), line.slice(index + 1).trim().replace(/^"|"$/g, '')];
  })
);

const jdbc = env.SUPABASE_JDBC_URL || env.CAMPX_JDBC_URL;
const user = env.SUPABASE_USER || env.CAMPX_DB_USER;
const password = env.SUPABASE_PASSWORD || env.CAMPX_DB_PASSWORD;
const url = new URL(jdbc.replace(/^jdbc:/, ''));
if (user) url.username = user;
if (password) url.password = password;
url.searchParams.delete('sslmode');

const client = new pg.Client({ connectionString: url.toString(), ssl: { rejectUnauthorized: false } });
await client.connect();
const schemas = ['plat','core','iam','cfg','acd','stu','hrm','lms','obe','fin','tax','bud','pay','exm','cam','doc','rpt','wf','sys','audit'];
const lines = ['-- App schemas exported from CampXSync-Staging before any project replacement.', '-- Auth, storage, and row data are not included.', ''];

const tables = await client.query(
  `SELECT n.nspname, c.relname, c.relkind
   FROM pg_class c
   JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = ANY($1) AND c.relkind IN ('r','p')
   ORDER BY 1, 2`,
  [schemas]
);

for (const schema of schemas) lines.push(`CREATE SCHEMA IF NOT EXISTS ${schema};`);
lines.push('');

for (const table of tables.rows) {
  const columns = await client.query(
    `SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod) AS type,
            pg_get_expr(ad.adbin, ad.adrelid) AS default_expr, a.attnotnull
     FROM pg_attribute a
     LEFT JOIN pg_attrdef ad ON ad.adrelid = a.attrelid AND ad.adnum = a.attnum
     WHERE a.attrelid = $1::regclass AND a.attnum > 0 AND NOT a.attisdropped
     ORDER BY a.attnum`,
    [`${table.nspname}.${table.relname}`]
  );
  const parts = columns.rows.map((column) => {
    const def = column.default_expr ? ` DEFAULT ${column.default_expr}` : '';
    const notNull = column.attnotnull ? ' NOT NULL' : '';
    return `  ${column.attname} ${column.type}${def}${notNull}`;
  });
  lines.push(`CREATE TABLE IF NOT EXISTS ${table.nspname}.${table.relname} (`);
  lines.push(parts.join(',\n'));
  lines.push(');');
  const constraints = await client.query(
    `SELECT pg_get_constraintdef(oid) AS def, conname
     FROM pg_constraint
     WHERE conrelid = $1::regclass AND contype IN ('p','u','c')
     ORDER BY contype, conname`,
    [`${table.nspname}.${table.relname}`]
  );
  for (const constraint of constraints.rows) {
    lines.push(`ALTER TABLE ${table.nspname}.${table.relname} ADD CONSTRAINT ${constraint.conname} ${constraint.def};`);
  }
  lines.push('');
}

const foreignKeys = await client.query(
  `SELECT n.nspname, c.relname, con.conname, pg_get_constraintdef(con.oid) AS def
   FROM pg_constraint con
   JOIN pg_class c ON c.oid = con.conrelid
   JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = ANY($1) AND con.contype = 'f'
   ORDER BY 1, 2, 3`,
  [schemas]
);
for (const key of foreignKeys.rows) {
  lines.push(`ALTER TABLE ${key.nspname}.${key.relname} ADD CONSTRAINT ${key.conname} ${key.def};`);
}

const out = new URL('../../database/schema/app-schemas.sql', import.meta.url);
fs.mkdirSync(new URL('../../database/schema/', import.meta.url), { recursive: true });
fs.writeFileSync(out, lines.join('\n'));
console.log(JSON.stringify({ tables: tables.rows.length, foreignKeys: foreignKeys.rows.length, bytes: Buffer.byteLength(lines.join('\n')) }));
await client.end();
