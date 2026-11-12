// scripts/dev/dump_schema.mjs
// Read-only schema introspection snapshot tool for CampXSync (Dev Supabase: azulbaxszsqxymqmlwfe)
import pg from '../../frontend/node_modules/pg/lib/index.js';
const { Client } = pg;
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

const SCHEMAS = ['doc', 'stu', 'acd', 'core', 'iam', 'plat'];
const OUTPUT_PATH = path.resolve(__dirname, '../../docs/engineering/DB_SCHEMA_SNAPSHOT.md');

async function main() {
  console.log('Connecting to dev Supabase (azulbaxszsqxymqmlwfe) for schema snapshot...');
  const client = new Client(DB_CONFIG);
  await client.connect();

  try {
    let md = `# Database Schema Snapshot (Dev: azulbaxszsqxymqmlwfe)\n\n`;
    md += `*Generated automatically on ${new Date().toISOString()} via \`scripts/dev/dump_schema.mjs\`*\n`;
    md += `*RULE: Consult this document before writing any JDBC repository queries or migrations.*\n\n`;

    for (const schema of SCHEMAS) {
      console.log(`Introspecting schema: ${schema}...`);
      md += `## Schema: \`${schema}\`\n\n`;

      // 1. Tables
      const tablesRes = await client.query(`
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = $1 AND table_type = 'BASE TABLE'
        ORDER BY table_name;
      `, [schema]);

      if (tablesRes.rows.length === 0) {
        md += `*No base tables found in schema \`${schema}\`.*\n\n`;
        continue;
      }

      for (const tableRow of tablesRes.rows) {
        const table = tableRow.table_name;
        md += `### Table: \`${schema}.${table}\`\n\n`;

        // Columns
        const colsRes = await client.query(`
          SELECT 
            column_name, 
            data_type, 
            is_nullable, 
            column_default,
            character_maximum_length
          FROM information_schema.columns
          WHERE table_schema = $1 AND table_name = $2
          ORDER BY ordinal_position;
        `, [schema, table]);

        md += `#### Columns\n`;
        md += `| Column | Type | Nullable | Default |\n`;
        md += `|---|---|---|---|\n`;
        for (const col of colsRes.rows) {
          const typeStr = col.character_maximum_length ? `${col.data_type}(${col.character_maximum_length})` : col.data_type;
          md += `| \`${col.column_name}\` | \`${typeStr}\` | ${col.is_nullable === 'YES' ? 'YES' : '**NO**'} | ${col.column_default ? `\`${col.column_default}\`` : '—'} |\n`;
        }
        md += `\n`;

        // Constraints (PK, FK, Unique, Check)
        const constRes = await client.query(`
          SELECT 
            conname AS constraint_name,
            contype AS constraint_type,
            pg_get_constraintdef(c.oid) AS definition
          FROM pg_constraint c
          JOIN pg_namespace n ON n.oid = c.connamespace
          JOIN pg_class cl ON cl.oid = c.conrelid
          WHERE n.nspname = $1 AND cl.relname = $2
          ORDER BY contype, conname;
        `, [schema, table]);

        if (constRes.rows.length > 0) {
          md += `#### Constraints\n`;
          md += `| Name | Type | Definition |\n`;
          md += `|---|---|---|\n`;
          for (const con of constRes.rows) {
            const typeLabel = con.constraint_type === 'p' ? 'PRIMARY KEY'
              : con.constraint_type === 'f' ? 'FOREIGN KEY'
              : con.constraint_type === 'u' ? 'UNIQUE'
              : con.constraint_type === 'c' ? 'CHECK' : con.constraint_type;
            md += `| \`${con.constraint_name}\` | ${typeLabel} | \`${con.definition}\` |\n`;
          }
          md += `\n`;
        }

        // Triggers (flag append-only / no-delete triggers)
        const trigRes = await client.query(`
          SELECT 
            trigger_name,
            action_timing,
            event_manipulation,
            action_statement
          FROM information_schema.triggers
          WHERE event_object_schema = $1 AND event_object_table = $2
          ORDER BY trigger_name;
        `, [schema, table]);

        if (trigRes.rows.length > 0) {
          md += `#### Triggers & Append-Only Flags\n`;
          md += `| Trigger | Timing | Event | Flag / Action |\n`;
          md += `|---|---|---|---|\n`;
          for (const trig of trigRes.rows) {
            const isNoDelete = trig.event_manipulation.includes('DELETE') || trig.trigger_name.toLowerCase().includes('prevent_delete') || trig.trigger_name.toLowerCase().includes('append_only');
            const alertTag = isNoDelete ? '⚠️ **NO-DELETE / APPEND-ONLY**' : 'Standard';
            md += `| \`${trig.trigger_name}\` | ${trig.action_timing} | ${trig.event_manipulation} | ${alertTag} |\n`;
          }
          md += `\n`;
        }

        // RLS Policies
        const rlsRes = await client.query(`
          SELECT 
            policyname,
            cmd,
            roles,
            qual,
            with_check
          FROM pg_policies
          WHERE schemaname = $1 AND tablename = $2
          ORDER BY policyname;
        `, [schema, table]);

        if (rlsRes.rows.length > 0) {
          md += `#### Row Level Security (RLS) Policies\n`;
          md += `| Policy | Command | Roles | Expression |\n`;
          md += `|---|---|---|---|\n`;
          for (const pol of rlsRes.rows) {
            md += `| \`${pol.policyname}\` | \`${pol.cmd}\` | \`${pol.roles}\` | \`${pol.qual || pol.with_check || '—'}\` |\n`;
          }
          md += `\n`;
        }

        // Indexes
        const idxRes = await client.query(`
          SELECT indexname, indexdef
          FROM pg_indexes
          WHERE schemaname = $1 AND tablename = $2
          ORDER BY indexname;
        `, [schema, table]);

        if (idxRes.rows.length > 0) {
          md += `#### Indexes\n`;
          md += `| Index Name | Definition |\n`;
          md += `|---|---|\n`;
          for (const idx of idxRes.rows) {
            md += `| \`${idx.indexname}\` | \`${idx.indexdef}\` |\n`;
          }
          md += `\n`;
        }

        md += `---\n\n`;
      }
    }

    fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
    fs.writeFileSync(OUTPUT_PATH, md, 'utf-8');
    console.log(`[+] Schema snapshot saved successfully to: ${OUTPUT_PATH}`);

  } finally {
    await client.end();
  }
}

main().catch(err => {
  console.error('Fatal schema snapshot error:', err);
  process.exit(1);
});
