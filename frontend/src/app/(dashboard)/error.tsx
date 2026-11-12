'use client';

import React, { useEffect } from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function DashboardError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const referenceId = error?.digest || 'REF-' + Math.random().toString(36).substring(2, 10).toUpperCase();

  useEffect(() => {
    // Log to structured logger or standard error
    process.env.NODE_ENV !== 'production' && console.warn('[Dashboard Boundary Captured]', {
      name: error.name,
      message: error.message,
      digest: error.digest,
    });
  }, [error]);

  return (
    <div className="p-8">
      <div className="mx-auto max-w-xl rounded-xl border border-red-200 bg-white p-6 shadow-sm">
        <div className="flex items-start gap-4">
          <div className="rounded-lg bg-red-50 p-2.5 text-red-600">
            <AlertCircle className="h-6 w-6" />
          </div>
          <div className="flex-1">
            <h2 className="text-base font-semibold text-gray-900">Dashboard Operation Encountered an Error</h2>
            <p className="mt-1 text-xs text-gray-600">
              The requested data could not be loaded. Institutional security and transaction boundaries were preserved.
            </p>
            {error?.message && (
              <div className="mt-3 rounded border border-gray-200 bg-gray-50 p-2 font-mono text-xs text-gray-800">
                {error.message}
              </div>
            )}
            <div className="mt-3 flex items-center gap-2 text-[11px] text-gray-500 font-mono">
              <span>Reference ID:</span>
              <span className="font-semibold text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">{referenceId}</span>
            </div>
            <div className="mt-5 flex gap-3">
              <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
                Reload Page
              </Button>
              <Button size="sm" variant="primary" onClick={() => reset()}>
                <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                Retry Component
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

