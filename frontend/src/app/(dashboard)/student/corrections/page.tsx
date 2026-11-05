'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  History,
  FileEdit,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Loader2,
  ArrowLeft,
  Send,
  Filter,
  ShieldAlert,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

const SUPPORTED_FIELDS = [
  { key: 'legalName', label: 'Legal Name (Display Name)' },
  { key: 'firstName', label: 'First Name' },
  { key: 'lastName', label: 'Last Name' },
  { key: 'dateOfBirth', label: 'Date of Birth (YYYY-MM-DD)' },
  { key: 'gender', label: 'Gender (MALE / FEMALE / OTHER)' },
  { key: 'primaryEmail', label: 'Primary Email Address' },
];

export default function StudentCorrectionsPage() {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [studentId, setStudentId] = useState<string | null>(null);
  const [studentProfile, setStudentProfile] = useState<any>(null);

  const [corrections, setCorrections] = useState<any[]>([]);
  const [historyLogs, setHistoryLogs] = useState<any[]>([]);

  // Form State
  const [targetField, setTargetField] = useState('legalName');
  const [proposedValue, setProposedValue] = useState('');
  const [reason, setReason] = useState('');
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  // Filter State for Corrections Table
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');

  useEffect(() => {
    init();
  }, []);

  async function init() {
    setLoading(true);
    setError(null);
    try {
      const profileRes = await fetch('/api/student/me');
      if (!profileRes.ok) {
        throw new Error('Failed to load student profile');
      }
      const profileData = await profileRes.json();
      setStudentProfile(profileData);
      setStudentId(profileData.id);

      await Promise.all([
        fetchCorrections(profileData.id),
        fetchHistory(profileData.id),
      ]);
    } catch (err: any) {
      setError(err.message || 'Error communicating with server');
    } finally {
      setLoading(false);
    }
  }

  async function fetchCorrections(sId: string) {
    try {
      const res = await fetch(`/api/student/${sId}/corrections`);
      if (res.ok) {
        const data = await res.json();
        setCorrections(Array.isArray(data) ? data : []);
      }
    } catch (err) {
      console.error('Failed to load corrections:', err);
    }
  }

  async function fetchHistory(sId: string) {
    try {
      const res = await fetch(`/api/student/${sId}/history`);
      if (res.ok) {
        const data = await res.json();
        setHistoryLogs(Array.isArray(data) ? data : []);
      }
    } catch (err) {
      console.error('Failed to load change history:', err);
    }
  }

  function resolveCurrentValue(fieldKey: string): string {
    if (!studentProfile) return 'Not provided';
    switch (fieldKey) {
      case 'legalName':
        return studentProfile.displayName || `${studentProfile.firstName || ''} ${studentProfile.lastName || ''}`.trim() || 'Not provided';
      case 'firstName':
        return studentProfile.firstName || 'Not provided';
      case 'lastName':
        return studentProfile.lastName || 'Not provided';
      case 'dateOfBirth':
        return studentProfile.dateOfBirth || 'Not provided';
      case 'gender':
        return studentProfile.gender || 'Not provided';
      case 'primaryEmail':
        return studentProfile.primaryEmail || 'Not provided';
      default:
        return 'Not provided';
    }
  }

  function validateForm(): boolean {
    const errors: Record<string, string> = {};
    if (!proposedValue.trim()) {
      errors.proposedValue = 'Proposed value is required';
    }
    if (!reason.trim()) {
      errors.reason = 'Reason for profile correction is required';
    } else if (reason.trim().length > 500) {
      errors.reason = 'Reason cannot exceed 500 characters';
    }
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validateForm()) return;
    if (!studentId) {
      setError('Student ID missing. Cannot submit request.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccessMsg(null);

    const payload = {
      targetField,
      proposedValue: proposedValue.trim(),
      reason: reason.trim(),
    };

    try {
      const res = await fetch(`/api/student/${studentId}/corrections`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || errorData.message || 'Failed to submit correction request');
      }

      setSuccessMsg('Correction request submitted to the Office of the Registrar.');
      setProposedValue('');
      setReason('');
      await fetchCorrections(studentId);
    } catch (err: any) {
      setError(err.message || 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  }

  const filteredCorrections = corrections.filter((c) => {
    if (statusFilter === 'ALL') return true;
    return c.status?.toUpperCase() === statusFilter;
  });

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="flex items-center gap-3 text-sm text-[#6B7688]">
          <Loader2 className="h-5 w-5 animate-spin text-[#1F6FEB]" />
          Loading profile corrections & change history...
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Breadcrumb & Navigation */}
      <div className="flex items-center gap-2 text-xs text-[#6B7688]">
        <Link href="/student" className="flex items-center gap-1 hover:text-[#1F6FEB] transition-colors">
          <ArrowLeft className="h-3.5 w-3.5" />
          Dashboard
        </Link>
        <span>/</span>
        <span className="font-semibold text-[#0B1F3A]">Profile Corrections</span>
      </div>

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Profile Correction Requests
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Request formal changes to protected biographical fields. All changes undergo institutional verification.
          </p>
        </div>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="rounded-[8px] border border-[#1E9E6A]/20 bg-[#1E9E6A]/10 p-3 text-xs text-[#1E9E6A] flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="rounded-[8px] border border-[#D9383A]/20 bg-[#D9383A]/10 p-3 text-xs text-[#D9383A] flex items-center gap-2">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Request Submission Form */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <FileEdit className="h-4 w-4 text-[#1F6FEB]" />
            Submit New Correction Request
          </CardTitle>
          <CardDescription>
            Select the protected field to update and provide the official justification
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Target Field Select */}
              <div className="flex flex-col gap-1.5 text-left">
                <label htmlFor="targetField" className="text-xs font-semibold text-[#1B2433] tracking-wide">
                  Target Protected Field *
                </label>
                <select
                  id="targetField"
                  value={targetField}
                  onChange={(e) => {
                    setTargetField(e.target.value);
                    setFormErrors({});
                  }}
                  className="flex h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 py-2 text-sm text-[#1B2433] focus-visible:outline-none focus-visible:border-[#1F6FEB] focus-visible:ring-2 focus-visible:ring-[#1F6FEB]"
                >
                  {SUPPORTED_FIELDS.map((f) => (
                    <option key={f.key} value={f.key}>
                      {f.label}
                    </option>
                  ))}
                </select>
                <span className="text-[11px] text-[#6B7688]">Protected biographical fields require registrar approval</span>
              </div>

              {/* Locked Current Value */}
              <div>
                <Input
                  label="Current Value on Record"
                  value={resolveCurrentValue(targetField)}
                  disabled
                  hint="Active value currently verified in institutional database"
                />
              </div>

              {/* Proposed Value Input */}
              <div className="sm:col-span-2">
                <Input
                  label="Proposed New Value *"
                  placeholder="Enter corrected value exactly as it appears on official documents"
                  value={proposedValue}
                  onChange={(e) => {
                    setProposedValue(e.target.value);
                    if (formErrors.proposedValue) {
                      setFormErrors((prev) => ({ ...prev, proposedValue: '' }));
                    }
                  }}
                  error={formErrors.proposedValue}
                />
              </div>

              {/* Reason / Justification Textarea */}
              <div className="sm:col-span-2 flex flex-col gap-1.5 text-left">
                <label htmlFor="reason" className="text-xs font-semibold text-[#1B2433] tracking-wide">
                  Reason for Correction *
                </label>
                <textarea
                  id="reason"
                  rows={3}
                  maxLength={500}
                  placeholder="Explain why this correction is required (e.g. typographical error in initial enrollment)..."
                  value={reason}
                  onChange={(e) => {
                    setReason(e.target.value);
                    if (formErrors.reason) {
                      setFormErrors((prev) => ({ ...prev, reason: '' }));
                    }
                  }}
                  className="w-full rounded-[8px] border border-[#E3E8EF] bg-white p-3 text-sm text-[#1B2433] placeholder:text-[#6B7688] focus-visible:outline-none focus-visible:border-[#1F6FEB] focus-visible:ring-2 focus-visible:ring-[#1F6FEB]"
                />
                <div className="flex items-center justify-between text-[11px] text-[#6B7688]">
                  <span>{formErrors.reason ? <span className="text-[#D9383A]">{formErrors.reason}</span> : 'Provide authentic context for verification'}</span>
                  <span>{reason.length} / 500 characters</span>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#E3E8EF]">
              <Button type="submit" size="sm" disabled={submitting} className="gap-1.5">
                {submitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Submitting Request...
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4" />
                    Submit Request
                  </>
                )}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Submitted Requests List with Status Filter Tabs */}
      <Card>
        <CardHeader>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-base flex items-center gap-2">
                <History className="h-4 w-4 text-[#1F6FEB]" />
                My Correction Requests
              </CardTitle>
              <CardDescription>
                Track the status and registrar decisions for all submitted requests
              </CardDescription>
            </div>

            {/* Status Filter Tabs */}
            <div className="inline-flex rounded-[8px] border border-[#E3E8EF] p-0.5 bg-[#F6F8FB]">
              {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setStatusFilter(tab)}
                  className={`px-3 py-1 text-xs font-semibold rounded-[6px] transition-colors capitalize ${
                    statusFilter === tab
                      ? 'bg-white text-[#0B1F3A] shadow-sm'
                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                  }`}
                >
                  {tab.toLowerCase()}
                </button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {filteredCorrections.length === 0 ? (
            <div className="text-center py-8">
              <History className="mx-auto h-8 w-8 text-[#6B7688]/40" />
              <p className="text-xs text-[#6B7688] mt-2">
                {statusFilter === 'ALL'
                  ? 'No correction requests submitted yet.'
                  : `No requests with status ${statusFilter}.`}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Date Submitted</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Target Field</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Old Value</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Proposed Value</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Registrar Remarks</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {filteredCorrections.map((req) => (
                    <tr key={req.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3 font-mono text-[#6B7688]">
                        {req.createdAt ? formatDate(req.createdAt) : '—'}
                      </td>
                      <td className="py-3 font-semibold text-[#0B1F3A]">
                        {SUPPORTED_FIELDS.find((f) => f.key === req.targetField)?.label || req.targetField}
                      </td>
                      <td className="py-3 text-[#6B7688]">
                        {req.oldValue || 'Not provided'}
                      </td>
                      <td className="py-3 font-medium text-[#0B1F3A]">
                        {req.newValue || 'Not provided'}
                      </td>
                      <td className="py-3 text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${
                            req.status === 'APPROVED'
                              ? 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
                              : req.status === 'REJECTED'
                              ? 'bg-[#D9383A]/10 text-[#D9383A]'
                              : 'bg-[#F2994A]/10 text-[#F2994A]'
                          }`}
                        >
                          {req.status || 'PENDING'}
                        </span>
                      </td>
                      <td className="py-3 text-[#6B7688]">
                        <div>{req.decisionReason || 'Under institutional review'}</div>
                        {req.decidedAt && (
                          <div className="text-[10px] text-[#6B7688] mt-0.5 font-mono">
                            Decided: {formatDate(req.decidedAt)}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Profile Change History Audit Trail */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Clock className="h-4 w-4 text-[#1F6FEB]" />
            Profile Mutation Audit Trail
          </CardTitle>
          <CardDescription>
            Official immutable timeline of profile changes recorded by the registrar
          </CardDescription>
        </CardHeader>
        <CardContent>
          {historyLogs.length === 0 ? (
            <div className="text-center py-6 text-xs text-[#6B7688]">
              No historical mutations logged for this student record.
            </div>
          ) : (
            <div className="relative pl-6 space-y-4 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-[#E3E8EF]">
              {historyLogs.map((log, index) => (
                <div key={log.id || index} className="relative">
                  <div className="absolute -left-6 top-1 h-2.5 w-2.5 rounded-full bg-[#1F6FEB] ring-4 ring-white" />
                  <div className="text-xs">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-[#0B1F3A]">
                        {log.action?.replace(/_/g, ' ') || 'ACTION'}
                      </span>
                      <span className="font-mono text-[11px] text-[#6B7688]">
                        {log.timestamp ? formatDate(log.timestamp) : '—'}
                      </span>
                    </div>
                    <p className="text-[#475569] mt-0.5">{log.summary || 'Profile record updated'}</p>
                    {log.changedFields && log.changedFields.length > 0 && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {log.changedFields.map((f: string) => (
                          <span
                            key={f}
                            className="inline-block rounded bg-[#F1F5F9] px-1.5 py-0.5 text-[10px] font-mono text-[#475569]"
                          >
                            {f}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

