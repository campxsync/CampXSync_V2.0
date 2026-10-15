/**
 * CampXSync Typed & Versioned User Preference Foundation (Section 5.1)
 *
 * Precedence Order:
 * 1. Security / Accessibility constraints (e.g. system high contrast or reduced motion)
 * 2. Tenant-enforced defaults
 * 3. Authenticated User + Role settings
 * 4. User + Module settings (role-scoped)
 * 5. Product defaults
 */

export interface UserPreferences {
  version: 1;
  theme: 'light' | 'dark' | 'system';
  sidebarCollapsed: boolean;
  tableDensity: 'comfortable' | 'compact';
  defaultDateRangeDays: 7 | 14 | 30;
  defaultAttendanceFilter: 'all' | 'unmarked' | 'shortage';
  accessibility: {
    highContrast: boolean;
    reducedMotion: boolean;
    largeFont: boolean;
  };
  dashboardWidgets: {
    order: string[];
    hidden: string[];
  };
}

export const PRODUCT_DEFAULTS: UserPreferences = {
  version: 1,
  theme: 'light',
  sidebarCollapsed: false,
  tableDensity: 'comfortable',
  defaultDateRangeDays: 14,
  defaultAttendanceFilter: 'all',
  accessibility: {
    highContrast: false,
    reducedMotion: false,
    largeFont: false,
  },
  dashboardWidgets: {
    order: ['kpis', 'trends', 'shortage', 'sessions'],
    hidden: [],
  },
};

/**
 * Validate and sanitize preference payload (allowlist only).
 * Never allows unauthorized keys, arbitrary code, or secrets.
 */
export function sanitizePreferences(
  input: unknown,
  fallback: UserPreferences = PRODUCT_DEFAULTS
): UserPreferences {
  if (!input || typeof input !== 'object') {
    return fallback;
  }

  const obj = input as Partial<UserPreferences>;
  const theme = obj.theme === 'dark' || obj.theme === 'light' || obj.theme === 'system'
    ? obj.theme
    : fallback.theme;

  const tableDensity = obj.tableDensity === 'compact' || obj.tableDensity === 'comfortable'
    ? obj.tableDensity
    : fallback.tableDensity;

  const defaultDateRangeDays = [7, 14, 30].includes(obj.defaultDateRangeDays as any)
    ? (obj.defaultDateRangeDays as 7 | 14 | 30)
    : fallback.defaultDateRangeDays;

  const defaultAttendanceFilter = ['all', 'unmarked', 'shortage'].includes(obj.defaultAttendanceFilter as any)
    ? (obj.defaultAttendanceFilter as 'all' | 'unmarked' | 'shortage')
    : fallback.defaultAttendanceFilter;

  const accessibility = {
    highContrast: Boolean(obj.accessibility?.highContrast),
    reducedMotion: Boolean(obj.accessibility?.reducedMotion),
    largeFont: Boolean(obj.accessibility?.largeFont),
  };

  const allowedWidgets = ['kpis', 'trends', 'shortage', 'sessions', 'audit', 'institutes'];
  const order = Array.isArray(obj.dashboardWidgets?.order)
    ? obj.dashboardWidgets.order.filter(w => allowedWidgets.includes(w))
    : fallback.dashboardWidgets.order;

  const hidden = Array.isArray(obj.dashboardWidgets?.hidden)
    ? obj.dashboardWidgets.hidden.filter(w => allowedWidgets.includes(w))
    : fallback.dashboardWidgets.hidden;

  return {
    version: 1,
    theme,
    sidebarCollapsed: Boolean(obj.sidebarCollapsed),
    tableDensity,
    defaultDateRangeDays,
    defaultAttendanceFilter,
    accessibility,
    dashboardWidgets: {
      order: order.length > 0 ? order : fallback.dashboardWidgets.order,
      hidden,
    },
  };
}

export interface PreferenceSaveResult {
  success: boolean;
  isAccountSynced: boolean;
  message?: string;
}

/**
 * Preference Storage Adapter interface
 */
export interface PreferenceAdapter {
  load(userId: string, role: string, tenantId: string | null): Promise<UserPreferences>;
  save(userId: string, role: string, tenantId: string | null, prefs: UserPreferences): Promise<PreferenceSaveResult>;
}

/**
 * Default Client Preference Adapter
 * Uses localStorage with explicit isolation by (tenantId:userId:role).
 * Discloses that account sync requires database preference schema migration.
 */
export class ClientScopedPreferenceAdapter implements PreferenceAdapter {
  private getKey(userId: string, role: string, tenantId: string | null): string {
    const tid = tenantId || 'platform';
    return `campx_pref_${tid}_${userId}_${role}`;
  }

  async load(userId: string, role: string, tenantId: string | null): Promise<UserPreferences> {
    if (typeof window === 'undefined') return PRODUCT_DEFAULTS;
    try {
      const raw = localStorage.getItem(this.getKey(userId, role, tenantId));
      if (!raw) return PRODUCT_DEFAULTS;
      const parsed = JSON.parse(raw);
      return sanitizePreferences(parsed, PRODUCT_DEFAULTS);
    } catch {
      return PRODUCT_DEFAULTS;
    }
  }

  async save(
    userId: string,
    role: string,
    tenantId: string | null,
    prefs: UserPreferences
  ): Promise<PreferenceSaveResult> {
    if (typeof window === 'undefined') {
      return { success: false, isAccountSynced: false, message: 'Server context cannot save local storage' };
    }
    try {
      const sanitized = sanitizePreferences(prefs);
      localStorage.setItem(this.getKey(userId, role, tenantId), JSON.stringify(sanitized));
      // Transparently disclose that local persistence succeeded, but backend persistence is pending GO MIGRATION
      return {
        success: true,
        isAccountSynced: false,
        message: 'Saved to this device. (Account sync pending approved database storage).',
      };
    } catch (e) {
      return { success: false, isAccountSynced: false, message: 'Failed to write to device storage.' };
    }
  }
}
