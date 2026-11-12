import crypto from 'crypto';

/**
 * Local Cryptographic Supabase JWT Verifier (ECC P-256 / ES256)
 * 
 * Verifies Supabase Auth access tokens locally using project JWKS public key
 * without traversing the network to GoTrue (auth.getUser()) on every navigation.
 * 
 * Security trade-off:
 * - Pro: Eliminates 80-150 ms network RTT to Supabase GoTrue on every page request.
 * - Pro: Cryptographically tamper-proof via ES256 asymmetric signature verification.
 * - Con: Does not detect instant server-side revocation prior to token expiration (exp).
 *   Mitigation: Short access token TTLs (default 1 hour) and immediate cache eviction on logout.
 *   Authoritative PostgreSQL checks remain enforced for write operations and sensitive mutations.
 */

// Active public key from https://azulbaxszsqxymqmlwfe.supabase.co/auth/v1/.well-known/jwks.json
const FALLBACK_JWK = {
  alg: 'ES256',
  crv: 'P-256',
  kty: 'EC',
  use: 'sig',
  kid: 'd84bdcbe-3f22-404a-8d66-ce46fcda2c17',
  x: '_nmxRbvNcBarfO_aywPw2iw2rSnBEN9tqBT1UG-d7LI',
  y: 'baztOo3fgKvc4CKKFOuiK99oF5vNTDXaWwAccN0RCqg',
};

let cachedPublicKey: crypto.KeyObject | null = null;

function getPublicKey(): crypto.KeyObject {
  if (!cachedPublicKey) {
    cachedPublicKey = crypto.createPublicKey({ format: 'jwk', key: FALLBACK_JWK });
  }
  return cachedPublicKey;
}

export interface VerifiedJwtClaims {
  sub: string;
  email?: string;
  role?: string;
  aud?: string | string[];
  app_metadata?: {
    role?: string;
    tenant_id?: string;
    membership_status?: string;
    [key: string]: any;
  };
  user_metadata?: Record<string, any>;
  exp: number;
  iat: number;
  iss?: string;
}

export interface VerifyJwtOptions {
  expectedIssuer?: string;
  expectedAudience?: string;
}

/**
 * Decodes base64url string to Buffer.
 */
function base64UrlDecode(str: string): Buffer {
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4) {
    base64 += '=';
  }
  return Buffer.from(base64, 'base64');
}

/**
 * Verifies an incoming Supabase JWT token locally using ECC P-256 public key.
 */
export function verifySupabaseJwtLocally(
  token: string,
  options?: VerifyJwtOptions
): { valid: boolean; claims?: VerifiedJwtClaims; error?: string } {
  if (!token || typeof token !== 'string') {
    return { valid: false, error: 'Empty or invalid token' };
  }

  const parts = token.trim().split('.');
  if (parts.length !== 3) {
    return { valid: false, error: 'Malformed JWT structure' };
  }

  const [headerB64, payloadB64, sigB64] = parts;

  try {
    // 1. Verify header
    const header = JSON.parse(base64UrlDecode(headerB64).toString('utf8'));
    if (!header || typeof header !== 'object') {
      return { valid: false, error: 'Invalid header' };
    }
    if (header.alg !== 'ES256') {
      return { valid: false, error: `Unsupported algorithm: ${header.alg}` };
    }
    if (header.kid && header.kid !== FALLBACK_JWK.kid) {
      return { valid: false, error: `Unknown kid: ${header.kid}` };
    }

    // 2. Verify payload and claims
    const payload: VerifiedJwtClaims = JSON.parse(base64UrlDecode(payloadB64).toString('utf8'));
    const nowSec = Math.floor(Date.now() / 1000);
    if (payload.exp && payload.exp < nowSec) {
      return { valid: false, error: 'Token expired' };
    }

    // Issuer verification
    if (options?.expectedIssuer && payload.iss !== options.expectedIssuer) {
      return { valid: false, error: `Invalid issuer: expected ${options.expectedIssuer}, got ${payload.iss}` };
    }

    // Audience verification
    if (options?.expectedAudience) {
      const audMatches = Array.isArray(payload.aud)
        ? payload.aud.includes(options.expectedAudience)
        : payload.aud === options.expectedAudience;
      if (!audMatches) {
        return { valid: false, error: `Invalid audience: expected ${options.expectedAudience}, got ${payload.aud}` };
      }
    }

    // 3. Cryptographic signature verification (IEEE P1363 raw 64-byte format)
    const dataToVerify = Buffer.from(`${headerB64}.${payloadB64}`, 'utf8');
    const signatureBuffer = base64UrlDecode(sigB64);
    const pubKey = getPublicKey();

    const isVerified = crypto.verify(
      'SHA256',
      dataToVerify,
      { key: pubKey, dsaEncoding: 'ieee-p1363' },
      signatureBuffer
    );

    if (!isVerified) {
      return { valid: false, error: 'Invalid cryptographic signature' };
    }

    return { valid: true, claims: payload };
  } catch (err: any) {
    return { valid: false, error: err.message || 'Verification failure' };
  }
}

/**
 * Extracts raw Supabase access token string from Next.js cookie store.
 */
export function extractAccessTokenFromCookies(cookieStore: { getAll: () => Array<{ name: string; value: string }> }): string | null {
  const allCookies = cookieStore.getAll();
  
  // 1. Check for standard or chunked sb-*-auth-token cookies
  const authChunks = allCookies
    .filter(c => c.name.startsWith('sb-') && c.name.includes('-auth-token'))
    .sort((a, b) => a.name.localeCompare(b.name));

  if (authChunks.length > 0) {
    const rawVal = authChunks.map(c => c.value).join('');
    try {
      const parsed = JSON.parse(rawVal);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed[0]; // [access_token, refresh_token]
      }
      if (parsed && typeof parsed.access_token === 'string') {
        return parsed.access_token;
      }
    } catch {
      // In case cookie stores the raw JWT directly
      if (rawVal.split('.').length === 3) {
        return rawVal;
      }
    }
  }

  return null;
}
