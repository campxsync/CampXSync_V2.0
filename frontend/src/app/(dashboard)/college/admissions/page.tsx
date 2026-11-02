import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  Users, 
  FileText, 
  CheckCircle2, 
  Clock, 
  Award, 
  ArrowRight, 
  Search, 
  Filter, 
  TrendingUp, 
  Settings, 
  ExternalLink,
  ShieldCheck,
  UserCheck
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchApplications, fetchFunnelIntelligence, fetchActiveIntakes } from '@/lib/api/admissions';

export const dynamic = 'force-dynamic';

export default async function AdmissionsOfficerWorkspacePage({
  searchParams,
}: {
  searchParams?: Promise<{ status?: string; intakeId?: string; search?: string }>;
}) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  const resolvedParams = searchParams ? await searchParams : {};
  const selectedStatus = resolvedParams.status || '';
  const selectedIntakeId = resolvedParams.intakeId || '';
  const searchFilter = (resolvedParams.search || '').toLowerCase();

  // 1. Fetch live metrics from Admissions Intelligence and active applications
  const [applications, funnelMetrics, activeIntakes] = await Promise.all([
    fetchApplications(tenantId, userId, 'COLLEGE_ADMIN', {
      intakeId: selectedIntakeId || undefined,
      status: selectedStatus || undefined,
    }, collegeId),
    fetchFunnelIntelligence(tenantId, userId, 'COLLEGE_ADMIN', selectedIntakeId || null, collegeId),
    fetchActiveIntakes(tenantId, userId, 'COLLEGE_ADMIN', collegeId),
  ]);

  // Client-side search filtering on application number or person ID
  const filteredApps = applications.filter((app) => {
    if (!searchFilter) return true;
    return (
      app.applicationNumber.toLowerCase().includes(searchFilter) ||
      app.applicantPersonId.toLowerCase().includes(searchFilter) ||
      (app.applicantData && app.applicantData.toLowerCase().includes(searchFilter))
    );
  });

  // Calculate status summary counts
  const totalCount = applications.length;
  const submittedCount = applications.filter(a => a.status === 'SUBMITTED' || a.status === 'ELIGIBLE').length;
  const underReviewCount = applications.filter(a => a.status === 'UNDER_REVIEW').length;
  const offeredCount = applications.filter(a => a.status === 'OFFERED').length;
  const acceptedCount = applications.filter(a => a.status === 'ACCEPTED').length;
  const handedOverCount = applications.filter(a => a.status === 'HANDED_OVER').length;

  return (
    <div className="space-y-6 p-6">
      {/* Top Header & Fast Actions */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Admissions & Application Management</h1>
            <Badge status="ACTIVE" label="STM-02" />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Manage admission intakes, review applicant submissions, score rubrics, and orchestrate student handover.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link href="/college/admissions/intakes">
            <Button variant="outline" size="sm">
              <Settings className="mr-2 h-4 w-4" /> Intakes & Rules
            </Button>
          </Link>
          <Link href="/college/admissions/approvals">
            <Button variant="outline" size="sm">
              <ShieldCheck className="mr-2 h-4 w-4 text-[#1F6FEB]" /> Approver Queue
            </Button>
          </Link>
          <Link href="/college/admissions/offers">
            <Button variant="secondary" size="sm">
              <UserCheck className="mr-2 h-4 w-4 text-[#15803D]" /> Offers & Handover
            </Button>
          </Link>
        </div>
      </div>

      {/* 7.6 Operational Dashboard Metrics */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <StatCard
          title="Total Applications"
          value={funnelMetrics?.totalApplications ?? totalCount}
          icon={FileText}
          description="All admissions submissions"
        />
        <StatCard
          title="Pending Verification"
          value={submittedCount}
          icon={Clock}
          description="Awaiting document review"
        />
        <StatCard
          title="Under Review"
          value={funnelMetrics?.underReviewCount ?? underReviewCount}
          icon={Users}
          description="Department rubric scoring"
        />
        <StatCard
          title="Offers Issued"
          value={funnelMetrics?.offeredCount ?? offeredCount}
          icon={Award}
          description="Valid offers pending response"
        />
        <StatCard
          title="Offers Accepted"
          value={funnelMetrics?.acceptedCount ?? acceptedCount}
          icon={CheckCircle2}
          description="Ready for student handover"
        />
        <StatCard
          title="Enrolment Conversion"
          value={`${funnelMetrics?.conversionRatePercent ?? (totalCount > 0 ? ((acceptedCount + handedOverCount) / totalCount * 100).toFixed(1) : 0)}%`}
          icon={TrendingUp}
          description="Lead-to-student handover rate"
        />
      </div>

      {/* 7.3 Admissions Officer Application Queue */}
      <Card>
        <CardHeader>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Application Review Queue</CardTitle>
              <CardDescription>
                Server-side queue of candidate applications across academic programs and intake cycles.
              </CardDescription>
            </div>

            {/* Filters */}
            <div className="flex flex-wrap items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-2.5 h-4 w-4 text-[#6B7688]" />
                <form method="GET">
                  <input
                    type="text"
                    name="search"
                    defaultValue={resolvedParams.search || ''}
                    placeholder="Search application # or applicant..."
                    className="h-9 rounded-[8px] border border-[#E3E8EF] bg-white pl-9 pr-3 text-sm text-[#0B1F3A] focus:border-[#1F6FEB] focus:outline-none"
                  />
                  {selectedStatus && <input type="hidden" name="status" value={selectedStatus} />}
                  {selectedIntakeId && <input type="hidden" name="intakeId" value={selectedIntakeId} />}
                </form>
              </div>

              <div className="flex items-center gap-1 rounded-[8px] border border-[#E3E8EF] bg-[#F8FAFC] p-1 text-xs">
                <Link
                  href="/college/admissions"
                  className={`rounded-[6px] px-2.5 py-1 font-medium transition-colors ${
                    !selectedStatus ? 'bg-white text-[#1F6FEB] shadow-sm' : 'text-[#64748B] hover:text-[#0B1F3A]'
                  }`}
                >
                  All ({applications.length})
                </Link>
                <Link
                  href="/college/admissions?status=SUBMITTED"
                  className={`rounded-[6px] px-2.5 py-1 font-medium transition-colors ${
                    selectedStatus === 'SUBMITTED' ? 'bg-white text-[#1F6FEB] shadow-sm' : 'text-[#64748B] hover:text-[#0B1F3A]'
                  }`}
                >
                  Submitted
                </Link>
                <Link
                  href="/college/admissions?status=UNDER_REVIEW"
                  className={`rounded-[6px] px-2.5 py-1 font-medium transition-colors ${
                    selectedStatus === 'UNDER_REVIEW' ? 'bg-white text-[#1F6FEB] shadow-sm' : 'text-[#64748B] hover:text-[#0B1F3A]'
                  }`}
                >
                  In Review
                </Link>
                <Link
                  href="/college/admissions?status=OFFERED"
                  className={`rounded-[6px] px-2.5 py-1 font-medium transition-colors ${
                    selectedStatus === 'OFFERED' ? 'bg-white text-[#1F6FEB] shadow-sm' : 'text-[#64748B] hover:text-[#0B1F3A]'
                  }`}
                >
                  Offered
                </Link>
                <Link
                  href="/college/admissions?status=ACCEPTED"
                  className={`rounded-[6px] px-2.5 py-1 font-medium transition-colors ${
                    selectedStatus === 'ACCEPTED' ? 'bg-white text-[#1F6FEB] shadow-sm' : 'text-[#64748B] hover:text-[#0B1F3A]'
                  }`}
                >
                  Accepted
                </Link>
              </div>
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-[#E3E8EF] bg-[#F8FAFC] text-xs font-semibold uppercase text-[#475569]">
                <tr>
                  <th className="px-5 py-3">Application #</th>
                  <th className="px-5 py-3">Applicant Identity</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Documents</th>
                  <th className="px-5 py-3">Eligibility</th>
                  <th className="px-5 py-3">Fee Status</th>
                  <th className="px-5 py-3">Submitted</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E3E8EF]">
                {filteredApps.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="p-8 text-center text-[#6B7688]">
                      No admissions applications found matching your selected criteria.
                    </td>
                  </tr>
                ) : (
                  filteredApps.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F8FAFC] transition-colors">
                      <td className="px-5 py-3 font-semibold text-[#0B1F3A]">
                        {app.applicationNumber}
                      </td>
                      <td className="px-5 py-3 font-mono text-xs text-[#475569]">
                        {app.applicantPersonId}
                      </td>
                      <td className="px-5 py-3">
                        <Badge status={app.status} label={app.status} />
                      </td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center text-xs font-medium ${
                          app.documentVerificationStatus === 'VERIFIED' ? 'text-[#15803D]' :
                          app.documentVerificationStatus === 'REJECTED' ? 'text-[#B91C1C]' : 'text-[#B45309]'
                        }`}>
                          {app.documentVerificationStatus || 'PENDING'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center text-xs font-medium ${
                          app.eligibilityResult === 'PASS' || app.eligibilityResult === 'OVERRIDDEN' ? 'text-[#15803D]' :
                          app.eligibilityResult === 'FAIL' ? 'text-[#B91C1C]' : 'text-[#64748B]'
                        }`}>
                          {app.eligibilityResult || 'UNEVALUATED'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center text-xs font-medium ${
                          app.paymentStatus === 'PAID' || app.paymentStatus === 'WAIVED' ? 'text-[#15803D]' : 'text-[#B45309]'
                        }`}>
                          {app.paymentStatus || 'UNPAID'}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-xs text-[#64748B]">
                        {app.submissionTimestamp ? new Date(app.submissionTimestamp).toLocaleDateString() : 'Draft'}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Link href={`/college/admissions/applications/${app.id}`}>
                          <Button variant="outline" size="sm">
                            Review <ArrowRight className="ml-1 h-3.5 w-3.5" />
                          </Button>
                        </Link>
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

