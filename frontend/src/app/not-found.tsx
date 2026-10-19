import React from 'react';
import Link from 'next/link';
import { FileQuestion, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#F6F8FB] p-6">
      <div className="w-full max-w-md rounded-[12px] border border-[#E3E8EF] bg-white p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[#E8F0FE] text-[#1F6FEB]">
          <FileQuestion className="h-7 w-7" />
        </div>

        <h1 className="mt-5 text-2xl font-bold text-[#0B1F3A]">Page Not Found</h1>
        <p className="mt-2 text-xs text-[#6B7688] leading-relaxed">
          The requested institutional route does not exist or has been relocated.
        </p>

        <div className="mt-6 flex justify-center">
          <Link href="/">
            <Button variant="primary" size="sm">
              <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
              Return to Workspace
            </Button>
          </Link>
        </div>

        <div className="mt-8 border-t border-[#E3E8EF] pt-4 text-[11px] text-[#6B7688]">
          CampXSync Platform 404 Guard
        </div>
      </div>
    </div>
  );
}
