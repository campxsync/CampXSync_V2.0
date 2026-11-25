'use client';

import React, { useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Sidebar } from '@/components/shell/sidebar';
import { Topbar } from '@/components/shell/topbar';
import { Breadcrumbs } from '@/components/shell/breadcrumbs';
import { PreferenceModal } from '@/components/preferences/preference-modal';
import { LogoutBroadcastListener } from '@/components/auth/logout-broadcast-listener';
import { ResolvedUserSession } from '@/lib/auth/resolve-role';
import { isSubscriptionLimitedPathAllowed } from '@/lib/auth/subscription-limited-paths';

export interface DashboardClientLayoutProps {
  session: ResolvedUserSession;
  children: React.ReactNode;
}

export function DashboardClientLayout({ session, children }: DashboardClientLayoutProps) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [showPreferences, setShowPreferences] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const limited =
    session.role === 'institution_admin' && session.subscriptionAllowsAccess === false;

  useEffect(() => {
    if (!limited) return;
    if (!isSubscriptionLimitedPathAllowed(pathname)) {
      router.replace('/access-denied?reason=subscription_inactive');
    }
  }, [limited, pathname, router]);

  return (
    <div className="flex h-screen w-full overflow-hidden bg-[#F6F8FB]" data-subscription-gate={limited ? 'limited' : 'full'}>
      <LogoutBroadcastListener />
      <Sidebar
        role={session.role}
        tenantName={session.tenantName}
        collapsed={sidebarCollapsed}
        onToggleCollapsed={() => setSidebarCollapsed(!sidebarCollapsed)}
        mobileOpen={mobileMenuOpen}
        onCloseMobile={() => setMobileMenuOpen(false)}
        subscriptionLimited={limited}
      />

      <div className="flex flex-1 flex-col overflow-hidden min-w-0">
        <Topbar
          displayName={session.displayName}
          email={session.email}
          role={session.role}
          tenantName={session.tenantName}
          onOpenPreferences={() => setShowPreferences(true)}
          onToggleMobileMenu={() => setMobileMenuOpen(!mobileMenuOpen)}
        />

        <main className="flex-1 overflow-y-auto p-4 sm:p-6 md:p-8">
          <div className="mx-auto max-w-[1280px]">
            {limited ? (
              <div
                className="mb-4 rounded-[8px] border border-[#F5D9A8] bg-[#FFF8EB] px-3 py-2 text-xs text-[#8A5A00]"
                data-testid="iadm04-subscription-limited-banner"
              >
                Commercial access is unavailable until the subscription is TRIAL or ACTIVE. You can
                open the subscription desk, your institute profile, or sign out.
              </div>
            ) : null}
            <Breadcrumbs />
            {children}
          </div>
        </main>
      </div>

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
