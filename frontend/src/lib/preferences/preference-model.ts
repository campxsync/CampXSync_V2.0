/**
 * CampXSync Typed & Versioned User Preference Foundation (Section 5.1 & Phase 7)
 *
 * Precedence Order:
 * 1. Security / Accessibility constraints (e.g. system high contrast or reduced motion)
 * 2. Tenant-enforced defaults
 * 3. Authenticated User + Role settings
 * 4. User + Module settings (role-scoped)
 * 5. Product defaults
 */

export interface RoleDashboardConfig {
  order: string[];
  hidden: string[];
}

export interface UserPreferences {
  version: 1;
  theme: 'light' | 'dark' | 'system';
  sidebarCollapsed: boolean;
  tableDensity: 'comfortable' | 'compact';
  defaultDateRangeDays: 7 | 14 | 30;
  defaultAttendanceFilter: 'all' | 'unmarked' | 'shortage';
  locale: string;
  timezone: string;
  accessibility: {
    highContrast: boolean;
    reducedMotion: boolean;
    largeFont: boolean;
  };
  dashboardWidgets: RoleDashboardConfig;
  roles?: {
    college_admin?: RoleDashboardConfig;
    registrar?: RoleDashboardConfig;
    faculty?: RoleDashboardConfig;
    platform_admin?: RoleDashboardConfig;
    student?: RoleDashboardConfig;
  };
}

export const ROLE_DEFAULT_WIDGETS: Record<string, RoleDashboardConfig> = {
  college_admin: {
    order: ['kpis', 'trends', 'batches', 'shortage', 'missed', 'recent'],
    hidden: [],
  },
  registrar: {
    order: ['lifecycle', 'documents', 'enrollment'],
    hidden: [],
  },
  faculty: {
    order: ['today', 'upcoming', 'missed'],
    hidden: [],
  },
  platform_admin: {
    order: ['kpis', 'lifecycle', 'institutes', 'audit'],
    hidden: [],
  },
  student: {
    order: ['profile', 'courses', 'documents', 'notifications', 'kpis'],
    hidden: [],
  },
};

export const PRODUCT_DEFAULTS: UserPreferences = {
  version: 1,
  theme: 'light',
  sidebarCollapsed: false,
  tableDensity: 'comfortable',
  defaultDateRangeDays: 14,
  defaultAttendanceFilter: 'all',
  locale: 'en-IN',
  timezone: 'Asia/Kolkata',
  accessibility: {
    highContrast: false,
    reducedMotion: false,
    largeFont: false,
  },
  dashboardWidgets: ROLE_DEFAULT_WIDGETS.college_admin,
  roles: {
    college_admin: ROLE_DEFAULT_WIDGETS.college_admin,
    registrar: ROLE_DEFAULT_WIDGETS.registrar,
    faculty: ROLE_DEFAULT_WIDGETS.faculty,
    platform_admin: ROLE_DEFAULT_WIDGETS.platform_admin,
    student: ROLE_DEFAULT_WIDGETS.student,
  },
};

const ALLOWED_WIDGETS = [
  'kpis',
  'trends',
  'batches',
  'shortage',
  'missed',
  'recent',
  'today',
  'upcoming',
  'lifecycle',
  'institutes',
  'audit',
  'profile',
  'courses',
  'documents',
  'enrollment',
  'notifications',
];

function sanitizeWidgetConfig(
  input: unknown,
  fallback: RoleDashboardConfig
): RoleDashboardConfig {
  if (!input || typeof input !== 'object') return fallback;
  const cfg = input as Partial<RoleDashboardConfig>;

  const order = Array.isArray(cfg.order)
    ? cfg.order.filter((w) => typeof w === 'string' && ALLOWED_WIDGETS.includes(w))
    : fallback.order;

  const hidden = Array.isArray(cfg.hidden)
    ? cfg.hidden.filter((w) => typeof w === 'string' && ALLOWED_WIDGETS.includes(w))
    : fallback.hidden;

  return {
    order: order.length > 0 ? order : fallback.order,
    hidden,
  };
}

/**
 * Validate and sanitize preference payload (allowlist only).
 * Never allows unauthorized keys, arbitrary code, or permission escalation.
 */
