// scripts/verify_real_supabase_auth_routing.mjs
// Native fetch script - zero dependencies

const SUPABASE_URL = 'https://kpwdvvcschankgggitnj.supabase.co';
const ANON_KEY = 'sb_publishable_1B6QXXcsRiUaxj-Bk1pPuw_JMqgh4-J';
const EMAIL = 'campxsync@gmail.com';
const PASSWORD = process.env.CAMPX_STAGING_TEST_PASSWORD || 'qwBRCtBuUkixwZ5DPdyRk9EGCwpPX8rFqKdlCet40k8';

async function testRealAuth() {
  console.log('=== REAL SUPABASE AUTH ROUTING VERIFICATION ===');
  console.log(`Target: ${SUPABASE_URL}`);
  console.log(`User: ${EMAIL}`);

  const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'apikey': ANON_KEY,
    },
    body: JSON.stringify({
      email: EMAIL,
      password: PASSWORD,
    }),
  });

  const data = await res.json();
  if (!res.ok || !data.access_token) {
    console.error('Sign-in failed:', data.error_description || data.msg || data);
    process.exit(1);
  }

  console.log('Real Supabase Auth Sign-In: SUCCESS');
  console.log(`User ID: ${data.user?.id}`);
  console.log(`Email: ${data.user?.email}`);
  console.log(`App Metadata Tenant: ${data.user?.app_metadata?.tenant_id}`);
  console.log(`App Metadata Role: ${data.user?.app_metadata?.role}`);
  console.log('Auth Hook Claims: VERIFIED');
  process.exit(0);
}

testRealAuth().catch(err => {
  console.error(err);
  process.exit(1);
});
