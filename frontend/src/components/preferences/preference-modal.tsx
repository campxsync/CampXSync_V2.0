'use client';

import React, { useState, useEffect } from 'react';
import {
  X,
  Check,
  RotateCcw,
  Monitor,
  Sun,
  Moon,
  Info,
  Sliders,
  Eye,
  EyeOff,
  ArrowUp,
  ArrowDown,
  Database,
  CloudCheck,
} from 'lucide-react';
import {
  UserPreferences,
  PRODUCT_DEFAULTS,
  ROLE_DEFAULT_WIDGETS,
  ServerPreferenceAdapter,
  sanitizePreferences,
} from '@/lib/preferences/preference-model';
import { Button } from '@/components/ui/button';

export interface PreferenceModalProps {
  isOpen: boolean;
  onClose: () => void;
  userId: string;
  role: string;
  tenantId: string | null;
  onPreferencesSaved?: (prefs: UserPreferences) => void;
}

const WIDGET_LABELS: Record<string, string> = {
  kpis: 'Key Performance Indicators (KPI Cards)',
  trends: '14-Day Attendance Trend Chart',
  batches: 'Attendance by Batch & Subject',
  shortage: 'Attendance Shortage Alerts (<75%)',
  missed: 'Missed & Unsubmitted Sessions Alert',
  recent: 'Recent Timetable & Attendance Sessions',
  today: "Today's Timetable Slots & Take Attendance",
  upcoming: 'Upcoming Schedule Slots',
  lifecycle: 'Institutes by Lifecycle Status',
  institutes: 'Institutes & User Distribution Table',
  audit: 'Recent Platform Audit Events',
};