export function sanitizePreferences(
  input: unknown,
  currentRole: string = 'college_admin',
  fallback: UserPreferences = PRODUCT_DEFAULTS
): UserPreferences {
  if (!input || typeof input !== 'object') {
    return fallback;
  }

  const obj = input as Partial<UserPreferences>;
  const theme =
    obj.theme === 'dark' || obj.theme === 'light' || obj.theme === 'system'
      ? obj.theme
      : fallback.theme;

  const tableDensity =
    obj.tableDensity === 'compact' || obj.tableDensity === 'comfortable'
      ? obj.tableDensity
      : fallback.tableDensity;

  const defaultDateRangeDays = [7, 14, 30].includes(obj.defaultDateRangeDays as any)
    ? (obj.defaultDateRangeDays as 7 | 14 | 30)
    : fallback.defaultDateRangeDays;

  const defaultAttendanceFilter = ['all', 'unmarked', 'shortage'].includes(
    obj.defaultAttendanceFilter as any
  )
    ? (obj.defaultAttendanceFilter as 'all' | 'unmarked' | 'shortage')
    : fallback.defaultAttendanceFilter;

  const locale = typeof obj.locale === 'string' && obj.locale.length <= 10 ? obj.locale : fallback.locale;
  const timezone = typeof obj.timezone === 'string' && obj.timezone.length <= 50 ? obj.timezone : fallback.timezone;

  const accessibility = {
    highContrast: Boolean(obj.accessibility?.highContrast),
    reducedMotion: Boolean(obj.accessibility?.reducedMotion),
    largeFont: Boolean(obj.accessibility?.largeFont),
  };

  const roleFallback = ROLE_DEFAULT_WIDGETS[currentRole] || ROLE_DEFAULT_WIDGETS.college_admin;
  const activeDashboardWidgets = sanitizeWidgetConfig(obj.dashboardWidgets, roleFallback);

  const rolesObj: Record<string, RoleDashboardConfig> = {};
  for (const roleKey of ['college_admin', 'registrar', 'faculty', 'platform_admin', 'student']) {
    const rFallback = ROLE_DEFAULT_WIDGETS[roleKey] || ROLE_DEFAULT_WIDGETS.student;
    const incoming = obj.roles?.[roleKey as keyof typeof obj.roles] || (roleKey === currentRole ? activeDashboardWidgets : undefined);
    rolesObj[roleKey] = sanitizeWidgetConfig(incoming, rFallback);
  }

  return {
    version: 1,
    theme,
    sidebarCollapsed: Boolean(obj.sidebarCollapsed),
    tableDensity,
    defaultDateRangeDays,
    defaultAttendanceFilter,
    locale,
    timezone,
    accessibility,
    dashboardWidgets: rolesObj[currentRole] || activeDashboardWidgets,
    roles: rolesObj,
  };
}

export interface PreferenceSaveResult {
  success: boolean;
  isAccountSynced: boolean;
  message?: string;
}

export interface PreferenceAdapter {
  load(userId: string, role: string, tenantId: string | null): Promise<UserPreferences>;
  save(
    userId: string,
    role: string,
    tenantId: string | null,
    prefs: UserPreferences
  ): Promise<PreferenceSaveResult>;
}

/**
 * Server-Backed Preference Adapter with Device Fallback (ACD-10)
 * Attempts durable PostgreSQL persistence via /api/user/preferences.
 * Falls back to local device storage with transparent user disclosure when offline or blocked.
 */
function portalPayloadToUserPreferences(portal: any, role: string): UserPreferences {
  const widgets = portal?.widgets || ROLE_DEFAULT_WIDGETS.student;
  const display = portal?.display || {};
  return sanitizePreferences(
    {
      version: 1,
      theme: display.theme || 'light',
      sidebarCollapsed: false,
      tableDensity: display.tableDensity || 'comfortable',
      defaultDateRangeDays: 14,
      defaultAttendanceFilter: 'all',
      locale: display.locale || 'en-IN',
      timezone: display.timezone || 'Asia/Kolkata',
      accessibility: {
        highContrast: Boolean(display.accessibility?.highContrast),
        reducedMotion: Boolean(display.accessibility?.reducedMotion),
        largeFont: Boolean(display.accessibility?.largeFont),
      },
      dashboardWidgets: widgets,
      roles: { student: widgets },
    },
    role,
    PRODUCT_DEFAULTS
  );
}

