const DEPLOYED_URL = 'https://campxsync-web-malvjaroe-camp-x-java-integration.vercel.app';

async function main() {
  console.log('=== INVESTIGATING DEPLOYED VERCEL DEPLOYMENT ===');
  console.log(`Target: ${DEPLOYED_URL}\n`);

  // 1. Check /login
  const loginRes = await fetch(`${DEPLOYED_URL}/login`);
  const loginHtml = await loginRes.text();
  console.log(`[1] GET /login status: ${loginRes.status}`);

  // Find any scripts
  const scriptMatches = [...loginHtml.matchAll(/src="(\/_next\/static\/chunks\/[^"]+)"/g)].map(m => m[1]);
  console.log(`Found ${scriptMatches.length} script chunks in /login.`);

  for (const src of scriptMatches) {
    const jsRes = await fetch(`${DEPLOYED_URL}${src}`);
    const jsText = await jsRes.text();
    const supaUrls = jsText.match(/https:\/\/[a-z0-9]+\.supabase\.co/g);
    if (supaUrls) {
      console.log(`Chunk ${src} mentions Supabase URL:`, [...new Set(supaUrls)]);
    }
  }

  // 2. Check /api/auth/resolve
  console.log('\n[2] Probing /api/auth/resolve unauthenticated...');
  const resolveRes = await fetch(`${DEPLOYED_URL}/api/auth/resolve`);
  console.log(`GET /api/auth/resolve status: ${resolveRes.status}`);
  const resolveText = await resolveRes.text();
  console.log('Response headers:', Object.fromEntries(resolveRes.headers.entries()));
  console.log('Response text (first 500 chars):', resolveText.slice(0, 500));
}

main().catch(console.error);
