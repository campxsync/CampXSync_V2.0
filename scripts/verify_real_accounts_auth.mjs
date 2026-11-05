import fs from 'node:fs';
import path from 'node:path';

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

const SUPABASE_URL = 'https://azulbaxszsqxymqmlwfe.supabase.co';
const ANON_KEY = 'sb_publishable_lwCQ-2xYqbKRsS7ki3FzEw_xK_YE69V';

const accounts = [
  { role: 'College Admin', email: 'campxsync@gmail.com', pass: getEnvVar('CAMPX_STAGING_TEST_PASSWORD') },
  { role: 'Platform Admin', email: 'platformadmin.campx@gmail.com', pass: getEnvVar('CAMPX_STAGING_PLATFORM_ADMIN_TEST_PASSWORD') },
  { role: 'Faculty', email: 'faculty.campx@gmail.com', pass: getEnvVar('CAMPX_STAGING_FACULTY_TEST_PASSWORD') },
];

async function check() {
  console.log('Testing Real Supabase Authentication for 3 Authorized Roles...');
  for (const acc of accounts) {
    if (!acc.pass) {
      console.log(`[FAIL] ${acc.role}: Missing password in environment`);
      continue;
    }
    const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: ANON_KEY,
      },
      body: JSON.stringify({ email: acc.email, password: acc.pass }),
    });
    const data = await res.json();
    if (res.ok && data.user) {
      console.log(`[PASS] ${acc.role} (${acc.email}): AUTH_SUCCESS (User UUID: ${data.user.id})`);
    } else {
      console.log(`[FAIL] ${acc.role} (${acc.email}): ${data.error_description || data.msg || JSON.stringify(data)}`);
    }
  }
}

check();

