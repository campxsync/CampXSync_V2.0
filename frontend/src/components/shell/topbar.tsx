'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  LogOut,
  Sliders,
  Building,
  User,
  Shield,
  Sun,
  Moon,
  ChevronDown,
  Menu,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { getInitials } from '@/lib/utils';
import { UserRole } from '@/lib/auth/resolve-role';
import { createClient } from '@/lib/supabase/client';

export interface TopbarProps {
  displayName: string;
  email: string;
  role: UserRole;
  tenantName: string | null;
  onOpenPreferences?: () => void;
  onToggleMobileMenu?: () => void;
}

export function Topbar({
  displayName,
  email,
  role,
  tenantName,
  onOpenPreferences,
  onToggleMobileMenu,
}: TopbarProps) {
  const router = useRouter();
  const supabase = createClient();
  const [showUserMenu, setShowUserMenu] = useState(false);

  const handleSignOut = async () => {
    try {
      await fetch('/api/auth/test-session', { method: 'DELETE' });
    } catch {}
    await supabase.auth.signOut();
    router.refresh();
    window.location.href = '/login';
  };

  const getRoleBadge = () => {
    switch (role) {
      case 'platform_admin':
        return <Badge variant="role" roleType="platform">Platform Admin</Badge>;
      case 'college_admin':
        return <Badge variant="role" roleType="college">College Admin</Badge>;
      case 'faculty':
        return <Badge variant="role" roleType="faculty">Faculty</Badge>;
      case 'student':
        return <Badge variant="role" roleType="student">Student</Badge>;
    }
  };

  return (
    <header className="sticky top-0 z-20 flex h-16 w-full items-center justify-between border-b border-[#E3E8EF] bg-white px-4 sm:px-6 shadow-xs">
      {/* Left: Mobile Toggle & Tenant / Institutional Context */}
      <div className="flex min-w-0 flex-1 items-center gap-2 sm:gap-3">
        {onToggleMobileMenu && (
          <button
            type="button"
            onClick={onToggleMobileMenu}
            className="flex h-10 w-10 items-center justify-center rounded-[8px] text-[#0B1F3A] hover:bg-[#F6F8FB] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] md:hidden"
            aria-label="Toggle navigation menu"
          >
            <Menu className="h-5 w-5" />
          </button>
        )}

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <Building className="h-4 w-4 text-[#1F6FEB] shrink-0" />
          <span
            className="min-w-0 truncate text-sm font-semibold text-[#0B1F3A]"
            title={tenantName || 'CampSync Enterprise Platform'}
          >
            {tenantName || 'CampSync Enterprise Platform'}
          </span>
        </div>
      </div>

      {/* Right: Actions, Role Badge, Preferences & User Menu */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        {getRoleBadge()}

        {onOpenPreferences && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onOpenPreferences}
            className="text-[#475569] hover:text-[#1B2433]"
            title="Workspace Preferences"
          >
            <Sliders className="h-4 w-4" />
          </Button>
        )}

        {/* User Menu Trigger */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 rounded-[8px] p-1.5 hover:bg-[#F6F8FB] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]"
            aria-haspopup="menu"
            aria-expanded={showUserMenu}
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#1F6FEB] text-xs font-bold text-white shadow-xs">
              {getInitials(displayName)}
            </div>
            <div className="hidden md:flex flex-col text-left">
              <span className="text-xs font-semibold text-[#0B1F3A] leading-tight">
                {displayName}
              </span>
              <span className="hidden max-w-[180px] truncate text-[10px] leading-tight text-[#475569] xl:block">
                {email}
              </span>
            </div>
            <ChevronDown className="h-3 w-3 text-[#475569]" />
          </button>

          {showUserMenu && (
            <div className="absolute right-0 mt-2 w-56 rounded-[8px] border border-[#E3E8EF] bg-white py-1.5 shadow-lg z-50">
              <div className="border-b border-[#E3E8EF] px-3 py-2">
                <p className="text-xs font-semibold text-[#0B1F3A]">{displayName}</p>
                <p className="text-[11px] text-[#475569] truncate">{email}</p>
              </div>

              {onOpenPreferences && (
                <button
                  onClick={() => {
                    setShowUserMenu(false);
                    onOpenPreferences();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-xs text-[#1B2433] hover:bg-[#F6F8FB]"
                >
                  <Sliders className="h-3.5 w-3.5 text-[#475569]" />
                  Preferences & Display
                </button>
              )}

              <div className="border-t border-[#E3E8EF] my-1" />

              <button
                onClick={handleSignOut}
                className="flex w-full items-center gap-2 px-3 py-2 text-xs text-[#D64545] hover:bg-[#FDECEC]"
              >
                <LogOut className="h-3.5 w-3.5" />
                Sign Out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
