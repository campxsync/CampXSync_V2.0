import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { EnrollmentClient } from './enrollment-client';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function StudentEnrollmentPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'student') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId, collegeId } = resolution.session;
  if (!tenantId || !userId) {
    redirect('/access-denied?reason=forbidden');
  }

  let initialEnrolments: any[] = [];
  let initialAlerts: any[] = [];
  let initialCourses: any[] = [];
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

    const [enrolRes, alertsRes] = await Promise.all([
      fetch(`${GATEWAY_BASE_URL}/api/v1/enrollment/enrolments`, {
        headers: gwHeaders,
        cache: 'no-store',
      }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/enrollment/alerts`, {
        headers: gwHeaders,
        cache: 'no-store',
      }).catch(() => null),
    ]);

    if (enrolRes && enrolRes.ok) {
      const enrolJson = await enrolRes.json();
      initialEnrolments = enrolJson.enrolments || [];
    }
    if (alertsRes && alertsRes.ok) {
      const alertsJson = await alertsRes.json();
      initialAlerts = alertsJson.alerts || [];
    }

    if (initialEnrolments.length > 0) {
      const activeId = initialEnrolments[0].id;
      const [catRes, histRes] = await Promise.all([
        fetch(`${GATEWAY_BASE_URL}/api/v1/enrollment/enrolments/${activeId}/catalogue`, {
          headers: gwHeaders,
          cache: 'no-store',
        }).catch(() => null),
        fetch(`${GATEWAY_BASE_URL}/api/v1/enrollment/enrolments/${activeId}/history`, {
          headers: gwHeaders,
          cache: 'no-store',
        }).catch(() => null),
      ]);
      if (catRes && catRes.ok) {
        const catJson = await catRes.json();
        initialCourses = catJson.courses || [];
      }
      if (histRes && histRes.ok) {
        const histJson = await histRes.json();
        initialHistory = histJson.history || [];
      }
    }
  } catch (_e) {
    // Client component falls back gracefully
  }

  return (
    <EnrollmentClient
      initialEnrolments={initialEnrolments}
      initialAlerts={initialAlerts}
      initialCourses={initialCourses}
      initialHistory={initialHistory}
    />
  );
}
