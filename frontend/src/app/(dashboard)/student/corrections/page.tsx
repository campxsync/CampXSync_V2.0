import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { CorrectionsClient } from './corrections-client';
import { getCachedStudentProfile, setCachedStudentProfile } from '@/lib/cache/student-profile-cache';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function StudentCorrectionsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'student') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId, collegeId } = resolution.session;
  if (!tenantId || !userId) {
    redirect('/access-denied?reason=forbidden');
  }

  let initialProfile: any = getCachedStudentProfile(userId);
  let initialCorrections: any[] = [];
  let initialHistory: any[] = [];

  try {
    const gwHeaders: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId,
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'STUDENT',
    };
    if (collegeId) {
      gwHeaders['X-College-Id'] = collegeId;
    }

    if (!initialProfile) {
      const profileRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
        headers: gwHeaders,
        cache: 'no-store',
      });

      if (profileRes.ok) {
        initialProfile = await profileRes.json();
        setCachedStudentProfile(userId, initialProfile);
      }
    }

    const studentId = initialProfile?.id;
    if (studentId) {
      const [corRes, histRes] = await Promise.all([
        fetch(`${GATEWAY_BASE_URL}/api/v1/students/${studentId}/corrections`, {
          headers: gwHeaders,
          cache: 'no-store',
        }).catch(() => null),
        fetch(`${GATEWAY_BASE_URL}/api/v1/students/${studentId}/history`, {
          headers: gwHeaders,
          cache: 'no-store',
        }).catch(() => null),
      ]);

      if (corRes && corRes.ok) {
        const corData = await corRes.json();
        initialCorrections = Array.isArray(corData) ? corData : [];
      }
      if (histRes && histRes.ok) {
        const histData = await histRes.json();
        initialHistory = Array.isArray(histData) ? histData : [];
      }
    }
  } catch (_e) {
    // If gateway unavailable, client component will fallback gracefully
  }

  return (
    <CorrectionsClient
      initialProfile={initialProfile}
      initialCorrections={initialCorrections}
      initialHistory={initialHistory}
    />
  );
}
