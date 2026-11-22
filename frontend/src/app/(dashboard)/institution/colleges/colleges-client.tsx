'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { AlertCircle, GraduationCap, Loader2, Plus, Search } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

export interface CollegeRow {
  collegeId: string;
  code: string;
  name: string;
  status: string;
  provisioningStatus?: string;
  handoverStatus?: string;
  createdAt?: string;
}

type StatusFilter = 'ALL' | 'ONBOARDING' | 'PENDING_HANDOVER' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export function CollegesClient() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [colleges, setColleges] = useState<CollegeRow[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');

  const load = useCallback(async (status?: string) => {
    setLoading(true);
    setError(null);
    try {
      const qs = status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : '';
      const res = await fetch(`/api/institution/colleges${qs}`, { method: 'GET' });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(typeof body.detail === 'string' ? body.detail : 'unavailable');
        setColleges([]);
        return;
      }
      const rows = Array.isArray(body.colleges) ? body.colleges : [];
      setColleges(
        rows.map((c: Record<string, unknown>) => ({
          collegeId: String(c.collegeId || c.id || ''),
          code: String(c.code || ''),
          name: String(c.name || ''),
          status: String(c.status || ''),
          provisioningStatus: c.provisioningStatus ? String(c.provisioningStatus) : undefined,
          handoverStatus: c.handoverStatus ? String(c.handoverStatus) : undefined,
          createdAt: c.createdAt ? String(c.createdAt) : undefined,
        }))
      );
    } catch {
      setError('unavailable');
      setColleges([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(statusFilter === 'ALL' ? undefined : statusFilter);
  }, [load, statusFilter]);

  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return colleges;
    return colleges.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.code.toLowerCase().includes(q) ||
        c.collegeId.toLowerCase().includes(q)
    );
  }, [colleges, searchQuery]);

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6" data-testid="iadm01-colleges-desk">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Colleges</h1>
          <p className="mt-1 text-xs text-[#6B7688]">
            Register and manage colleges in your institute; hand over to College Administration when ready.
          </p>
        </div>
        <Link href="/institution/colleges/new">
          <Button variant="primary" size="sm" data-testid="iadm01-colleges-register">
            <Plus className="mr-1.5 h-4 w-4" />
            Register college
          </Button>
        </Link>
      </div>

      <Card>
        <CardContent className="space-y-4 pt-5 pb-4">
          <div className="flex flex-wrap gap-1.5 rounded-[8px] border border-[#E3E8EF] bg-[#F6F8FB] p-1">
            {(
              ['ALL', 'ONBOARDING', 'PENDING_HANDOVER', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'] as const
            ).map((tab) => (
              <button
                key={tab}
                type="button"
                onClick={() => setStatusFilter(tab)}
                className={`rounded-[6px] px-3 py-1.5 text-xs font-semibold transition-all ${
                  statusFilter === tab
                    ? 'border border-[#E3E8EF] bg-white text-[#0B1F3A] shadow-xs'
                    : 'text-[#6B7688] hover:text-[#0B1F3A]'
                }`}
                data-testid={`iadm01-colleges-filter-${tab.toLowerCase()}`}
              >
                {tab.replace(/_/g, ' ').toLowerCase()}
                {tab === 'ALL' ? ` (${colleges.length})` : ''}
              </button>
            ))}
          </div>
          <div className="relative w-full sm:max-w-sm">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-[#6B7688]" />
            <Input
              className="pl-9"
              placeholder="Search name or code"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              data-testid="iadm01-colleges-search"
            />
          </div>
        </CardContent>
      </Card>

      {loading && (
        <p className="flex items-center gap-2 text-sm text-slate-600" data-testid="iadm01-colleges-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading colleges…
        </p>
      )}

      {error && (
        <div
          className="flex gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
          data-testid="iadm01-colleges-error"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {!loading && !error && filtered.length === 0 && (
        <Card data-testid="iadm01-colleges-empty">
          <CardContent className="space-y-3 py-8 text-center text-sm text-[#6B7688]">
            <GraduationCap className="mx-auto h-8 w-8 text-[#6B7688]" />
            <p>Register your first college to continue onboarding.</p>
            <Link href="/institution/colleges/new">
              <Button variant="primary" size="sm" data-testid="iadm01-colleges-empty-cta">
                Register your first college
              </Button>
            </Link>
          </CardContent>
        </Card>
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="space-y-2" data-testid="iadm01-colleges-list">
          {filtered.map((c) => (
            <Link
              key={c.collegeId}
              href={`/institution/colleges/${c.collegeId}`}
              className="block rounded-[8px] border border-[#E3E8EF] bg-white px-4 py-3 transition hover:border-[#0B1F3A]/30"
              data-testid={`iadm01-colleges-row-${c.code || c.collegeId}`}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-medium text-[#0B1F3A]">{c.name}</p>
                  <p className="text-xs font-mono text-[#6B7688]">{c.code}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="outline">{c.status || '—'}</Badge>
                  {c.provisioningStatus && (
                    <span className="text-xs text-[#6B7688]">Admin: {c.provisioningStatus}</span>
                  )}
                  {c.handoverStatus && (
                    <span className="text-xs text-[#6B7688]">Handover: {c.handoverStatus}</span>
                  )}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}