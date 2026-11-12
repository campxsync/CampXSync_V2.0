/**
 * In-Memory Caching for Student Domain Profiles & Operational Queries
 * 
 * Provides short-lived (60s-120s TTL) memory caching for student profiles and dashboards
 * to eliminate repeated multi-query WAN waterfalls to remote Supabase (AWS Mumbai)
 * during student navigations and audits.
 */

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

const globalForStudentCache = globalThis as unknown as {
  __campx_student_profile_cache?: Map<string, CacheEntry<any>>;
  __campx_student_attendance_cache?: Map<string, CacheEntry<any>>;
};

if (!globalForStudentCache.__campx_student_profile_cache) {
  globalForStudentCache.__campx_student_profile_cache = new Map();
}
if (!globalForStudentCache.__campx_student_attendance_cache) {
  globalForStudentCache.__campx_student_attendance_cache = new Map();
}

export function getCachedStudentProfile<T = any>(userId: string): T | null {
  const cache = globalForStudentCache.__campx_student_profile_cache!;
  const entry = cache.get(userId);
  if (entry && entry.expiresAt > Date.now()) {
    return entry.data as T;
  }
  return null;
}

export function setCachedStudentProfile<T = any>(userId: string, data: T, ttlSeconds = 120): void {
  const cache = globalForStudentCache.__campx_student_profile_cache!;
  cache.set(userId, {
    data,
    expiresAt: Date.now() + ttlSeconds * 1000,
  });
}

export function getCachedStudentAttendance<T = any>(studentId: string): T | null {
  const cache = globalForStudentCache.__campx_student_attendance_cache!;
  const entry = cache.get(studentId);
  if (entry && entry.expiresAt > Date.now()) {
    return entry.data as T;
  }
  return null;
}

export function setCachedStudentAttendance<T = any>(studentId: string, data: T, ttlSeconds = 60): void {
  const cache = globalForStudentCache.__campx_student_attendance_cache!;
  cache.set(studentId, {
    data,
    expiresAt: Date.now() + ttlSeconds * 1000,
  });
}

export function invalidateStudentCache(userId?: string, studentId?: string): void {
  if (userId) {
    globalForStudentCache.__campx_student_profile_cache?.delete(userId);
  } else {
    globalForStudentCache.__campx_student_profile_cache?.clear();
  }

  if (studentId) {
    globalForStudentCache.__campx_student_attendance_cache?.delete(studentId);
  } else {
    globalForStudentCache.__campx_student_attendance_cache?.clear();
  }
}

