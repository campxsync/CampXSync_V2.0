'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  Building,
  GraduationCap,
  CalendarCheck2,
  X,
} from 'lucide-react';
import { navConfigs, NavItem } from '@/lib/nav-config';
import { UserRole } from '@/lib/auth/resolve-role';
import { cn } from '@/lib/utils';

export interface SidebarProps {
  role: UserRole;
  tenantName: string | null;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  mobileOpen?: boolean;
  onCloseMobile?: () => void;
}

function isNavItemActive(pathname: string, href: string, hrefs: string[]): boolean {
  if (pathname === href) return true;
  if (!pathname.startsWith(`${href}/`)) return false;
  return !hrefs.some(
    (other) =>
      other !== href &&
      other.length > href.length &&
      (pathname === other || pathname.startsWith(`${other}/`))
  );
}

export function Sidebar({
  role,
  tenantName,
  collapsed,
  onToggleCollapsed,
  mobileOpen = false,
  onCloseMobile,
}: SidebarProps) {
  const pathname = usePathname();
  const items: NavItem[] = navConfigs[role] || [];
  const hrefs = items.map((item) => item.href);

  const getRoleIcon = () => {
    switch (role) {
      case 'platform_admin':
        return Building;
      case 'college_admin':
        return ShieldCheck;
      case 'faculty':
        return CalendarCheck2;
      case 'student':
        return GraduationCap;
    }
  };

  const RoleIcon = getRoleIcon();

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs md:hidden"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col border-r border-[#12315C] bg-[#0B1F3A] text-white transition-all duration-200 select-none shrink-0',
          'md:relative md:translate-x-0',
          mobileOpen ? 'translate-x-0 w-64 shadow-2xl' : '-translate-x-full md:translate-x-0',
          collapsed ? 'md:w-16' : 'md:w-64'
        )}
      >
        {/* Brand & Collapse Header */}
        <div className="flex h-16 items-center justify-between border-b border-[#12315C] px-4">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[6px] bg-[#1F6FEB] text-white">
              <RoleIcon className="h-5 w-5" />
            </div>
            {(!collapsed || mobileOpen) && (
              <div className="flex flex-col">
                <span className="text-sm font-bold tracking-tight text-white">CampXSync</span>
                <span className="text-[10px] text-[#E3E8EF]/60 truncate max-w-[150px]">
                  {tenantName || 'Enterprise'}
                </span>
              </div>
            )}
          </div>

          {/* Desktop Collapse Toggle */}
          <button
            onClick={onToggleCollapsed}
            className={cn(
              'hidden md:flex h-7 w-7 items-center justify-center rounded-[6px] text-[#E3E8EF]/70 hover:bg-[#12315C] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]',
              collapsed && 'hidden'
            )}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            <ChevronLeft className="h-4 w-4" />
          </button>

          {/* Mobile Close Button */}
          {mobileOpen && (
            <button
              onClick={onCloseMobile}
              className="flex h-8 w-8 items-center justify-center rounded-[6px] text-[#E3E8EF]/70 hover:bg-[#12315C] hover:text-white md:hidden"
              aria-label="Close navigation menu"
            >
              <X className="h-5 w-5" />
            </button>
          )}
        </div>

        {/* Navigation List */}
        <nav className="flex-1 overflow-y-auto py-4 px-2 space-y-1" aria-label="Main Navigation">
          {items.map((item) => {
            const Icon = item.icon;
            const isActive = isNavItemActive(pathname, item.href, hrefs);

            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => {
                  if (onCloseMobile) onCloseMobile();
                }}
                className={cn(
                  'group flex items-center gap-3 rounded-[8px] px-3 py-2.5 text-xs font-medium transition-colors min-h-[44px] md:min-h-0',
                  isActive
                    ? 'bg-[#1F6FEB] text-white shadow-sm font-semibold'
                    : 'text-[#E3E8EF]/80 hover:bg-[#12315C] hover:text-white',
                  collapsed && !mobileOpen && 'justify-center px-0'
                )}
                title={collapsed && !mobileOpen ? item.title : undefined}
                aria-current={isActive ? 'page' : undefined}
              >
                <Icon className={cn('h-4 w-4 shrink-0', isActive ? 'text-white' : 'text-[#E3E8EF]/70')} />
                {(!collapsed || mobileOpen) && <span className="truncate">{item.title}</span>}
                {(!collapsed || mobileOpen) && item.badge && (
                  <span className="ml-auto rounded-full bg-[#12315C] px-1.5 py-0.5 text-[10px] text-[#3B82F6]">
                    {item.badge}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>

        {/* Toggle Button when Collapsed (Desktop only) */}
        {collapsed && (
          <div className="hidden md:block p-2 border-t border-[#12315C]">
            <button
              onClick={onToggleCollapsed}
              className="flex w-full h-8 items-center justify-center rounded-[6px] text-[#E3E8EF]/70 hover:bg-[#12315C] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]"
              title="Expand sidebar"
              aria-label="Expand sidebar"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Footer Role Identifier */}
        {(!collapsed || mobileOpen) && (
          <div className="border-t border-[#12315C] p-3 text-[11px] text-white/80">
            <div className="flex items-center justify-between">
              <span className="uppercase tracking-wider font-medium text-white/70">Role Scope</span>
              <span className="capitalize font-mono text-white text-[10px] font-semibold">
                {role.replace('_', ' ')}
              </span>
            </div>
          </div>
        )}
      </aside>
    </>
  );
}
