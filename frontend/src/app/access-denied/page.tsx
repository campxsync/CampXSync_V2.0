'use client';

import React, { Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ShieldAlert, ArrowLeft, Building2, UserX, AlertTriangle, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { createClient } from '@/lib/supabase/client';

export const dynamic = 'force-dynamic';

function AccessDeniedContent() {
  const searchParams = useSearchParams();
  const reason = searchParams?.get('reason') || 'unauthorized';
  const supabase = createClient();

  const handleLogout = async () => {
    await supabase.auth.signOut();
    window.location.href = '/login';
  };

  const getReasonDetails = () => {
    switch (reason) {
      case 'no_profile':
        return {
          title: 'Institutional Profile Not Found',
          description:
            'Your authentication was verified, but no active organizational profile is attached to your account in this college. Please contact your college administrator to grant user permissions.',
          icon: UserX,
        };
      case 'inactive_membership':
        return {
          title: 'Account Membership Inactive',
          description:
            'Your institutional membership has expired or has been temporarily deactivated by your institution administrator.',
          icon: AlertTriangle,
        };
      case 'tenant_suspended':
        return {
          title: 'Institution Workspace Suspended',
          description:
            'Access for this educational institution is currently suspended. Platform administrators may resolve status transitions.',
          icon: Building2,
        };
      case 'forbidden':
      default:
        return {
          title: 'Access Restricted',
          description:
            'You do not have the required role permissions to access this workspace section. Contact your department administrator if you believe this is in error.',
          icon: ShieldAlert,
        };
    }
  };

  const { title, description, icon: Icon } = getReasonDetails();

  return (
    <div className="w-full max-w-md rounded-[12px] border border-[#E3E8EF] bg-white p-8 text-center shadow-sm">
      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[#FEF7EA] text-[#E39B16]">
        <Icon className="h-7 w-7" />
      </div>

      <h1 className="mt-5 text-xl font-bold text-[#0B1F3A]">{title}</h1>
      <p className="mt-2 text-xs text-[#6B7688] leading-relaxed">{description}</p>

      <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
        <Link href="/login" className="w-full sm:w-auto">
          <Button variant="outline" size="sm" className="w-full">
            <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
            Return to Login
          </Button>
        </Link>

        <Button
          variant="ghost"
          size="sm"
          onClick={handleLogout}
          className="w-full sm:w-auto text-[#D64545] hover:bg-[#FDECEC]"
        >
          <LogOut className="mr-1.5 h-3.5 w-3.5" />
          Sign Out
        </Button>
      </div>

      <div className="mt-8 border-t border-[#E3E8EF] pt-4 text-[11px] text-[#6B7688]">
        Reference: Supabase Staging Policy Guard
      </div>
    </div>
  );
}

export default function AccessDeniedPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#F6F8FB] p-6">
      <Suspense fallback={<div className="text-xs text-[#6B7688]">Checking permissions...</div>}>
        <AccessDeniedContent />
      </Suspense>
    </div>
  );
}
