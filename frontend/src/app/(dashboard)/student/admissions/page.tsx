import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  FileText, 
  CheckCircle2, 
  Clock, 
  Award, 
  AlertCircle, 
  Upload, 
  Send, 
  Layers,
  ArrowRight,
  ShieldCheck,
  User
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchApplications, fetchActiveIntakes } from '@/lib/api/admissions';
import { ApplyIntakeDialog } from './apply-intake-dialog';
import { AcceptOfferButton } from './accept-offer-button';

export const dynamic = 'force-dynamic';

export default async function ApplicantPortalPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    redirect('/access-denied?reason=unauthenticated');
  }

  const { tenantId, userId, displayName, email } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Fetch the applicant's existing applications and currently active admission intakes
  const [myApplications, activeIntakes] = await Promise.all([
    fetchApplications(tenantId, userId, 'APPLICANT'),
    fetchActiveIntakes(tenantId, userId, 'APPLICANT'),
  ]);

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Applicant Admissions Portal</h1>
            <Badge status="ACTIVE" label="Self-Service" />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Welcome, <span className="font-semibold text-[#0B1F3A]">{displayName || email}</span>. Track your applications, view admissions offers, and complete student enrolment.
          </p>
        </div>
      </div>

      {/* Active Applications Section */}
      <div className="space-y-4">
        <h2 className="text-lg font-bold text-[#0B1F3A]">My Admissions Applications</h2>

        {myApplications.length === 0 ? (
          <Card>
            <CardContent className="p-8 text-center">
              <FileText className="mx-auto h-12 w-12 text-[#94A3B8]" />
              <h3 className="mt-4 text-base font-semibold text-[#0B1F3A]">No Active Applications</h3>
              <p className="mt-2 text-sm text-[#64748B]">
                You have not submitted an admissions application yet. Explore open intakes below to apply for upcoming academic terms.
              </p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {myApplications.map((app) => (
              <Card key={app.id} className="hover:border-[#B8D3FC] transition-colors">
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <div>
                      <span className="text-xs font-mono font-semibold text-[#1F6FEB]">
                        {app.applicationNumber}
                      </span>
                      <CardTitle className="mt-1">Application for Admission</CardTitle>
                    </div>
                    <Badge status={app.status} label={app.status} />
                  </div>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="flex justify-between border-b border-[#E3E8EF] pb-2 text-xs">
                    <span className="text-[#64748B]">Status:</span>
                    <span className="font-semibold text-[#0B1F3A]">{app.status}</span>
                  </div>
                  <div className="flex justify-between border-b border-[#E3E8EF] pb-2 text-xs">
                    <span className="text-[#64748B]">Eligibility Check:</span>
                    <span className="font-semibold text-[#15803D]">{app.eligibilityResult || 'PASS'}</span>
                  </div>
                  <div className="flex justify-between border-b border-[#E3E8EF] pb-2 text-xs">
                    <span className="text-[#64748B]">Document Verification:</span>
                    <span className="font-semibold text-[#0B1F3A]">{app.documentVerificationStatus}</span>
                  </div>
                  <div className="flex justify-between border-b border-[#E3E8EF] pb-2 text-xs">
                    <span className="text-[#64748B]">Fee Payment:</span>
                    <span className="font-semibold text-[#15803D]">{app.paymentStatus || 'PAID'}</span>
                  </div>

                  {app.status === 'OFFERED' && (
                    <div className="rounded-[8px] border border-[#B8D3FC] bg-[#E8F0FE] p-3 text-xs text-[#1D4ED8] mt-2">
                      <p className="font-bold flex items-center gap-1">
                        <Award className="h-4 w-4" /> Admission Offer Issued!
                      </p>
                      <p className="mt-1">
                        Congratulations! You have received a formal admission offer. Review and accept your offer to confirm your student enrolment.
                      </p>
                      <div className="mt-3 flex justify-end">
                        <AcceptOfferButton applicationId={app.id} />
                      </div>
                    </div>
                  )}

                  {app.status === 'ACCEPTED' && (
                    <div className="rounded-[8px] border border-[#B8D3FC] bg-[#EFF6FF] p-3 text-xs text-[#1D4ED8] mt-2">
                      <p className="font-bold flex items-center gap-1">
                        <CheckCircle2 className="h-4 w-4 text-[#1D4ED8]" /> Offer Accepted
                      </p>
                      <p className="mt-1">
                        Your offer acceptance is confirmed. The institution is preparing your matriculation record and student profile.
                      </p>
                    </div>
                  )}

                  {app.status === 'HANDED_OVER' && (
                    <div className="rounded-[8px] border border-[#B2E6CE] bg-[#EAF8F1] p-3 text-xs text-[#15803D] mt-2">
                      <p className="font-bold flex items-center gap-1">
                        <CheckCircle2 className="h-4 w-4" /> Enrolment Handover Complete!
                      </p>
                      <p className="mt-1">
                        Your student identity has been established in STM-01. Access your student dashboard to review attendance and course registration.
                      </p>
                    </div>
                  )}

                  <div className="pt-2 flex justify-end">
                    <Link href={`/student`}>
                      <Button variant="outline" size="sm" className="text-xs">
                        Student Portal &rarr;
                      </Button>
                    </Link>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* Available Intakes to Apply */}
      <div className="space-y-4 pt-4">
        <h2 className="text-lg font-bold text-[#0B1F3A]">Open Academic Intakes</h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {activeIntakes.length === 0 ? (
            <div className="col-span-full rounded-[8px] border border-[#E3E8EF] p-6 text-center text-sm text-[#64748B]">
              No open intake cycles at this time. Please check back when admissions open.
            </div>
          ) : (
            activeIntakes.map((intake) => (
              <Card key={intake.id} className="p-4">
                <div className="flex items-start justify-between">
                  <span className="text-xs font-mono font-semibold text-[#1F6FEB]">{intake.intakeCode}</span>
                  <Badge status={intake.windowOpen ? 'ACTIVE' : 'LOCKED'} label={intake.windowOpen ? 'OPEN' : 'CLOSED'} />
                </div>
                <h3 className="mt-2 font-bold text-sm text-[#0B1F3A]">{intake.intakeName}</h3>
                <p className="mt-2 text-xs text-[#64748B]">
                  Capacity: {intake.capacity} Seats | Admitted: {intake.admittedCount || 0}
                </p>
                <p className="text-xs text-[#64748B] mt-1">
                  Window: {new Date(intake.applicationWindowStart).toLocaleDateString()} – {new Date(intake.applicationWindowEnd).toLocaleDateString()}
                </p>
                <div className="mt-4 flex justify-between items-center">
                  <span className="text-xs font-semibold text-[#0B1F3A]">
                    {intake.feeRequired ? `${intake.currency} ${intake.feeAmount}` : 'Free'}
                  </span>
                  <ApplyIntakeDialog intake={intake} />
                </div>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
