import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  ArrowLeft, 
  Award, 
  CheckCircle2, 
  UserCheck, 
  AlertCircle, 
  RotateCw, 
  Clock, 
  GraduationCap,
  ExternalLink
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchApplications } from '@/lib/api/admissions';
import { HandoverTriggerButton } from './handover-trigger-button';

export const dynamic = 'force-dynamic';

export default async function OffersAndHandoverPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Fetch applications in OFFERED, ACCEPTED, and HANDED_OVER states
  const [offeredApps, acceptedApps, handedOverApps] = await Promise.all([
    fetchApplications(tenantId, userId, 'COLLEGE_ADMIN', { status: 'OFFERED' }, collegeId),
    fetchApplications(tenantId, userId, 'COLLEGE_ADMIN', { status: 'ACCEPTED' }, collegeId),
    fetchApplications(tenantId, userId, 'COLLEGE_ADMIN', { status: 'HANDED_OVER' }, collegeId),
  ]);

  const allOfferRelated = [...acceptedApps, ...offeredApps, ...handedOverApps];

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href="/college/admissions" className="inline-flex items-center text-xs font-semibold text-[#1F6FEB] hover:underline mb-2">
            <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Back to Admissions Hub
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Admission Offers & STM-01 Handover</h1>
            <Badge status="ACTIVE" label="STM-01 Integration" />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Track issued offers, accepted admissions, and trigger idempotent student identity creation in STM-01.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link href="/college/students">
            <Button variant="outline" size="sm">
              <GraduationCap className="mr-2 h-4 w-4" /> View Student Roster
            </Button>
          </Link>
        </div>
      </div>

      {/* Handover Architecture Callout */}
      <div className="flex items-start gap-3 rounded-[8px] border border-[#B2E6CE] bg-[#EAF8F1] p-4 text-sm text-[#15803D]">
        <CheckCircle2 className="h-5 w-5 shrink-0 mt-0.5 text-[#15803D]" />
        <div>
          <span className="font-semibold">Idempotent STM-01 Cross-Domain Handover (US-021, US-022 / ADR D-09):</span>
          <p className="text-xs text-[#166534] mt-0.5">
            Upon offer acceptance, admissions data triggers student identity registration in the STM-01 Student Domain. Handover transactions are guarded by unique idempotency keys, multi-tenant boundaries, and automated retry tracking.
          </p>
        </div>
      </div>

      {/* Offers & Handover Table */}
      <Card>
        <CardHeader>
          <CardTitle>Offers & Handover Lifecycle ({allOfferRelated.length})</CardTitle>
          <CardDescription>
            Applications with issued admission offers, accepted terms, or completed student enrolment handovers.
          </CardDescription>
        </CardHeader>

        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-[#E3E8EF] bg-[#F8FAFC] text-xs font-semibold uppercase text-[#475569]">
                <tr>
                  <th className="px-5 py-3">Application #</th>
                  <th className="px-5 py-3">Applicant Identity</th>
                  <th className="px-5 py-3">Lifecycle State</th>
                  <th className="px-5 py-3">Fee Status</th>
                  <th className="px-5 py-3">Handover Phase</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E3E8EF]">
                {allOfferRelated.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-[#6B7688]">
                      No active offers or student handovers currently recorded.
                    </td>
                  </tr>
                ) : (
                  allOfferRelated.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F8FAFC] transition-colors">
                      <td className="px-5 py-3 font-semibold text-[#0B1F3A]">
                        {app.applicationNumber}
                      </td>
                      <td className="px-5 py-3 font-mono text-xs text-[#475569]">
                        {app.applicantPersonId}
                      </td>
                      <td className="px-5 py-3">
                        <Badge 
                          status={app.status === 'HANDED_OVER' ? 'ACTIVE' : app.status === 'ACCEPTED' ? 'SUBMITTED' : 'OPEN'} 
                          label={app.status} 
                        />
                      </td>
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center text-xs font-medium text-[#15803D]">
                          {app.paymentStatus || 'PAID'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        {app.status === 'HANDED_OVER' ? (
                          <span className="inline-flex items-center text-xs font-semibold text-[#15803D]">
                            <CheckCircle2 className="mr-1 h-3.5 w-3.5" /> Enrolled in STM-01
                          </span>
                        ) : app.status === 'ACCEPTED' ? (
                          <span className="inline-flex items-center text-xs font-semibold text-[#1D4ED8]">
                            <Clock className="mr-1 h-3.5 w-3.5" /> Ready for Handover
                          </span>
                        ) : (
                          <span className="inline-flex items-center text-xs font-medium text-[#B45309]">
                            <Clock className="mr-1 h-3.5 w-3.5" /> Awaiting Applicant Acceptance
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {app.status === 'ACCEPTED' && (
                            <HandoverTriggerButton applicationId={app.id} />
                          )}
                          <Link href={`/college/admissions/applications/${app.id}`}>
                            <Button variant="outline" size="sm">
                              Details
                            </Button>
                          </Link>
                          {app.status === 'HANDED_OVER' && (
                            <Link href="/college/students">
                              <Button variant="ghost" size="sm" className="text-xs text-[#1F6FEB]">
                                View Roster <ExternalLink className="ml-1 h-3 w-3" />
                              </Button>
                            </Link>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
