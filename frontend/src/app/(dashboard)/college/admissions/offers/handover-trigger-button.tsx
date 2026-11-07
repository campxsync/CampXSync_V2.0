'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { RotateCw, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface HandoverTriggerButtonProps {
  applicationId: string;
}

export function HandoverTriggerButton({ applicationId }: HandoverTriggerButtonProps) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const handleHandover = async () => {
    if (!confirm('Trigger STM-01 Student Handover? This will register the student identity and matriculate the record in PostgreSQL.')) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await fetch(`/api/college/admissions/applications/${applicationId}/handover`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to trigger student handover');
      }

      const data = await res.json().catch(() => ({}));
      setDone(true);
      const studentId = data?.createdStudentId;
      if (studentId) {
        const params = new URLSearchParams({
          studentId: String(studentId),
          applicationId,
        });
        router.push(`/college/enrollment?${params.toString()}`);
        return;
      }
      router.refresh();
    } catch (err: any) {
      setError(err.message || 'Error triggering handover');
    } finally {
      setLoading(false);
    }
  };

  if (done) {
    return (
      <span className="inline-flex items-center text-xs font-semibold text-[#15803D]">
        <CheckCircle2 className="mr-1 h-3.5 w-3.5" /> Handover Complete
      </span>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      {error && <span className="text-[10px] text-red-600">{error}</span>}
      <Button
        variant="primary"
        size="sm"
        disabled={loading}
        onClick={handleHandover}
        className="bg-[#15803D] hover:bg-[#166534] text-white text-xs"
      >
        <RotateCw className={`mr-1.5 h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
        {loading ? 'Enrolling...' : 'Trigger STM-01 Handover'}
      </Button>
    </div>
  );
}
