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

const DEPLOYED_BASE = 'https://campxsync-web-dev.vercel.app';
const SUPABASE_URL = 'https://azulbaxszsqxymqmlwfe.supabase.co';
const ANON_KEY = 'sb_publishable_lwCQ-2xYqbKRsS7ki3FzEw_xK_YE69V';
const EMAIL = 'campxsync@gmail.com';
const PASSWORD = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');

async function testDeployedAuth() {
  console.log('=== TESTING REAL DEPLOYED AUTH RESOLUTION ===');
  console.log(`Target: ${DEPLOYED_BASE}`);
  console.log(`Authenticating as: ${EMAIL}`);

  // Step 1: Sign in with Supabase Auth
  const authRes = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: ANON_KEY,
    },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });

  const authData = await authRes.json();
  if (!authRes.ok || !authData.access_token) {
    console.error('Sign-in failed:', authData);
    process.exit(1);
  }
  console.log('Supabase GoTrue Auth: SUCCESS');
  console.log(`User ID: ${authData.user.id}`);

  // Construct cookies as @supabase/ssr expects them
  // Supabase ssr uses: sb-<project-ref>-auth-token
  const projectRef = 'azulbaxszsqxymqmlwfe';
  const cookieName = `sb-${projectRef}-auth-token`;
  const sessionData = JSON.stringify([authData.access_token, authData.refresh_token]);
  const cookieHeader = `${cookieName}=${encodeURIComponent(sessionData)}`;

  console.log(`Calling ${DEPLOYED_BASE}/api/auth/resolve with cookies...`);
  const resolveRes = await fetch(`${DEPLOYED_BASE}/api/auth/resolve`, {
    headers: {
      Cookie: cookieHeader,
    },
  });

  console.log(`Status: ${resolveRes.status}`);
  const resolveJson = await resolveRes.json();
  console.log('Response JSON:', resolveJson);
}

testDeployedAuth().catch(console.error);

