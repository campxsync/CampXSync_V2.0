import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

function getEnvVar(varName) {
  let val = process.env[varName];
  if (!val) {
    try {
      const envPath = path.resolve('frontend/.env.local');
      if (fs.existsSync(envPath)) {
        const content = fs.readFileSync(envPath, 'utf8');
        const match = content.match(new RegExp(`^${varName}=(.+)`, 'm'));
        if (match) {
          val = match[1].trim().replace(/^['"]|['"]$/g, '');
        }
      }
    } catch {}
  }
  return val;
}

const varsToSync = [
  { name: 'SUPABASE_JDBC_URL', sensitive: true, val: getEnvVar('SUPABASE_JDBC_URL') },
  { name: 'SUPABASE_USER', sensitive: true, val: getEnvVar('SUPABASE_USER') },
  { name: 'SUPABASE_PASSWORD', sensitive: true, val: getEnvVar('SUPABASE_PASSWORD') },
  { name: 'NEXT_PUBLIC_SUPABASE_URL', sensitive: false, val: 'https://azulbaxszsqxymqmlwfe.supabase.co' },
  { name: 'NEXT_PUBLIC_SUPABASE_ANON_KEY', sensitive: false, val: 'sb_publishable_lwCQ-2xYqbKRsS7ki3FzEw_xK_YE69V' },
];

console.log('=== SYNCING VERCEL ENVIRONMENT VARIABLES (PRODUCTION & PREVIEW) ===');

for (const v of varsToSync) {
  if (!v.val) {
    console.error(`[ERROR] Variable ${v.name} has no value in local environment!`);
    continue;
  }
  console.log(`Setting ${v.name} for production,preview...`);
  try {
    const sensitiveFlag = v.sensitive ? '--sensitive' : '--no-sensitive';
    const envs = ['production', 'preview'];
    for (const env of envs) {
      execSync(
        `npx vercel env add ${v.name} --environment ${env} --value "${v.val}" --force ${sensitiveFlag} --yes`,
        { stdio: 'pipe' }
      );
      console.log(`  [PASS] ${v.name} synced to ${env}`);
    }
  } catch (err) {
    console.error(`  [FAIL] ${v.name}:`, err?.message || err);
  }
}

console.log('\nEnvironment variables sync completed.');

