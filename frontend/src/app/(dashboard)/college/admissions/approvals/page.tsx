import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  ArrowLeft, 
  ShieldCheck, 
  Award, 
  CheckCircle2, 
  XCircle, 
  AlertTriangle, 
  Clock, 
  User,
  ArrowRight
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchApplications, fetchActiveIntakes } from '@/lib/api/admissions';

export const dynamic = 'force-dynamic';

export default async function AdmissionsApproverWorkspacePage({
  searchParams,
}: {
  searchParams?: Promise<{ applicationId?: string }>;
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

  // Fetch applications in UNDER_REVIEW status ready for executive decision approval
  const pendingApprovals = await fetchApplications(
    tenantId, 
    userId, 
    'COLLEGE_ADMIN', 
    { status: 'UNDER_REVIEW' }, 
    collegeId
  );

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href="/college/admissions" className="inline-flex items-center text-xs font-semibold text-[#1F6FEB] hover:underline mb-2">
            <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Back to Admissions Hub
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Admissions Approver Workspace</h1>
            <Badge status="ACTIVE" label="Executive Authority" />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Review faculty rubric recommendations, verify separation-of-duties boundaries, and record binding admission decisions.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link href="/college/admissions/offers">
            <Button variant="outline" size="sm">
              View Issued Offers
            </Button>
          </Link>
        </div>
      </div>

      {/* Segregation of Duties Notice */}
      <div className="flex items-start gap-3 rounded-[8px] border border-[#B8D3FC] bg-[#E8F0FE] p-4 text-sm text-[#1D4ED8]">
        <ShieldCheck className="h-5 w-5 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">Segregation of Duties Enforcement (ADR D-04):</span>
          <p className="text-xs text-[#1E40AF] mt-0.5">
            Admissions decisions must be committed by an authorized Dean, Principal, or College Administrator. In accordance with governance rules, the assigned rubric reviewer who scored an application cannot approve the binding admission decision.
          </p>
        </div>
      </div>

      {/* Pending Approvals Table */}
      <Card>
        <CardHeader>
          <CardTitle>Pending Decision Approvals ({pendingApprovals.length})</CardTitle>
          <CardDescription>
            Candidate applications that have completed departmental rubric evaluations and await executive decision.
          </CardDescription>
        </CardHeader>

        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-[#E3E8EF] bg-[#F8FAFC] text-xs font-semibold uppercase text-[#475569]">
                <tr>
                  <th className="px-5 py-3">Application #</th>
                  <th className="px-5 py-3">Applicant Person</th>
                  <th className="px-5 py-3">Eligibility</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Submission Date</th>
                  <th className="px-5 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E3E8EF]">
                {pendingApprovals.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-[#6B7688]">
                      No applications currently awaiting decision approval.
                    </td>
                  </tr>
                ) : (
                  pendingApprovals.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F8FAFC] transition-colors">
                      <td className="px-5 py-3 font-semibold text-[#0B1F3A]">
                        {app.applicationNumber}
                      </td>
                      <td className="px-5 py-3 font-mono text-xs text-[#475569]">
                        {app.applicantPersonId}
                      </td>
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center text-xs font-medium text-[#15803D]">
                          {app.eligibilityResult || 'PASS'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        <Badge status="ACTIVE" label={app.status} />
                      </td>
                      <td className="px-5 py-3 text-xs text-[#64748B]">
                        {app.submissionTimestamp ? new Date(app.submissionTimestamp).toLocaleDateString() : 'N/A'}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Link href={`/college/admissions/applications/${app.id}`}>
                          <Button variant="primary" size="sm">
                            Evaluate & Decide <ArrowRight className="ml-1 h-3.5 w-3.5" />
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

