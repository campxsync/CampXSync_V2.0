// scripts/verify_deployed_login.mjs
// Probe live deployed login page chunks and verify real login flow

const BASE_URL = 'https://frontend-murex-one-58.vercel.app';

async function main() {
  console.log(`=== VERIFYING DEPLOYED LOGIN BUNDLE (${BASE_URL}) ===`);
  const res = await fetch(`${BASE_URL}/login`, { cache: 'no-store' });
  console.log(`HTTP Status: ${res.status}`);
  const html = await res.text();

  // Find all chunk URLs in HTML
  const matches = [...html.matchAll(/src="(\/_next\/static\/chunks\/[^"]+)"/g)];
  const chunkPaths = matches.map(m => m[1]);
  console.log(`Found ${chunkPaths.length} static chunk scripts referenced.`);

  let foundPlatformAdminEmail = false;
  let foundStagingAccounts = false;
  let foundTestSessionCallInLogin = false;

  for (const p of chunkPaths) {
    const chunkRes = await fetch(`${BASE_URL}${p}`, { cache: 'no-store' });
    const text = await chunkRes.text();
    if (text.includes('platformadmin.campx@gmail.com')) {
      foundPlatformAdminEmail = true;
      console.log(`[PASS] Verified platformadmin.campx@gmail.com in chunk: ${p}`);
    }
    if (text.includes('Staging Accounts')) {
      foundStagingAccounts = true;
      console.log(`[PASS] Verified 'Staging Accounts' label in chunk: ${p}`);
    }
    if (text.includes('handleQuickDemo') || text.includes('/api/auth/test-session')) {
      // Topbar or access-denied might have DELETE /api/auth/test-session for clearing cookies
      if (text.includes('Trial test session endpoint')) {
        foundTestSessionCallInLogin = true;
      }
    }
  }

  console.log('\n--- VERIFICATION FINDINGS ---');
  console.log(`Staging Platform Admin Email in Bundle: ${foundPlatformAdminEmail ? 'VERIFIED' : 'FAILED'}`);
  console.log(`'Staging Accounts' UI Selector in Bundle: ${foundStagingAccounts ? 'VERIFIED' : 'FAILED'}`);

  if (foundPlatformAdminEmail && foundStagingAccounts) {
    console.log('\nSUCCESS: Deployed login page bundle contains the hardened credentials selector!');
  } else {
    console.error('\nFAILED: Deployed bundle does not reflect the expected changes.');
    process.exit(1);
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
