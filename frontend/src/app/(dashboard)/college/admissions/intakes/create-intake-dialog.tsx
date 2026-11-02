'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Plus, X, Layers, Calendar, DollarSign, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface CreateIntakeDialogProps {
  programs: Array<{ id: string; name: string; code: string }>;
}

export function CreateIntakeDialog({ programs }: CreateIntakeDialogProps) {
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const [formData, setFormData] = useState({
    intakeName: 'Fall 2026 Undergraduate Admissions',
    intakeCode: `INTAKE-${new Date().getFullYear()}-${Math.floor(100 + Math.random() * 900)}`,
    programId: programs[0]?.id || '11111111-2222-3333-4444-555555555551',
    applicationWindowStart: new Date().toISOString().split('T')[0],
    applicationWindowEnd: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
    capacity: 120,
    feeRequired: false,
    feeAmount: 500,
    currency: 'INR',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const res = await fetch('/api/college/admissions/intakes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...formData,
          capacity: Number(formData.capacity),
          feeAmount: formData.feeRequired ? Number(formData.feeAmount) : null,
          applicationWindowStart: new Date(formData.applicationWindowStart).toISOString(),
          applicationWindowEnd: new Date(formData.applicationWindowEnd + 'T23:59:59.999Z').toISOString(),
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to create intake cycle');
      }

      setSuccess(true);
      setTimeout(() => {
        setIsOpen(false);
        setSuccess(false);
        router.refresh();
      }, 1000);
    } catch (err: any) {
      setError(err.message || 'Error creating intake');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button variant="primary" size="sm" onClick={() => setIsOpen(true)}>
        <Plus className="mr-2 h-4 w-4" /> Create Intake Cycle
      </Button>

      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl animate-in fade-in zoom-in duration-200">
            <div className="flex items-center justify-between border-b border-[#E3E8EF] pb-3">
              <div className="flex items-center gap-2">
                <Layers className="h-5 w-5 text-[#1F6FEB]" />
                <h2 className="text-lg font-bold text-[#0B1F3A]">Create Admission Intake</h2>
              </div>
              <button
                onClick={() => setIsOpen(false)}
                className="text-[#64748B] hover:text-[#0B1F3A]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {error && (
              <div className="mt-4 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 border border-red-200">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {success && (
              <div className="mt-4 flex items-center gap-2 rounded-lg bg-green-50 p-3 text-sm text-green-700 border border-green-200">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>Intake cycle created successfully! Refreshing...</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#334155]">Intake Name</label>
                <input
                  type="text"
                  required
                  value={formData.intakeName}
                  onChange={(e) => setFormData({ ...formData, intakeName: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Intake Code</label>
                  <input
                    type="text"
                    required
                    value={formData.intakeCode}
                    onChange={(e) => setFormData({ ...formData, intakeCode: e.target.value })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm font-mono focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Seat Capacity</label>
                  <input
                    type="number"
                    min={1}
                    required
                    value={formData.capacity}
                    onChange={(e) => setFormData({ ...formData, capacity: Number(e.target.value) })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#334155]">Academic Program</label>
                <select
                  value={formData.programId}
                  onChange={(e) => setFormData({ ...formData, programId: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                >
                  {programs.length > 0 ? (
                    programs.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({p.code})
                      </option>
                    ))
                  ) : (
                    <option value="11111111-2222-3333-4444-555555555551">
                      B.Tech Computer Science and Engineering (UG)
                    </option>
                  )}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Window Start Date</label>
                  <input
                    type="date"
                    required
                    value={formData.applicationWindowStart}
                    onChange={(e) => setFormData({ ...formData, applicationWindowStart: e.target.value })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#334155]">Window End Date</label>
                  <input
                    type="date"
                    required
                    value={formData.applicationWindowEnd}
                    onChange={(e) => setFormData({ ...formData, applicationWindowEnd: e.target.value })}
                    className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  type="checkbox"
                  id="feeRequired"
                  checked={formData.feeRequired}
                  onChange={(e) => setFormData({ ...formData, feeRequired: e.target.checked })}
                  className="h-4 w-4 rounded border-[#CBD5E1] text-[#1F6FEB]"
                />
                <label htmlFor="feeRequired" className="text-xs text-[#334155]">
                  Require Application Fee
                </label>
              </div>

              {formData.feeRequired && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-[#334155]">Fee Amount</label>
                    <input
                      type="number"
                      value={formData.feeAmount}
                      onChange={(e) => setFormData({ ...formData, feeAmount: Number(e.target.value) })}
                      className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm focus:border-[#1F6FEB] focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#334155]">Currency</label>
                    <input
                      type="text"
                      value={formData.currency}
                      onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
                      className="mt-1 w-full rounded-lg border border-[#CBD5E1] p-2 text-sm uppercase focus:border-[#1F6FEB] focus:outline-none"
                    />
                  </div>
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3 pt-3 border-t border-[#E3E8EF]">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsOpen(false)}
                  disabled={loading}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" disabled={loading}>
                  {loading ? 'Creating...' : 'Save & Publish Intake'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
