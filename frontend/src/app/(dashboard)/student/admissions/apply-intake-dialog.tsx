'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Send, X, FileText, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ApplyIntakeDialogProps {
  intake: {
    id: string;
    intakeName: string;
    intakeCode: string;
    windowOpen: boolean;
    feeRequired: boolean;
    feeAmount: number | null;
    currency: string;
  };
}

export function ApplyIntakeDialog({ intake }: ApplyIntakeDialogProps) {
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const [formData, setFormData] = useState({
    candidateName: 'Aditya Verma',
    email: 'aditya.verma@example.com',
    phone: '+91 9876543210',
    qualificationGrade: 'Grade A (88.5%)',
    declarationAccepted: true,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.declarationAccepted) {
      setError('You must accept the declaration to submit the application.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await fetch('/api/student/admissions/applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          intakeId: intake.id,
          declarationAccepted: true,
          candidateName: formData.candidateName,
          email: formData.email,
          phone: formData.phone,
          qualificationGrade: formData.qualificationGrade,
          applicantData: {
            dateOfBirth: '2005-06-15',
            gender: 'MALE',
            nationality: 'INDIAN',
            previousInstitution: 'National Model Secondary School',
          },
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to submit application');
      }

      setSuccess(true);
      setTimeout(() => {
        setIsOpen(false);
        setSuccess(false);
        router.refresh();
      }, 1200);
    } catch (err: any) {
      setError(err.message || 'Error submitting application');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button
        variant="secondary"
        size="sm"
        className="text-xs"
        disabled={!intake.windowOpen}
        onClick={() => setIsOpen(true)}
      >
        Apply Now
      </Button>

      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl animate-in fade-in zoom-in duration-200">
            <div className="flex items-center justify-between border-b border-[#E3E8EF] pb-3">
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-[#1F6FEB]" />
                <div>
                  <h2 className="text-base font-bold text-[#0B1F3A]">Apply for Admission</h2>
                  <p className="text-xs text-[#64748B]">{intake.intakeName} ({intake.intakeCode})</p>
                </div>
              </div>
              <button onClick={() => setIsOpen(false)} className="text-[#64748B] hover:text-[#0B1F3A]">
                <X className="h-5 w-5" />
              </button>
            </div>

            {error && (
              <div className="mt-4 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 border border-red-200">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {success && (
              <div className="mt-4 flex items-center gap-2 rounded-lg bg-green-50 p-3 text-sm text-green-700 border border-green-200">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>Application submitted successfully! Refreshing portal...</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#334155]">Full Legal Name</label>
                <input
                  type="text"
                  required
                  value={formData.candidateName}
                  onChange={(e) => setFormData({ ...formData, candidateName: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Email Address</label>
                  <input
                    type="email"
                    required
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Phone Number</label>
                  <input
                    type="tel"
                    required
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#334155]">Prior Qualification Grade / Percentage</label>
                <input
                  type="text"
                  required
                  value={formData.qualificationGrade}
                  onChange={(e) => setFormData({ ...formData, qualificationGrade: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  placeholder="e.g. 88.5% in Higher Secondary Examination"
                />
              </div>

              <div className="rounded-lg bg-[#F8FAFC] border border-[#E3E8EF] p-3 text-xs text-[#475569]">
                <div className="flex items-start gap-2">
                  <input
                    type="checkbox"
                    id="declaration"
                    checked={formData.declarationAccepted}
                    onChange={(e) => setFormData({ ...formData, declarationAccepted: e.target.checked })}
                    className="mt-0.5 h-4 w-4 rounded border-[#CBD5E1] text-[#1F6FEB]"
                  />
                  <label htmlFor="declaration" className="cursor-pointer">
                    <span className="font-semibold text-[#0B1F3A]">Applicant Declaration (ADR D-02 / US-040):</span>
                    <p className="mt-0.5">
                      I hereby certify that all information provided is true and accurate. I understand that false statements will result in immediate disqualification.
                    </p>
                  </label>
                </div>
              </div>

              <div className="mt-6 flex justify-end gap-3 pt-3 border-t border-[#E3E8EF]">
                <Button type="button" variant="outline" onClick={() => setIsOpen(false)} disabled={loading}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" disabled={loading}>
                  {loading ? 'Submitting...' : 'Submit Application'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
