'use client';

import React, { useEffect } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('Application runtime error:', error);
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#F6F8FB] p-6">
      <div className="w-full max-w-md rounded-[12px] border border-[#F8BDBD] bg-white p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[#FDECEC] text-[#D64545]">
          <AlertTriangle className="h-7 w-7" />
        </div>

        <h1 className="mt-5 text-xl font-bold text-[#D64545]">Workspace Execution Error</h1>
        <p className="mt-2 text-xs text-[#6B7688] leading-relaxed">
          An unexpected error occurred during execution. Institutional security constraints prevented unsafe degradation.
        </p>

        {error?.message && (
          <div className="mt-4 rounded-[6px] border border-[#E3E8EF] bg-[#F6F8FB] p-2.5 font-mono text-[11px] text-[#1B2433] text-left overflow-x-auto">
            {error.message}
          </div>
        )}

        <div className="mt-6 flex justify-center gap-3">
          <Button variant="outline" size="sm" onClick={() => (window.location.href = '/')}>
            Return Home
          </Button>
          <Button variant="primary" size="sm" onClick={() => reset()}>
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
            Retry Action
          </Button>
        </div>

        <div className="mt-8 border-t border-[#E3E8EF] pt-4 text-[11px] text-[#6B7688]">
          CampXSync Resilient Error Boundary
        </div>
      </div>
    </div>
  );
}
