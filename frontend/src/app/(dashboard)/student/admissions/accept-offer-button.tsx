'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Award, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface AcceptOfferButtonProps {
  applicationId: string;
}

export function AcceptOfferButton({ applicationId }: AcceptOfferButtonProps) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accepted, setAccepted] = useState(false);

  const handleAccept = async () => {
    if (!confirm('Are you sure you want to accept this admission offer? This commits your seat allocation.')) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await fetch(`/api/student/admissions/applications/${applicationId}/accept-offer`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ termsAccepted: true }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to accept offer');
      }

      setAccepted(true);
      router.refresh();
    } catch (err: any) {
      setError(err.message || 'Error accepting offer');
    } finally {
      setLoading(false);
    }
  };

  if (accepted) {
    return (
      <span className="inline-flex items-center text-xs font-semibold text-[#15803D]">
        <CheckCircle2 className="mr-1 h-3.5 w-3.5" /> Offer Accepted!
      </span>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      {error && <span className="text-xs text-red-600">{error}</span>}
      <Button
        variant="primary"
        size="sm"
        disabled={loading}
        onClick={handleAccept}
        className="bg-[#15803D] hover:bg-[#166534] text-white text-xs"
      >
        <Award className="mr-1.5 h-3.5 w-3.5" />
        {loading ? 'Accepting...' : 'Accept Admission Offer'}
      </Button>
    </div>
  );
}
