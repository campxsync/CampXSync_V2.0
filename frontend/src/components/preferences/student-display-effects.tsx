'use client';

import { useEffect } from 'react';
import {
  ServerPreferenceAdapter,
  UserPreferences,
} from '@/lib/preferences/preference-model';

export interface StudentDisplayEffectsProps {
  userId: string;
  tenantId: string | null;
}

/** STM-10-US-014 — apply durable language/display preferences to the document. */
export function StudentDisplayEffects({ userId, tenantId }: StudentDisplayEffectsProps) {
  useEffect(() => {
    const adapter = new ServerPreferenceAdapter();
    const apply = (prefs: UserPreferences) => {
      document.documentElement.lang = prefs.locale || 'en-IN';
      document.documentElement.dataset.timezone = prefs.timezone || 'Asia/Kolkata';
      document.documentElement.dataset.tableDensity = prefs.tableDensity || 'comfortable';
      if (prefs.theme === 'dark') {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
      if (prefs.accessibility?.reducedMotion) {
        document.documentElement.classList.add('reduce-motion');
      } else {
        document.documentElement.classList.remove('reduce-motion');
      }
      if (prefs.accessibility?.highContrast) {
        document.documentElement.classList.add('high-contrast');
      } else {
        document.documentElement.classList.remove('high-contrast');
      }
      document.documentElement.setAttribute('data-testid', 'student-display-prefs-applied');
      document.documentElement.dataset.locale = prefs.locale || 'en-IN';
    };

    adapter.load(userId, 'student', tenantId).then(apply);
    const onChange = (event: Event) => {
      const detail = (event as CustomEvent<UserPreferences>).detail;
      if (detail) apply(detail);
    };
    window.addEventListener('campx-preferences-changed', onChange);
    return () => window.removeEventListener('campx-preferences-changed', onChange);
  }, [userId, tenantId]);

  return null;
}
