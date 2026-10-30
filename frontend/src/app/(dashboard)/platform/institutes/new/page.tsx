import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { OnboardingWizard } from './onboarding-wizard';

export const dynamic = 'force-dynamic';

export default async function NewInstituteOnboardingPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  // Enforce server-side Platform Admin authorization
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Institutional Tenant Onboarding
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Provision a new college, university, or multi-campus institution into the multi-tenant architecture.
        </p>
      </div>

      <OnboardingWizard />
    </div>
  );
}
