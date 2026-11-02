'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { 
  CheckCircle, 
  XCircle, 
  UserPlus, 
  Award, 
  Send, 
  RotateCw, 
  ShieldCheck, 
  AlertCircle,
  FileCheck
} from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ApplicationActionsProps {
  applicationId: string;
  status: string;
  documents: Array<{ id: string; fileName: string; verificationStatus: string }>;
  hasReviews: boolean;
  hasDecision: boolean;
  hasOffer: boolean;
  isOfferAccepted: boolean;
}

export function ApplicationActions({
  applicationId,
  status,
  documents,
  hasReviews,
  hasDecision,
  hasOffer,
  isOfferAccepted,
}: ApplicationActionsProps) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<{ text: string; error?: boolean } | null>(null);

  // Review Form state
  const [showReviewForm, setShowReviewForm] = useState(false);
  const [score, setScore] = useState(85);
  const [recommendation, setRecommendation] = useState<'ADMIT' | 'WAITLIST' | 'REJECT'>('ADMIT');
  const [comments, setComments] = useState('Applicant demonstrates strong academic fundamentals.');

  // Decision Form state
  const [showDecisionForm, setShowDecisionForm] = useState(false);
  const [decisionType, setDecisionType] = useState<'APPROVED' | 'REJECTED' | 'WAITLISTED'>('APPROVED');
  const [decisionNotes, setDecisionNotes] = useState('Admission approved based on academic merit.');

  // 1. Verify Document
  const handleVerifyDocument = async (docId: string, docStatus: 'VERIFIED' | 'REJECTED') => {
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/documents/${docId}/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: docStatus }),
      });
      if (!res.ok) throw new Error(await res.text());
      setMsg({ text: `Document marked ${docStatus}` });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  // 2. Assign Reviewer
  const handleAssignReviewer = async () => {
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/assign`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          reviewerId: '11111111-9e05-0000-0000-000000000001',
          departmentId: '11111111-2222-3333-4444-555555555550',
        }),
      });
      if (!res.ok) throw new Error(await res.text());
      setMsg({ text: 'Reviewer assigned. Status moved to UNDER_REVIEW.' });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  // 3. Submit Review
  const handleSubmitReview = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/reviews`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          totalScore: Number(score),
          recommendation,
          confidentialComments: comments,
          applicantFacingRemarks: 'Evaluation completed.',
          rubricVersion: '1.0',
        }),
      });
      if (!res.ok) throw new Error(await res.text());
      setShowReviewForm(false);
      setMsg({ text: 'Department evaluation review recorded successfully.' });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  // 4. Record Decision
  const handleRecordDecision = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/decision`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          decisionType,
          decisionNotes,
        }),
      });
      if (!res.ok) throw new Error(await res.text());
      setShowDecisionForm(false);
      setMsg({ text: `Admission decision [${decisionType}] recorded.` });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  // 5. Issue Offer
  const handleIssueOffer = async () => {
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/offers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ validDays: 14 }),
      });
      if (!res.ok) throw new Error(await res.text());
      setMsg({ text: 'Formal admission offer issued to applicant!' });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  // 6. Trigger Handover
  const handleTriggerHandover = async () => {
    setLoading(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/handover`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      setMsg({ text: `Handover complete! Matriculated Student ID: ${data.createdStudentId || 'Created'}` });
      router.refresh();
    } catch (err: any) {
      setMsg({ text: err.message, error: true });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {msg && (
        <div className={`p-3 rounded-lg text-xs flex items-center gap-2 ${msg.error ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-green-50 text-green-700 border border-green-200'}`}>
          {msg.error ? <AlertCircle className="h-4 w-4 shrink-0" /> : <CheckCircle className="h-4 w-4 shrink-0" />}
          <span>{msg.text}</span>
        </div>
      )}

      {/* Action Buttons Toolbar */}
      <div className="flex flex-wrap items-center gap-2">
        {/* Step 1: Assign Reviewer (if SUBMITTED or ELIGIBLE) */}
        {(status === 'SUBMITTED' || status === 'ELIGIBLE') && (
          <Button
            variant="outline"
            size="sm"
            disabled={loading}
            onClick={handleAssignReviewer}
            className="text-xs"
          >
            <UserPlus className="mr-1.5 h-3.5 w-3.5 text-[#1F6FEB]" />
            Assign Reviewer
          </Button>
        )}

        {/* Step 2: Score / Review Application (if UNDER_REVIEW) */}
        {status === 'UNDER_REVIEW' && (
          <Button
            variant="outline"
            size="sm"
            disabled={loading}
            onClick={() => setShowReviewForm(!showReviewForm)}
            className="text-xs"
          >
            <FileCheck className="mr-1.5 h-3.5 w-3.5 text-[#1F6FEB]" />
            {showReviewForm ? 'Cancel Review' : 'Record Faculty Review'}
          </Button>
        )}

        {/* Step 3: Record Executive Decision (if reviews exist & no decision yet) */}
        {hasReviews && !hasDecision && (
          <Button
            variant="primary"
            size="sm"
            disabled={loading}
            onClick={() => setShowDecisionForm(!showDecisionForm)}
            className="text-xs"
          >
            <ShieldCheck className="mr-1.5 h-3.5 w-3.5" />
            {showDecisionForm ? 'Cancel Decision' : 'Record Executive Decision'}
          </Button>
        )}

        {/* Step 4: Issue Formal Offer (if decision APPROVED and no offer yet) */}
        {hasDecision && !hasOffer && (
          <Button
            variant="primary"
            size="sm"
            disabled={loading}
            onClick={handleIssueOffer}
            className="bg-[#1F6FEB] text-xs"
          >
            <Award className="mr-1.5 h-3.5 w-3.5" />
            Issue Formal Offer
          </Button>
        )}

        {/* Step 5: Trigger STM-01 Student Handover (if offer ACCEPTED and not handed over) */}
        {isOfferAccepted && status !== 'HANDED_OVER' && (
          <Button
            variant="primary"
            size="sm"
            disabled={loading}
            onClick={handleTriggerHandover}
            className="bg-[#15803D] hover:bg-[#166534] text-white text-xs"
          >
            <RotateCw className="mr-1.5 h-3.5 w-3.5" />
            Trigger STM-01 Handover
          </Button>
        )}
      </div>

      {/* Review Submission Form Drawer / Card */}
      {showReviewForm && (
        <form onSubmit={handleSubmitReview} className="rounded-lg border border-[#CBD5E1] bg-[#F8FAFC] p-4 text-xs space-y-3">
          <h4 className="font-bold text-sm text-[#0B1F3A]">Department Rubric Evaluation</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="font-semibold text-[#334155]">Total Score (0–100)</label>
              <input
                type="number"
                min={0}
                max={100}
                required
                value={score}
                onChange={(e) => setScore(Number(e.target.value))}
                className="mt-1 w-full rounded border border-[#CBD5E1] p-1.5 text-xs bg-white"
              />
            </div>
            <div>
              <label className="font-semibold text-[#334155]">Faculty Recommendation</label>
              <select
                value={recommendation}
                onChange={(e: any) => setRecommendation(e.target.value)}
                className="mt-1 w-full rounded border border-[#CBD5E1] p-1.5 text-xs bg-white"
              >
                <option value="ADMIT">ADMIT (Recommend Admission)</option>
                <option value="WAITLIST">WAITLIST</option>
                <option value="REJECT">REJECT</option>
              </select>
            </div>
          </div>
          <div>
            <label className="font-semibold text-[#334155]">Confidential Remarks</label>
            <textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              className="mt-1 w-full rounded border border-[#CBD5E1] p-1.5 text-xs bg-white"
              rows={2}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setShowReviewForm(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={loading}>
              {loading ? 'Submitting...' : 'Save Evaluation'}
            </Button>
          </div>
        </form>
      )}

      {/* Decision Recording Form Drawer / Card */}
      {showDecisionForm && (
        <form onSubmit={handleRecordDecision} className="rounded-lg border border-[#B8D3FC] bg-[#EFF6FF] p-4 text-xs space-y-3">
          <h4 className="font-bold text-sm text-[#1E40AF]">Executive Admission Decision (Dean / Approver)</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="font-semibold text-[#1E3A8A]">Decision Type</label>
              <select
                value={decisionType}
                onChange={(e: any) => setDecisionType(e.target.value)}
                className="mt-1 w-full rounded border border-[#93C5FD] p-1.5 text-xs bg-white"
              >
                <option value="APPROVED">APPROVED</option>
                <option value="REJECTED">REJECTED</option>
                <option value="WAITLISTED">WAITLISTED</option>
              </select>
            </div>
            <div>
              <label className="font-semibold text-[#1E3A8A]">Decision Notes / Justification</label>
              <input
                type="text"
                required
                value={decisionNotes}
                onChange={(e) => setDecisionNotes(e.target.value)}
                className="mt-1 w-full rounded border border-[#93C5FD] p-1.5 text-xs bg-white"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setShowDecisionForm(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={loading} className="bg-[#1D4ED8]">
              {loading ? 'Recording...' : 'Commit Executive Decision'}
            </Button>
          </div>
        </form>
      )}

      {/* Document Verification Actions (Inline for each document) */}
      {documents.length > 0 && (
        <div className="rounded-lg border border-[#E3E8EF] bg-white p-3 space-y-2">
          <h4 className="font-bold text-xs text-[#0B1F3A]">Quick Document Verification</h4>
          {documents.map((doc) => (
            <div key={doc.id} className="flex items-center justify-between border-b border-[#F1F5F9] pb-1.5 text-xs">
              <span className="font-medium text-[#334155]">{doc.fileName}</span>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={loading || doc.verificationStatus === 'VERIFIED'}
                  onClick={() => handleVerifyDocument(doc.id, 'VERIFIED')}
                  className="h-6 text-[11px] px-2 text-[#15803D]"
                >
                  <CheckCircle className="mr-1 h-3 w-3" /> Verify
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={loading || doc.verificationStatus === 'REJECTED'}
                  onClick={() => handleVerifyDocument(doc.id, 'REJECTED')}
                  className="h-6 text-[11px] px-2 text-[#B91C1C]"
                >
                  <XCircle className="mr-1 h-3 w-3" /> Reject
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
