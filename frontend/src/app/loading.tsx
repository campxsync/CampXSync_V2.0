import React from 'react';

export default function Loading() {
  return (
    <div className="flex min-h-[50vh] w-full items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#1F6FEB] border-t-transparent" />
        <span className="text-xs font-medium text-[#6B7688]">Loading workspace...</span>
      </div>
    </div>
  );
}

