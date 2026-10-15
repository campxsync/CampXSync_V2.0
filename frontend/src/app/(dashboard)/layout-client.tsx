'use client';

import React, { useState } from 'react';
import { Sidebar } from '@/components/shell/sidebar';
import { Topbar } from '@/components/shell/topbar';
import { Breadcrumbs } from '@/components/shell/breadcrumbs';
import { PreferenceModal } from '@/components/preferences/preference-modal';
import { ResolvedUserSession } from '@/lib/auth/resolve-role';

export interface DashboardClientLayoutProps {
  session: ResolvedUserSession;
  children: React.ReactNode;
}

export function DashboardClientLayout({ session, children }: DashboardClientLayoutProps) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [showPreferences, setShowPreferences] = useState(false);

  return (
    <div className="flex h-screen w-full overflow-hidden bg-[#F6F8FB]">
      {/* Role-Aware Collapsible Sidebar */}
      <Sidebar
        role={session.role}
        tenantName={session.tenantName}
        collapsed={sidebarCollapsed}
        onToggleCollapsed={() => setSidebarCollapsed(!sidebarCollapsed)}
      />

      {/* Main View Area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Sticky Topbar */}
        <Topbar
          displayName={session.displayName}
          email={session.email}
          role={session.role}
          tenantName={session.tenantName}
          onOpenPreferences={() => setShowPreferences(true)}
        />

        {/* Content Container */}
        <main className="flex-1 overflow-y-auto p-6 md:p-8">
          <div className="mx-auto max-w-[1280px]">
            <Breadcrumbs />
            {children}
          </div>
        </main>
      </div>

      {/* Workspace Preferences Modal */}
      <PreferenceModal
        isOpen={showPreferences}
        onClose={() => setShowPreferences(false)}
        userId={session.userId}
        role={session.role}
        tenantId={session.tenantId}
      />
    </div>
  );
}