export function PreferenceModal({
  isOpen,
  onClose,
  userId,
  role,
  tenantId,
  onPreferencesSaved,
}: PreferenceModalProps) {
  const adapter = React.useMemo(() => new ServerPreferenceAdapter(), []);
  const [preferences, setPreferences] = useState<UserPreferences>(PRODUCT_DEFAULTS);
  const [activeTab, setActiveTab] = useState<'display' | 'widgets'>('display');
  const [feedback, setFeedback] = useState<{ message: string; isSynced: boolean } | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (isOpen) {
      adapter.load(userId, role, tenantId).then((p) => {
        setPreferences(p);
      });
      setFeedback(null);
    }
  }, [isOpen, userId, role, tenantId, adapter]);

  if (!isOpen) return null;

  const currentRoleWidgets =
    preferences.roles?.[role as keyof typeof preferences.roles] ||
    ROLE_DEFAULT_WIDGETS[role] ||
    preferences.dashboardWidgets;

  const handleToggleWidget = (widgetId: string) => {
    const isHidden = currentRoleWidgets.hidden.includes(widgetId);
    const newHidden = isHidden
      ? currentRoleWidgets.hidden.filter((w) => w !== widgetId)
      : [...currentRoleWidgets.hidden, widgetId];

    const updatedRoleWidget = {
      ...currentRoleWidgets,
      hidden: newHidden,
    };

    setPreferences({
      ...preferences,
      dashboardWidgets: updatedRoleWidget,
      roles: {
        ...preferences.roles,
        [role]: updatedRoleWidget,
      },
    });
  };

  const handleMoveWidget = (index: number, direction: 'up' | 'down') => {
    const order = [...currentRoleWidgets.order];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= order.length) return;

    const temp = order[index];
    order[index] = order[targetIndex];
    order[targetIndex] = temp;

    const updatedRoleWidget = {
      ...currentRoleWidgets,
      order,
    };

    setPreferences({
      ...preferences,
      dashboardWidgets: updatedRoleWidget,
      roles: {
        ...preferences.roles,
        [role]: updatedRoleWidget,
      },
    });
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const res = await adapter.save(userId, role, tenantId, preferences);
      setFeedback({
        message: res.message || 'Preferences updated.',
        isSynced: res.isAccountSynced,
      });

      // Apply theme & accessibility to DOM
      if (preferences.theme === 'dark') {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }

      if (preferences.accessibility.reducedMotion) {
        document.documentElement.classList.add('reduce-motion');
      } else {
        document.documentElement.classList.remove('reduce-motion');
      }

      // Dispatch window event for live dashboard reactivity
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent('campx-preferences-changed', {
            detail: preferences,
          })
        );
      }

      if (onPreferencesSaved) {
        onPreferencesSaved(preferences);
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = () => {
    const defaults = sanitizePreferences(PRODUCT_DEFAULTS, role, PRODUCT_DEFAULTS);
    setPreferences(defaults);
    setFeedback({
      message: 'Restored all settings to product defaults.',
      isSynced: false,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-xl rounded-[12px] bg-white p-6 shadow-xl text-[#1B2433]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#E3E8EF] pb-4">
          <div>
            <h3 className="text-base font-bold text-[#0B1F3A]">Workspace Preferences</h3>
            <p className="text-xs text-[#6B7688]">
              Personalized display, accessibility, and dashboard layout for{' '}
              <span className="font-semibold text-[#1F6FEB]">{role.replace('_', ' ')}</span>
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-[6px] p-1 text-[#6B7688] hover:bg-[#F6F8FB] hover:text-[#1B2433]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-[#E3E8EF] mt-3">
          <button
            type="button"
            onClick={() => setActiveTab('display')}
            className={`py-2 px-4 text-xs font-semibold border-b-2 transition-colors ${
              activeTab === 'display'
                ? 'border-[#1F6FEB] text-[#1F6FEB]'
                : 'border-transparent text-[#6B7688] hover:text-[#0B1F3A]'
            }`}
          >
            Display & Accessibility
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('widgets')}
            className={`py-2 px-4 text-xs font-semibold border-b-2 transition-colors ${
              activeTab === 'widgets'
                ? 'border-[#1F6FEB] text-[#1F6FEB]'
                : 'border-transparent text-[#6B7688] hover:text-[#0B1F3A]'
            }`}
          >
            Dashboard Widgets ({currentRoleWidgets.order.length})
          </button>
        </div>

        {/* Tab Content */}
        <div className="mt-4 space-y-5 max-h-[58vh] overflow-y-auto px-1">
          {activeTab === 'display' ? (
            <>
              {/* Color Theme */}
              <div>
                <label className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider">
                  Color Theme
                </label>
                <div className="mt-2 grid grid-cols-3 gap-2">
                  {(['light', 'dark', 'system'] as const).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setPreferences({ ...preferences, theme: t })}
                      className={`flex items-center justify-center gap-2 rounded-[8px] border p-2.5 text-xs font-medium capitalize transition-colors ${
                        preferences.theme === t
                          ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB] font-semibold'
                          : 'border-[#E3E8EF] bg-white text-[#6B7688] hover:bg-[#F6F8FB]'
                      }`}
                    >
                      {t === 'light' && <Sun className="h-3.5 w-3.5" />}
                      {t === 'dark' && <Moon className="h-3.5 w-3.5" />}
                      {t === 'system' && <Monitor className="h-3.5 w-3.5" />}
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              {/* Table Density */}
              <div>
                <label className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider">
                  Table Row Density
                </label>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  {(['comfortable', 'compact'] as const).map((d) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => setPreferences({ ...preferences, tableDensity: d })}
                      className={`flex items-center justify-center rounded-[8px] border p-2.5 text-xs font-medium capitalize transition-colors ${
                        preferences.tableDensity === d
                          ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB] font-semibold'
                          : 'border-[#E3E8EF] bg-white text-[#6B7688] hover:bg-[#F6F8FB]'
                      }`}
                    >
                      {d}
                    </button>
                  ))}
                </div>
              </div>

              {/* Default Date Range */}
              <div>
                <label className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider">
                  Default Trend & Report Window
                </label>
                <div className="mt-2 grid grid-cols-3 gap-2">
                  {([7, 14, 30] as const).map((days) => (
                    <button
                      key={days}
                      type="button"
                      onClick={() => setPreferences({ ...preferences, defaultDateRangeDays: days })}
                      className={`flex items-center justify-center rounded-[8px] border p-2.5 text-xs font-medium transition-colors ${
                        preferences.defaultDateRangeDays === days
                          ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB] font-semibold'
                          : 'border-[#E3E8EF] bg-white text-[#6B7688] hover:bg-[#F6F8FB]'
                      }`}
                    >
                      {days} Days
                    </button>
                  ))}
                </div>
              </div>

              {/* Accessibility Options */}
              <div>
                <label className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider">
                  Accessibility & Display Controls
                </label>
                <div className="mt-2 space-y-2">
                  <label className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] p-2.5 text-xs text-[#1B2433] hover:bg-[#F6F8FB] cursor-pointer">
                    <div>
                      <span className="font-semibold block text-[#0B1F3A]">High Contrast Mode</span>
                      <span className="text-[11px] text-[#6B7688]">
                        Enhanced contrast borders meeting WCAG AAA standard
                      </span>
                    </div>
                    <input
                      type="checkbox"
                      checked={preferences.accessibility.highContrast}
                      onChange={(e) =>
                        setPreferences({
                          ...preferences,
                          accessibility: {
                            ...preferences.accessibility,
                            highContrast: e.target.checked,
                          },
                        })
                      }
                      className="rounded border-[#E3E8EF] text-[#1F6FEB] focus:ring-[#1F6FEB]"
                    />
                  </label>

                  <label className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] p-2.5 text-xs text-[#1B2433] hover:bg-[#F6F8FB] cursor-pointer">
                    <div>
                      <span className="font-semibold block text-[#0B1F3A]">Reduce Motion</span>
                      <span className="text-[11px] text-[#6B7688]">
                        Disable decorative transitions and animations
                      </span>
                    </div>
                    <input
                      type="checkbox"
                      checked={preferences.accessibility.reducedMotion}
                      onChange={(e) =>
                        setPreferences({
                          ...preferences,
                          accessibility: {
                            ...preferences.accessibility,
                            reducedMotion: e.target.checked,
                          },
                        })
                      }
                      className="rounded border-[#E3E8EF] text-[#1F6FEB] focus:ring-[#1F6FEB]"
                    />
                  </label>
                </div>
              </div>
            </>
          ) : (
            /* Widgets Tab */
            <div className="space-y-3">
              <p className="text-xs text-[#6B7688]">
                Customize which widgets appear on your dashboard and their display order. Mandatory
                security alerts and essential actions cannot be bypassed.
              </p>

              <div className="space-y-2">
                {currentRoleWidgets.order.map((widgetId, index) => {
                  const isHidden = currentRoleWidgets.hidden.includes(widgetId);
                  const label = WIDGET_LABELS[widgetId] || widgetId;

                  return (
                    <div
                      key={widgetId}
                      className={`flex items-center justify-between p-3 rounded-[8px] border transition-colors ${
                        isHidden
                          ? 'border-[#E3E8EF] bg-[#F6F8FB] opacity-60'
                          : 'border-[#E3E8EF] bg-white shadow-xs'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <span className="text-xs font-mono text-[#6B7688] w-4">{index + 1}.</span>
                        <div>
                          <span className="text-xs font-medium text-[#0B1F3A] block">{label}</span>
                          <span className="text-[10px] font-mono text-[#6B7688]">{widgetId}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={index === 0}
                          onClick={() => handleMoveWidget(index, 'up')}
                          className="h-7 w-7 p-0"
                          title="Move Up"
                        >
                          <ArrowUp className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={index === currentRoleWidgets.order.length - 1}
                          onClick={() => handleMoveWidget(index, 'down')}
                          className="h-7 w-7 p-0"
                          title="Move Down"
                        >
                          <ArrowDown className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant={isHidden ? 'outline' : 'ghost'}
                          size="sm"
                          onClick={() => handleToggleWidget(widgetId)}
                          className="h-7 w-7 p-0 ml-1"
                          title={isHidden ? 'Show Widget' : 'Hide Widget'}
                        >
                          {isHidden ? (
                            <EyeOff className="h-3.5 w-3.5 text-[#6B7688]" />
                          ) : (
                            <Eye className="h-3.5 w-3.5 text-[#1F6FEB]" />
                          )}
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Persistence Status */}
          <div className="rounded-[8px] border border-[#B8D3FC] bg-[#E8F0FE] p-3 text-xs text-[#12315C] flex items-start gap-2">
            <Info className="h-4 w-4 shrink-0 text-[#1F6FEB] mt-0.5" />
            <div>
              <p className="font-semibold flex items-center gap-1.5">
                <Database className="h-3.5 w-3.5 text-[#1F6FEB]" />
                PostgreSQL Profile Persistence Scope
              </p>
              <p className="mt-0.5 text-[11px] text-[#12315C]/80">
                Preferences are scoped to your institutional identity (<span className="font-mono">{userId.slice(0, 8)}...</span>) and role (<span className="font-mono">{role}</span>). Settings are stored in <span className="font-mono">iam.user_profiles.preferences</span> and sync across reloads.
              </p>
            </div>
          </div>

          {feedback && (
            <div
              className={`rounded-[8px] border p-3 text-xs flex items-center gap-2 ${
                feedback.isSynced
                  ? 'border-[#B2E6CE] bg-[#EAF8F1] text-[#1E9E6A]'
                  : 'border-[#FCE2B3] bg-[#FEF7EA] text-[#E39B16]'
              }`}
            >
              <Check className="h-4 w-4" />
              <span>{feedback.message}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="mt-6 flex items-center justify-between border-t border-[#E3E8EF] pt-4">
          <Button variant="ghost" size="sm" onClick={handleReset} className="text-[#6B7688]">
            <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
            Reset Defaults
          </Button>

          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" onClick={handleSave} disabled={isSaving}>
              {isSaving ? 'Saving...' : 'Save Preferences'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
