import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { AddressClient } from './address-client';
import { getCachedStudentProfile, setCachedStudentProfile } from '@/lib/cache/student-profile-cache';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function StudentAddressPage() {
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
  if (!initialProfile) {
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
      const gwRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
        headers: gwHeaders,
        cache: 'no-store',
      });
      if (gwRes.ok) {
        initialProfile = await gwRes.json();
        setCachedStudentProfile(userId, initialProfile);
      }
    } catch (_e) {
      // If gateway is down, AddressClient will handle fallback on client or show form
    }
  }

  return <AddressClient initialProfile={initialProfile} />;
}