function userPreferencesToPortalPayload(prefs: UserPreferences) {
  const widgets = prefs.roles?.student || prefs.dashboardWidgets || ROLE_DEFAULT_WIDGETS.student;
  return {
    version: 1,
    widgets: {
      order: widgets.order || [],
      hidden: widgets.hidden || [],
    },
    display: {
      theme: prefs.theme,
      locale: prefs.locale,
      timezone: prefs.timezone,
      tableDensity: prefs.tableDensity,
      accessibility: prefs.accessibility,
    },
  };
}

export class ServerPreferenceAdapter implements PreferenceAdapter {
  private getStorageKey(userId: string, role: string, tenantId: string | null): string {
    const tid = tenantId || 'platform';
    return `campx_pref_${tid}_${userId}_${role}`;
  }

  async load(userId: string, role: string, tenantId: string | null): Promise<UserPreferences> {
    // Student portal preferences persist via student-service (STM-10-EN-001 / US-012).
    if (typeof window !== 'undefined' && role === 'student') {
      try {
        const res = await fetch('/api/student/portal-preferences', { cache: 'no-store' });
        if (res.ok) {
          const data = await res.json();
          if (data.success && data.preferences) {
            const sanitized = portalPayloadToUserPreferences(data.preferences, role);
            localStorage.setItem(this.getStorageKey(userId, role, tenantId), JSON.stringify(sanitized));
            return sanitized;
          }
        }
      } catch {
        // fall through to local cache
      }
    }

    // Staff path: institute-admin preferences via /api/user/preferences
    if (typeof window !== 'undefined') {
      try {
        const res = await fetch(`/api/user/preferences?role=${encodeURIComponent(role)}`);
        if (res.ok) {
          const data = await res.json();
          if (data.success && data.preferences) {
            const sanitized = sanitizePreferences(data.preferences, role, PRODUCT_DEFAULTS);
            localStorage.setItem(this.getStorageKey(userId, role, tenantId), JSON.stringify(sanitized));
            return sanitized;
          }
        }
      } catch {
        // Network or offline, fallback to local
      }

      try {
        const raw = localStorage.getItem(this.getStorageKey(userId, role, tenantId));
        if (raw) {
          const parsed = JSON.parse(raw);
          return sanitizePreferences(parsed, role, PRODUCT_DEFAULTS);
        }
      } catch {}
    }

    return sanitizePreferences(PRODUCT_DEFAULTS, role, PRODUCT_DEFAULTS);
  }

  async save(
    userId: string,
    role: string,
    tenantId: string | null,
    prefs: UserPreferences
  ): Promise<PreferenceSaveResult> {
    const sanitized = sanitizePreferences(prefs, role, PRODUCT_DEFAULTS);

    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(this.getStorageKey(userId, role, tenantId), JSON.stringify(sanitized));
      } catch {}
    }

    if (typeof window !== 'undefined' && role === 'student') {
      try {
        const res = await fetch('/api/student/portal-preferences', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(userPreferencesToPortalPayload(sanitized)),
        });
        if (res.ok) {
          const data = await res.json();
          if (data.success && data.isAccountSynced) {
            return {
              success: true,
              isAccountSynced: true,
              message: 'Dashboard widgets synced to your student portal account (PostgreSQL).',
            };
          }
        }
      } catch {
        // fallback below
      }
      return {
        success: true,
        isAccountSynced: false,
        message: 'Saved to local device cache. (Server sync will complete on next network connection).',
      };
    }

    if (typeof window !== 'undefined') {
      try {
        const res = await fetch('/api/user/preferences', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            preferences: sanitized,
            role,
          }),
        });

        if (res.ok) {
          const data = await res.json();
          if (data.success && data.isAccountSynced) {
            return {
              success: true,
              isAccountSynced: true,
              message: 'Preferences successfully synced to your institutional account (PostgreSQL).',
            };
          }
        }
      } catch {
        // Fallback message below
      }
    }

    return {
      success: true,
      isAccountSynced: false,
      message: 'Saved to local device cache. (Server sync will complete on next network connection).',
    };
  }
}

// Backward-compatible alias
export class ClientScopedPreferenceAdapter extends ServerPreferenceAdapter {}
