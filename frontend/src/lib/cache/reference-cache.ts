/**
 * In-memory short-TTL cache for slow-changing reference data (departments, programs, document types)
 * with tag-based invalidation on write operations (Sprint Item 5).
 */

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

const globalForRef = globalThis as unknown as { __campx_ref_cache: Map<string, CacheEntry<any>> | undefined };
if (!globalForRef.__campx_ref_cache) {
  globalForRef.__campx_ref_cache = new Map<string, CacheEntry<any>>();
}
const REF_CACHE = globalForRef.__campx_ref_cache;

const DEFAULT_TTL_MS = 60000; // 60 seconds

export function getCachedReference<T>(key: string): T | null {
  const entry = REF_CACHE.get(key);
  if (entry && Date.now() < entry.expiresAt) {
    return entry.data as T;
  }
  return null;
}

export function setCachedReference<T>(key: string, data: T, ttlMs = DEFAULT_TTL_MS): void {
  REF_CACHE.set(key, { data, expiresAt: Date.now() + ttlMs });
}

export function invalidateReferenceTag(tag: string): void {
  for (const key of REF_CACHE.keys()) {
    if (key.includes(tag)) {
      REF_CACHE.delete(key);
    }
  }
}
