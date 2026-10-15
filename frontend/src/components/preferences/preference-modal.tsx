'use client';

import React, { useState, useEffect } from 'react';
import { X, Check, RotateCcw, Monitor, Sun, Moon, Info } from 'lucide-react';
import {
  UserPreferences,
  PRODUCT_DEFAULTS,
  ClientScopedPreferenceAdapter,
} from '@/lib/preferences/preference-model';
import { Button } from '@/components/ui/button';

export interface PreferenceModalProps {
  isOpen: boolean;
  onClose: () => void;
  userId: string;
  role: string;
  tenantId: string | null;
}

export function PreferenceModal({
  isOpen,
  onClose,
  userId,
  role,
  tenantId,
}: PreferenceModalProps) {
  const adapter = React.useMemo(() => new ClientScopedPreferenceAdapter(), []);
  const [preferences, setPreferences] = useState<UserPreferences>(PRODUCT_DEFAULTS);
  const [feedback, setFeedback] = useState<{ message: string; isSynced: boolean } | null>(null);

  useEffect(() => {
    if (isOpen) {
      adapter.load(userId, role, tenantId).then((p) => {
        setPreferences(p);
      });
      setFeedback(null);
    }
  }, [isOpen, userId, role, tenantId, adapter]);

  if (!isOpen) return null;

  const handleSave = async () => {
    const res = await adapter.save(userId, role, tenantId, preferences);
    setFeedback({
      message: res.message || 'Preferences updated.',
      isSynced: res.isAccountSynced,
    });
    // Apply theme
    if (preferences.theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const handleReset = () => {
    setPreferences(PRODUCT_DEFAULTS);
    setFeedback({
      message: 'Restored to product defaults.',
      isSynced: false,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-lg rounded-[12px] bg-white p-6 shadow-xl text-[#1B2433]">
        <div className="flex items-center justify-between border-b border-[#E3E8EF] pb-4">
          <div>
            <h3 className="text-base font-bold text-[#0B1F3A]">Workspace Preferences</h3>
            <p className="text-xs text-[#6B7688]">
              Personalized display and operational defaults for {role.replace('_', ' ')}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-[6px] p-1 text-[#6B7688] hover:bg-[#F6F8FB] hover:text-[#1B2433]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-4 space-y-5 max-h-[60vh] overflow-y-auto px-1">
          {/* Theme */}
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
              Default Trend Window (Days)
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
              Accessibility
            </label>
            <div className="mt-2 space-y-2">
              <label className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] p-2.5 text-xs text-[#1B2433]">
                <span>High Contrast Mode (WCAG AAA)</span>
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

              <label className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] p-2.5 text-xs text-[#1B2433]">
                <span>Reduce Motion & Animations</span>
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

          {/* Persistence Disclosure Alert */}
          <div className="rounded-[8px] border border-[#B8D3FC] bg-[#E8F0FE] p-3 text-xs text-[#12315C] flex items-start gap-2">
            <Info className="h-4 w-4 shrink-0 text-[#1F6FEB] mt-0.5" />
            <div>
              <p className="font-semibold">Persistence Scope</p>
              <p className="mt-0.5 text-[11px] text-[#12315C]/80">
                Preferences are scoped to your authenticated identity (<span className="font-mono">{userId.slice(0, 8)}...</span>) and role (<span className="font-mono">{role}</span>).
              </p>
            </div>
          </div>

          {feedback && (
            <div className="rounded-[8px] border border-[#B2E6CE] bg-[#EAF8F1] p-3 text-xs text-[#1E9E6A] flex items-center gap-2">
              <Check className="h-4 w-4" />
              <span>{feedback.message}</span>
            </div>
          )}
        </div>

        <div className="mt-6 flex items-center justify-between border-t border-[#E3E8EF] pt-4">
          <Button variant="ghost" size="sm" onClick={handleReset} className="text-[#6B7688]">
            <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
            Reset Defaults
          </Button>

          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" onClick={handleSave}>
              Save Preferences
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
