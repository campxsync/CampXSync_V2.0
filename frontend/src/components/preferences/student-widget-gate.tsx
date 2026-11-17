'use client';

import React, { useEffect, useState } from 'react';
import {
  ROLE_DEFAULT_WIDGETS,
  ServerPreferenceAdapter,
  UserPreferences,
} from '@/lib/preferences/preference-model';

export interface StudentWidgetGateProps {
  widgetId: string;
  userId: string;
  tenantId: string | null;
  children: React.ReactNode;
}

/**
 * STM-10-US-012 — show/hide student dashboard widgets from durable portal preferences.
 */
export function StudentWidgetGate({ widgetId, userId, tenantId, children }: StudentWidgetGateProps) {
  const [visible, setVisible] = useState(true);
  const [orderIndex, setOrderIndex] = useState(0);
  const adapter = React.useMemo(() => new ServerPreferenceAdapter(), []);

  const applyPrefs = (prefs: UserPreferences) => {
    const cfg =
      prefs.roles?.student ||
      prefs.dashboardWidgets ||
      ROLE_DEFAULT_WIDGETS.student;
    const hidden = cfg.hidden || [];
    setVisible(!hidden.includes(widgetId));
    const order = cfg.order || ROLE_DEFAULT_WIDGETS.student.order;
    const idx = order.indexOf(widgetId);
    setOrderIndex(idx >= 0 ? idx : 999);
  };

  useEffect(() => {
    let cancelled = false;
    adapter.load(userId, 'student', tenantId).then((prefs) => {
      if (!cancelled) applyPrefs(prefs);
    });

    const onChange = (event: Event) => {
      const detail = (event as CustomEvent<UserPreferences>).detail;
      if (detail) applyPrefs(detail);
    };
    window.addEventListener('campx-preferences-changed', onChange);
    return () => {
      cancelled = true;
      window.removeEventListener('campx-preferences-changed', onChange);
    };
  }, [adapter, userId, tenantId, widgetId]);

  if (!visible) {
    return null;
  }

  return (
    <div
      data-testid={`student-widget-${widgetId}`}
      data-widget-id={widgetId}
      data-widget-order={orderIndex}
      style={{ order: orderIndex }}
    >
      {children}
    </div>
  );
}
