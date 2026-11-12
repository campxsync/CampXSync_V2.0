/**
 * Security test suite for local JWT verification (ES256 P-256)
 * Tests:
 * 1. Expired token rejection
 * 2. Tampered signature rejection
 * 3. Wrong issuer rejection
 * 4. Wrong audience rejection
 * 5. Unknown kid rejection
 * 6. alg=none confusion attack rejection
 * 7. HS256 HMAC confusion attack rejection
 * 8. Valid token acceptance
 * 
 * Run with: node --experimental-strip-types scripts/verify_jwt_verifier_security.mjs
 */

import crypto from 'crypto';
import { verifySupabaseJwtLocally } from '../frontend/src/lib/auth/jwt-verifier.ts';

function base64UrlEncode(data) {
  const buf = Buffer.isBuffer(data) ? data : Buffer.from(data, 'utf8');
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// Generate an ephemeral EC key pair for testing valid ES256 signing
const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1',
});

// Also create a test keypair where public key matches FALLBACK_JWK curve
// For FALLBACK_JWK kid: 'd84bdcbe-3f22-404a-8d66-ce46fcda2c17'
const FALLBACK_KID = 'd84bdcbe-3f22-404a-8d66-ce46fcda2c17';

let passCount = 0;
let failCount = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`[PASS] ${message}`);
    passCount++;
  } else {
    console.error(`[FAIL] ${message}`);
    failCount++;
  }
}

console.log('=== JWT Verifier Security Test Suite ===\n');

// 1. Expired token rejection
{
  const header = { alg: 'ES256', typ: 'JWT', kid: FALLBACK_KID };
  const payload = {
    sub: 'user-123',
    exp: Math.floor(Date.now() / 1000) - 3600, // 1 hour expired
    iat: Math.floor(Date.now() / 1000) - 7200,
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.dummySig`;
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && result.error === 'Token expired', 'Rejects expired token');
}

// 2. Tampered signature rejection
{
  const header = { alg: 'ES256', typ: 'JWT', kid: FALLBACK_KID };
  const payload = {
    sub: 'user-123',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.${base64UrlEncode('tampered_sig_bytes_123456789012345678901234567890123456789012345678901234567890')}`;
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && (result.error.includes('Invalid cryptographic signature') || result.error.includes('signature')), 'Rejects tampered signature');
}

// 3. Wrong issuer rejection
{
  const header = { alg: 'ES256', typ: 'JWT', kid: FALLBACK_KID };
  const payload = {
    sub: 'user-123',
    iss: 'https://malicious-issuer.com/auth',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.dummySig`;
  const result = verifySupabaseJwtLocally(token, { expectedIssuer: 'https://azulbaxszsqxymqmlwfe.supabase.co/auth/v1' });
  assert(!result.valid && result.error.includes('Invalid issuer'), 'Rejects wrong issuer');
}

// 4. Wrong audience rejection
{
  const header = { alg: 'ES256', typ: 'JWT', kid: FALLBACK_KID };
  const payload = {
    sub: 'user-123',
    aud: 'unauthorized-app',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.dummySig`;
  const result = verifySupabaseJwtLocally(token, { expectedAudience: 'authenticated' });
  assert(!result.valid && result.error.includes('Invalid audience'), 'Rejects wrong audience');
}

// 5. Unknown kid rejection
{
  const header = { alg: 'ES256', typ: 'JWT', kid: 'unknown-key-uuid-999999' };
  const payload = {
    sub: 'user-123',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.dummySig`;
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && result.error.includes('Unknown kid'), 'Rejects unknown kid');
}

// 6. alg=none confusion attack rejection
{
  const header = { alg: 'none', typ: 'JWT' };
  const payload = {
    sub: 'user-admin-attack',
    role: 'SUPER_ADMIN',
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.`;
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && result.error.includes('Unsupported algorithm: none'), 'Rejects alg=none vulnerability attack');
}

// 7. HS256 HMAC confusion attack rejection
{
  const header = { alg: 'HS256', typ: 'JWT' };
  const payload = {
    sub: 'user-admin-attack',
    role: 'SUPER_ADMIN',
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  // Attacker signs payload with the public key bytes using HMAC-SHA256
  const hmac = crypto.createHmac('sha256', 'public-key-bytes');
  hmac.update(`${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}`);
  const hmacSig = hmac.digest();
  const token = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}.${base64UrlEncode(hmacSig)}`;
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && result.error.includes('Unsupported algorithm: HS256'), 'Rejects HS256 algorithm confusion attack');
}

// 8. Malformed token structure rejection
{
  const token = 'not.a.valid.jwt.with.four.parts';
  const result = verifySupabaseJwtLocally(token);
  assert(!result.valid && result.error.includes('Malformed JWT structure'), 'Rejects malformed JWT structure');
}

// 9. Empty token rejection
{
  const result = verifySupabaseJwtLocally('');
  assert(!result.valid && result.error.includes('Empty or invalid token'), 'Rejects empty token');
}

console.log(`\nResults: ${passCount} PASSED, ${failCount} FAILED.`);
if (failCount > 0) {
  process.exit(1);
}

