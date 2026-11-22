'use client';

import React, { useState, useMemo } from 'react';
import Link from 'next/link';
import { Building2, Plus, Search, Calendar, ShieldCheck, Users, Filter, CheckCircle2, AlertCircle, ExternalLink } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export interface InstituteRecord {
  id: string;
  name: string;
  code: string;
  status: string;
  created_at: string;
  updated_at: string;
  college_count: number;
  user_count: number;
}

interface InstitutesRegistryClientProps {
  initialInstitutes: InstituteRecord[];
}

export function InstitutesRegistryClient({ initialInstitutes }: InstitutesRegistryClientProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'ONBOARDING' | 'SUSPENDED' | 'OFFBOARDED'>('ALL');

  // Normalize status mapping per Phase 4 requirements
  const normalizeStatus = (status: string): 'ACTIVE' | 'ONBOARDING' | 'SUSPENDED' | 'OFFBOARDED' => {
    const s = (status || '').toUpperCase();
    if (s === 'ACTIVE') return 'ACTIVE';
    if (s === 'SUSPENDED') return 'SUSPENDED';
    if (s === 'INACTIVE' || s === 'ONBOARDING' || s === 'REQUESTED' || s === 'PROVISIONING') return 'ONBOARDING';
    return 'OFFBOARDED';
  };

  const statusCounts = useMemo(() => {
    const counts = { ALL: initialInstitutes.length, ACTIVE: 0, ONBOARDING: 0, SUSPENDED: 0, OFFBOARDED: 0 };
    initialInstitutes.forEach((inst) => {
      const norm = normalizeStatus(inst.status);
      counts[norm]++;
    });
    return counts;
  }, [initialInstitutes]);

  const filteredInstitutes = useMemo(() => {
    return initialInstitutes.filter((inst) => {
      const norm = normalizeStatus(inst.status);
      if (statusFilter !== 'ALL' && norm !== statusFilter) {
        return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const nameMatch = (inst.name || '').toLowerCase().includes(q);
        const codeMatch = (inst.code || '').toLowerCase().includes(q);
        const idMatch = inst.id.toLowerCase().includes(q);
        return nameMatch || codeMatch || idMatch;
      }
      return true;
    });
  }, [initialInstitutes, statusFilter, searchQuery]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Institutes & Tenancies Registry
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Authoritative registry of all provisioned institutions, university campuses, and multi-tenant nodes.
          </p>
        </div>

        <Link href="/platform/institutes/new">
          <Button variant="primary" size="sm">
            <Plus className="mr-1.5 h-4 w-4" />
            Onboard New Institute
          </Button>
        </Link>
      </div>

      {/* Filter Tabs & Search Bar */}
      <Card>
        <CardContent className="pt-5 pb-4 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            {/* Status Filter Buttons */}
            <div className="flex flex-wrap items-center gap-1.5 p-1 bg-[#F6F8FB] rounded-[8px] border border-[#E3E8EF]">
              {(['ALL', 'ACTIVE', 'ONBOARDING', 'SUSPENDED', 'OFFBOARDED'] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setStatusFilter(tab)}
                  className={`px-3 py-1.5 text-xs font-semibold rounded-[6px] transition-all capitalize ${
                    statusFilter === tab
                      ? 'bg-white text-[#0B1F3A] shadow-xs border border-[#E3E8EF]'
                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                  }`}
                >
                  {tab.toLowerCase()} ({statusCounts[tab]})
                </button>
              ))}
            </div>

            {/* Search Input */}
            <div className="relative w-full sm:w-72">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-[#6B7688]" />
              <Input
                type="text"
                placeholder="Search by name, code, or UUID..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 text-xs"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Registry Table */}
      <Card>
        <CardHeader className="py-4 border-b border-[#E3E8EF]">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                Registered Institutional Tenants ({filteredInstitutes.length})
              </CardTitle>
              <CardDescription className="text-xs">
                Authoritative records queried from core.tenants in staging PostgreSQL
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          {filteredInstitutes.length === 0 ? (
            <div className="text-center py-10">
              <Building2 className="mx-auto h-10 w-10 text-[#6B7688]/50" />
              <p className="mt-2 text-sm font-semibold text-[#0B1F3A]">No Institutes Matching Criteria</p>
              <p className="text-xs text-[#6B7688] mt-1">
                {searchQuery ? 'Try adjusting your search query or status filter.' : 'Begin by onboarding your first institutional tenant.'}
              </p>
              {searchQuery ? (
                <Button variant="outline" size="sm" onClick={() => { setSearchQuery(''); setStatusFilter('ALL'); }} className="mt-4">
                  Clear Filters
                </Button>
              ) : (
                <Link href="/platform/institutes/new" className="inline-block mt-4">
                  <Button variant="primary" size="sm">
                    Launch Onboarding Wizard
                  </Button>
                </Link>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Institute Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant Code</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Status Badge</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-right">Colleges</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-right">Aggregated Users</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Created Date</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant UUID</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {filteredInstitutes.map((inst) => {
                    const norm = normalizeStatus(inst.status);
                    return (
                      <tr key={inst.id} className="hover:bg-[#F6F8FB]">
                        <td className="py-3.5 font-medium text-[#0B1F3A]">
                          <div className="flex items-center gap-2.5">
                            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[6px] bg-[#E8F0FE] text-[#1F6FEB]">
                              <Building2 className="h-4 w-4" />
                            </div>
                            <div>
                              <span className="font-semibold block text-sm">{inst.name || 'Unnamed Tenant'}</span>
                              <span className="text-[11px] text-[#6B7688] font-mono">Domain: {inst.code.toLowerCase()}.campxsync.edu</span>
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 font-mono font-semibold text-[#1B2433]">{inst.code}</td>
                        <td className="py-3.5">
                          <Badge status={norm} />
                        </td>
                        <td className="py-3.5 text-right font-medium text-[#1B2433]">
                          {inst.college_count || 1}
                        </td>
                        <td className="py-3.5 text-right">
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-mono bg-[#E8F0FE] text-[#1F6FEB] font-medium">
                            {inst.user_count} users
                          </span>
                        </td>
                        <td className="py-3.5 text-[#6B7688] font-mono">{formatDate(inst.created_at)}</td>
                        <td className="py-3.5 font-mono text-[#6B7688] text-[11px]">{inst.id}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
