import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  FileText, 
  ArrowLeft, 
  CheckCircle, 
  XCircle, 
  ShieldAlert, 
  Award, 
  Clock, 
  UserCheck,
  Send,
  Download
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchApplicationDetails } from '@/lib/api/admissions';
import { ApplicationActions } from './application-actions';

export const dynamic = 'force-dynamic';

export default async function ApplicationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
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

  const { id: applicationId } = await params;

  const {
    application,
    documents,
    reviews,
    decision,
    offer,
    handover
  } = await fetchApplicationDetails(tenantId, userId, 'COLLEGE_ADMIN', applicationId, collegeId);

  if (!application) {
    return (
      <div className="p-8 text-center">
        <h2 className="text-xl font-bold text-[#0B1F3A]">Application Not Found</h2>
        <p className="mt-2 text-sm text-[#475569]">The requested application does not exist or has been deleted.</p>
        <Link href="/college/admissions" className="mt-4 inline-block">
          <Button variant="outline">Back to Queue</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      {/* Back button and Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href="/college/admissions" className="inline-flex items-center text-xs font-semibold text-[#1F6FEB] hover:underline mb-2">
            <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Back to Application Queue
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">{application.applicationNumber}</h1>
            <Badge status={application.status} label={application.status} />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Applicant Person: <span className="font-mono text-xs">{application.applicantPersonId}</span> | Intake: <span className="font-mono text-xs">{application.intakeId}</span>
          </p>
        </div>

        <div className="flex items-center gap-2">
          {application.status === 'OFFERED' && (
            <Badge status="ACTIVE" label="Offer Pending Response" />
          )}
          {application.status === 'ACCEPTED' && (
            <Badge status="ACTIVE" label="Ready for STM-01 Handover" />
          )}
          {application.status === 'HANDED_OVER' && (
            <Link href="/college/students">
              <Button variant="secondary" size="sm">
                <UserCheck className="mr-2 h-4 w-4 text-[#15803D]" /> Enrolled in STM-01
              </Button>
            </Link>
          )}
        </div>
      </div>

      {/* Interactive Actions Panel */}
      <Card className="border-[#B8D3FC] bg-[#F4F8FE]">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm text-[#1D4ED8]">Admissions Officer Actions & Lifecycle Controls</CardTitle>
          <CardDescription className="text-xs">
            Perform document verification, assign faculty evaluators, record rubric evaluations, and issue offers.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ApplicationActions
            applicationId={application.id}
            status={application.status}
            documents={documents.map((d: any) => ({
              id: d.id,
              fileName: d.fileName || d.documentType,
              verificationStatus: d.verificationStatus,
            }))}
            hasReviews={reviews.length > 0}
            hasDecision={decision !== null}
            hasOffer={offer !== null}
            isOfferAccepted={offer !== null && offer.status === 'ACCEPTED'}
          />
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left Column: Metadata & Applicant Data */}
        <div className="space-y-6 lg:col-span-1">
          <Card>
            <CardHeader>
              <CardTitle>Application Overview</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <div className="flex justify-between border-b border-[#E3E8EF] pb-2">
                <span className="text-[#64748B]">Status</span>
                <span className="font-semibold text-[#0B1F3A]">{application.status}</span>
              </div>
              <div className="flex justify-between border-b border-[#E3E8EF] pb-2">
                <span className="text-[#64748B]">Submitted</span>
                <span className="text-[#0B1F3A]">
                  {application.submissionTimestamp ? new Date(application.submissionTimestamp).toLocaleString() : 'Not submitted'}
                </span>
              </div>
              <div className="flex justify-between border-b border-[#E3E8EF] pb-2">
                <span className="text-[#64748B]">Eligibility Check</span>
                <span className="font-semibold text-[#15803D]">{application.eligibilityResult || 'PASS'}</span>
              </div>
              <div className="flex justify-between border-b border-[#E3E8EF] pb-2">
                <span className="text-[#64748B]">Document Verification</span>
                <span className="font-semibold text-[#0B1F3A]">{application.documentVerificationStatus}</span>
              </div>
              <div className="flex justify-between border-b border-[#E3E8EF] pb-2">
                <span className="text-[#64748B]">Application Fee</span>
                <span className="font-semibold text-[#0B1F3A]">{application.paymentStatus || 'PAID'}</span>
              </div>
              <div className="flex justify-between pb-2">
                <span className="text-[#64748B]">Declaration Accepted</span>
                <span className="font-semibold text-[#15803D]">{application.declarationAccepted ? 'YES' : 'NO'}</span>
              </div>
            </CardContent>
          </Card>

          {/* Applicant Profile JSON */}
          <Card>
            <CardHeader>
              <CardTitle>Applicant Profile Details</CardTitle>
              <CardDescription>Submitted personal and academic history</CardDescription>
            </CardHeader>
            <CardContent>
              <pre className="max-h-60 overflow-y-auto rounded-[8px] bg-[#F8FAFC] p-3 text-xs text-[#334155]">
                {application.applicantData || '{}'}
              </pre>
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Document Verification & Reviews */}
        <div className="space-y-6 lg:col-span-2">
          {/* Documents Workspace */}
          <Card>
            <CardHeader>
              <CardTitle>Document Verification Workspace</CardTitle>
              <CardDescription>
                Review submitted transcripts, identity credentials, and qualification certificates.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {documents.length === 0 ? (
                <div className="py-6 text-center text-sm text-[#64748B]">
                  No supporting documents attached to this application.
                </div>
              ) : (
                <div className="space-y-3">
                  {documents.map((doc: any) => (
                    <div key={doc.id} className="flex flex-col sm:flex-row sm:items-center justify-between rounded-[8px] border border-[#E3E8EF] p-4 bg-white">
                      <div className="flex items-center gap-3">
                        <FileText className="h-5 w-5 text-[#1F6FEB]" />
                        <div>
                          <p className="font-medium text-[#0B1F3A] text-sm">{doc.fileName || doc.documentType}</p>
                          <p className="text-xs text-[#64748B]">
                            Type: {doc.documentType} | Size: {Math.round(doc.fileSizeBytes / 1024)} KB
                          </p>
                        </div>
                      </div>

                      <div className="mt-3 sm:mt-0 flex items-center gap-2">
                        <Badge 
                          status={doc.verificationStatus === 'VERIFIED' ? 'ACTIVE' : doc.verificationStatus === 'REJECTED' ? 'CANCELLED' : 'DRAFT'} 
                          label={doc.verificationStatus} 
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Reviewer Scoring History */}
          <Card>
            <CardHeader>
              <CardTitle>Department Reviews & Rubric Evaluations</CardTitle>
              <CardDescription>
                Faculty evaluation scores, recommendation notes, and confidential reviewer feedback.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {reviews.length === 0 ? (
                <div className="py-6 text-center text-sm text-[#64748B]">
                  No departmental reviews recorded yet. Use the action panel above to assign a reviewer.
                </div>
              ) : (
                <div className="space-y-4">
                  {reviews.map((rev: any) => (
                    <div key={rev.id} className="rounded-[8px] border border-[#E3E8EF] p-4 bg-[#F8FAFC]">
                      <div className="flex items-center justify-between border-b border-[#E3E8EF] pb-2 mb-2">
                        <span className="font-semibold text-sm text-[#0B1F3A]">
                          Recommendation: <span className="text-[#1F6FEB]">{rev.recommendation}</span>
                        </span>
                        <span className="font-bold text-sm text-[#0B1F3A]">
                          Score: {rev.totalScore} / 100
                        </span>
                      </div>
                      <p className="text-xs text-[#334155] mt-1">
                        <span className="font-semibold">Reviewer Comments:</span> {rev.confidentialComments || 'No comments'}
                      </p>
                      <p className="text-xs text-[#64748B] mt-2">
                        Rubric Version: {rev.rubricVersion} | Recorded: {new Date(rev.createdAt).toLocaleString()}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Admission Decision & Offer Summary */}
          {(decision || offer || handover) && (
            <Card>
              <CardHeader>
                <CardTitle>Decision, Offer & Handover Record</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {decision && (
                  <div className="rounded-[8px] border border-[#E3E8EF] p-3 bg-white">
                    <p className="font-semibold text-xs text-[#475569] uppercase">Executive Decision</p>
                    <p className="text-[#0B1F3A] font-medium mt-1">{decision.decisionType} — {decision.decisionNotes}</p>
                    <p className="text-xs text-[#64748B] mt-1">Recorded: {new Date(decision.createdAt).toLocaleString()}</p>
                  </div>
                )}
                {offer && (
                  <div className="rounded-[8px] border border-[#B8D3FC] p-3 bg-[#E8F0FE]">
                    <p className="font-semibold text-xs text-[#1D4ED8] uppercase">Formal Admission Offer</p>
                    <p className="text-[#0B1F3A] font-medium mt-1">Offer Code: {offer.offerCode} | Status: {offer.status}</p>
                    <p className="text-xs text-[#1D4ED8] mt-1">Valid Until: {new Date(offer.validTo).toLocaleDateString()}</p>
                  </div>
                )}
                {handover && (
                  <div className="rounded-[8px] border border-[#B2E6CE] p-3 bg-[#EAF8F1]">
                    <p className="font-semibold text-xs text-[#15803D] uppercase">STM-01 Student Handover</p>
                    <p className="text-[#0B1F3A] font-medium mt-1">Status: {handover.enrolmentStatus} | Student ID: {handover.createdStudentId || 'N/A'}</p>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
