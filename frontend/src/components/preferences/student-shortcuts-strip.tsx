'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  PortalShortcut,
  ServerPreferenceAdapter,
  UserPreferences,
} from '@/lib/preferences/preference-model';

export interface StudentShortcutsStripProps {
  userId: string;
  tenantId: string | null;
}

/** STM-10-US-013 — durable student portal shortcuts. */
export function StudentShortcutsStrip({ userId, tenantId }: StudentShortcutsStripProps) {
  const [shortcuts, setShortcuts] = useState<PortalShortcut[]>([]);
  const adapter = React.useMemo(() => new ServerPreferenceAdapter(), []);

  useEffect(() => {
    let cancelled = false;
    adapter.load(userId, 'student', tenantId).then((prefs) => {
      if (!cancelled) setShortcuts(prefs.shortcuts || []);
    });
    const onChange = (event: Event) => {
      const detail = (event as CustomEvent<UserPreferences>).detail;
      if (detail) setShortcuts(detail.shortcuts || []);
    };
    window.addEventListener('campx-preferences-changed', onChange);
    return () => {
      cancelled = true;
      window.removeEventListener('campx-preferences-changed', onChange);
    };
  }, [adapter, userId, tenantId]);

  if (!shortcuts.length) {
    return (
      <section
        className="rounded-[8px] border border-dashed border-[#E3E8EF] bg-white p-3"
        data-testid="student-shortcuts-empty"
      >
        <h2 className="text-sm font-semibold text-[#0B1F3A]">My shortcuts</h2>
        <p className="mt-1 text-xs text-[#6B7688]">
          No shortcuts saved yet. Open Preferences to add internal portal links.
        </p>
      </section>
    );
  }

  return (
    <section
      className="rounded-[8px] border border-[#E3E8EF] bg-white p-3"
      data-testid="student-shortcuts-strip"
      aria-label="Saved shortcuts"
    >
      <h2 className="text-sm font-semibold text-[#0B1F3A]">My shortcuts</h2>
      <ul className="mt-2 flex flex-wrap gap-2">
        {shortcuts.map((item) => (
          <li key={item.id}>
            <Link
              href={item.path}
              className="inline-flex items-center rounded-[6px] border border-[#B8D3FC] bg-[#F0F6FF] px-2.5 py-1 text-xs font-medium text-[#1F6FEB]"
              data-testid={`student-shortcut-${item.id}`}
            >
              {item.label}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
