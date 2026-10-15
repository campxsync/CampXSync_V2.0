'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  Building,
  GraduationCap,
  CalendarCheck2,
  Sliders,
} from 'lucide-react';
import { navConfigs, NavItem } from '@/lib/nav-config';
import { UserRole } from '@/lib/auth/resolve-role';
import { cn } from '@/lib/utils';

export interface SidebarProps {
  role: UserRole;
  tenantName: string | null;
  collapsed: boolean;
  onToggleCollapsed: () => void;
}

export function Sidebar({ role, tenantName, collapsed, onToggleCollapsed }: SidebarProps) {
  const pathname = usePathname();
  const items: NavItem[] = navConfigs[role] || [];

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
    <aside
      className={cn(
        'relative flex flex-col border-r border-[#12315C] bg-[#0B1F3A] text-white transition-all duration-200 select-none z-30 shrink-0',
        collapsed ? 'w-16' : 'w-64'
      )}
    >
      {/* Brand & Collapse Header */}
      <div className="flex h-16 items-center justify-between border-b border-[#12315C] px-4">
        {!collapsed && (
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[6px] bg-[#1F6FEB] text-white">
              <RoleIcon className="h-5 w-5" />
            </div>
            <div className="flex flex-col">
              <span className="text-sm font-bold tracking-tight text-white">CampXSync</span>
              <span className="text-[10px] text-[#E3E8EF]/60 truncate max-w-[150px]">
                {tenantName || 'Enterprise'}
              </span>
            </div>
          </div>
        )}

        {collapsed && (
          <div className="mx-auto flex h-8 w-8 items-center justify-center rounded-[6px] bg-[#1F6FEB] text-white">
            <RoleIcon className="h-5 w-5" />
          </div>
        )}

        <button
          onClick={onToggleCollapsed}
          className={cn(
            'flex h-7 w-7 items-center justify-center rounded-[6px] text-[#E3E8EF]/70 hover:bg-[#12315C] hover:text-white',
            collapsed && 'hidden'
          )}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
      </div>

      {/* Navigation List */}
      <div className="flex-1 overflow-y-auto py-4 px-2 space-y-1">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href || (item.href !== `/${role}` && pathname.startsWith(item.href));

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'group flex items-center gap-3 rounded-[8px] px-3 py-2.5 text-xs font-medium transition-colors',
                isActive
                  ? 'bg-[#1F6FEB] text-white shadow-sm font-semibold'
                  : 'text-[#E3E8EF]/80 hover:bg-[#12315C] hover:text-white',
                collapsed && 'justify-center px-0'
              )}
              title={collapsed ? item.title : undefined}
            >
              <Icon className={cn('h-4 w-4 shrink-0', isActive ? 'text-white' : 'text-[#E3E8EF]/70')} />
              {!collapsed && <span className="truncate">{item.title}</span>}
              {!collapsed && item.badge && (
                <span className="ml-auto rounded-full bg-[#12315C] px-1.5 py-0.5 text-[10px] text-[#3B82F6]">
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}
      </div>

      {/* Toggle Button when Collapsed */}
      {collapsed && (
        <div className="p-2 border-t border-[#12315C]">
          <button
            onClick={onToggleCollapsed}
            className="flex w-full h-8 items-center justify-center rounded-[6px] text-[#E3E8EF]/70 hover:bg-[#12315C] hover:text-white"
            title="Expand sidebar"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Footer Role Identifier */}
      {!collapsed && (
        <div className="border-t border-[#12315C] p-3 text-[11px] text-[#E3E8EF]/50">
          <div className="flex items-center justify-between">
            <span className="uppercase tracking-wider">Role Scope</span>
            <span className="capitalize font-mono text-white text-[10px]">
              {role.replace('_', ' ')}
            </span>
          </div>
        </div>
      )}
    </aside>
  );
}
