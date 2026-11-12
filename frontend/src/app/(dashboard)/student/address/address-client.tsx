'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  Building2,
  Mail,
  Phone,
  CheckCircle2,
  AlertCircle,
  Loader2,
  ArrowLeft,
  Save,
  MapPin,
  Copy,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface AddressData {
  id?: string;
  line1: string;
  line2: string;
  landmark: string;
  city: string;
  district: string;
  stateCode: string;
  postalCode: string;
  countryCode: string;
}

const emptyAddress: AddressData = {
  line1: '',
  line2: '',
  landmark: '',
  city: '',
  district: '',
  stateCode: '',
  postalCode: '',
  countryCode: 'IND',
};

function mapAddress(addr: any): AddressData {
  if (!addr) return emptyAddress;
  return {
    id: addr.id,
    line1: addr.line1 || '',
    line2: addr.line2 || '',
    landmark: addr.landmark || '',
    city: addr.city || '',
    district: addr.district || '',
    stateCode: addr.stateCode || '',
    postalCode: addr.postalCode || '',
    countryCode: addr.countryCode || 'IND',
  };
}

export function AddressClient({ initialProfile }: { initialProfile?: any }) {
  const [loading, setLoading] = useState(!initialProfile);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [studentId, setStudentId] = useState<string | null>(initialProfile?.id || null);
  const [studentProfile, setStudentProfile] = useState<any>(initialProfile || null);

  const [activeTab, setActiveTab] = useState<'PERMANENT' | 'CURRENT'>('PERMANENT');

  const [permanentAddress, setPermanentAddress] = useState<AddressData>(() =>
    initialProfile?.permanentAddress ? mapAddress(initialProfile.permanentAddress) : emptyAddress
  );
  const [currentAddress, setCurrentAddress] = useState<AddressData>(() =>
    initialProfile?.currentAddress ? mapAddress(initialProfile.currentAddress) : emptyAddress
  );
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!initialProfile) {
      fetchProfile();
    }
  }, [initialProfile]);

  async function fetchProfile() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/student/me');
      if (!res.ok) {
        throw new Error('Failed to load student profile');
      }
      const data = await res.json();
      setStudentProfile(data);
      setStudentId(data.id);

      if (data.permanentAddress) {
        setPermanentAddress(mapAddress(data.permanentAddress));
      }

      if (data.currentAddress) {
        setCurrentAddress(mapAddress(data.currentAddress));
      }
    } catch (err: any) {
      setError(err.message || 'Error communicating with profile service');
    } finally {
      setLoading(false);
    }
  }

  const currentForm = activeTab === 'PERMANENT' ? permanentAddress : currentAddress;

  function updateField(field: keyof AddressData, value: string) {
    if (activeTab === 'PERMANENT') {
      setPermanentAddress((prev) => ({ ...prev, [field]: value }));
    } else {
      setCurrentAddress((prev) => ({ ...prev, [field]: value }));
    }
    if (validationErrors[field]) {
      setValidationErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  }

  function handleCopyPermanentToCurrent() {
    setCurrentAddress({
      ...permanentAddress,
      id: undefined,
    });
    setSuccessMsg('Permanent address copied to current address. Click "Save Address" to apply.');
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!currentForm.line1.trim()) {
      errors.line1 = 'Address line 1 is required';
    }
    if (!currentForm.city.trim()) {
      errors.city = 'City is required';
    }
    if (!currentForm.stateCode.trim()) {
      errors.stateCode = 'State is required';
    }
    if (!currentForm.postalCode.trim()) {
      errors.postalCode = 'Postal code is required';
    } else if (!/^\d{6}$/.test(currentForm.postalCode.trim())) {
      errors.postalCode = 'Postal code must be exactly 6 numeric digits';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    if (!studentId) {
      setError('Student ID missing. Cannot save address.');
      return;
    }

    setSaving(true);
    setError(null);
    setSuccessMsg(null);

    const payload = {
      addressType: activeTab,
      line1: currentForm.line1.trim(),
      line2: currentForm.line2?.trim() || null,
      landmark: currentForm.landmark?.trim() || null,
      city: currentForm.city.trim(),
      district: currentForm.district?.trim() || null,
      stateCode: currentForm.stateCode.trim(),
      postalCode: currentForm.postalCode.trim(),
      countryCode: currentForm.countryCode?.trim() || 'IND',
    };

    try {
      const res = await fetch(`/api/student/${studentId}/address`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || errorData.message || 'Failed to update address');
      }

      setSuccessMsg(`${activeTab === 'PERMANENT' ? 'Permanent' : 'Current'} address saved successfully.`);
      await fetchProfile();
    } catch (err: any) {
      setError(err.message || 'Failed to save address changes');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="flex items-center gap-3 text-sm text-[#6B7688]">
          <Loader2 className="h-5 w-5 animate-spin text-[#1F6FEB]" />
          Loading student address & contact details...
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
        <span className="font-semibold text-[#0B1F3A]">Contact & Address</span>
      </div>

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Contact & Address Management
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Maintain your official residential addresses on file with the registrar.
          </p>
        </div>
      </div>

      {/* Identity & Contact Details Card */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Building2 className="h-4 w-4 text-[#1F6FEB]" />
            Student Identity & Communication Channels
          </CardTitle>
          <CardDescription>
            Institutional credentials and emergency contact routing
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
            <div className="rounded-[8px] border border-[#E3E8EF] p-3 bg-[#F8FAFC]">
              <span className="text-[#6B7688] font-medium block">Enrollment Number</span>
              <span className="font-mono font-semibold text-[#0B1F3A] text-sm mt-0.5 block">
                {studentProfile?.enrollmentNo || 'Not provided'}
              </span>
            </div>

            <div className="rounded-[8px] border border-[#E3E8EF] p-3 bg-[#F8FAFC]">
              <span className="text-[#6B7688] font-medium flex items-center gap-1">
                <Mail className="h-3.5 w-3.5 text-[#1F6FEB]" />
                Primary Institutional Email
              </span>
              <span className="font-semibold text-[#0B1F3A] text-sm mt-0.5 block truncate">
                {studentProfile?.primaryEmail || 'Not provided'}
              </span>
              <span className="text-[10px] text-[#6B7688] mt-0.5 block">SSO Authenticated (Read-only)</span>
            </div>

            <div className="rounded-[8px] border border-[#E3E8EF] p-3 bg-[#F8FAFC]">
              <span className="text-[#6B7688] font-medium flex items-center gap-1">
                <Phone className="h-3.5 w-3.5 text-[#1F6FEB]" />
                Primary Contact Phone
              </span>
              <span className="font-semibold text-[#0B1F3A] text-sm mt-0.5 block">
                {studentProfile?.primaryPhone || 'Not provided'}
              </span>
              <span className="text-[10px] text-[#6B7688] mt-0.5 block">
                {studentProfile?.primaryPhone ? 'Registered on file' : 'Contact registrar for phone registration'}
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

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

      {/* Address Form */}
      <Card>
        <CardHeader>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-base flex items-center gap-2">
                <MapPin className="h-4 w-4 text-[#1F6FEB]" />
                Residential Addresses
              </CardTitle>
              <CardDescription>
                Update your permanent residence or current local correspondence address
              </CardDescription>
            </div>

            {/* Address Type Toggle */}
            <div className="inline-flex rounded-[8px] border border-[#E3E8EF] p-0.5 bg-[#F6F8FB]">
              <button
                type="button"
                onClick={() => {
                  setActiveTab('PERMANENT');
                  setValidationErrors({});
                  setError(null);
                  setSuccessMsg(null);
                }}
                className={`px-3 py-1.5 text-xs font-semibold rounded-[6px] transition-colors ${
                  activeTab === 'PERMANENT'
                    ? 'bg-white text-[#0B1F3A] shadow-sm'
                    : 'text-[#6B7688] hover:text-[#0B1F3A]'
                }`}
              >
                Permanent Address
              </button>
              <button
                type="button"
                onClick={() => {
                  setActiveTab('CURRENT');
                  setValidationErrors({});
                  setError(null);
                  setSuccessMsg(null);
                }}
                className={`px-3 py-1.5 text-xs font-semibold rounded-[6px] transition-colors ${
                  activeTab === 'CURRENT'
                    ? 'bg-white text-[#0B1F3A] shadow-sm'
                    : 'text-[#6B7688] hover:text-[#0B1F3A]'
                }`}
              >
                Current / Local Address
              </button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {activeTab === 'CURRENT' && (
            <div className="mb-4 flex items-center justify-between rounded-[8px] bg-[#F8FAFC] border border-[#E3E8EF] p-3 text-xs">
              <span className="text-[#6B7688]">Same as your permanent residence?</span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCopyPermanentToCurrent}
                className="h-8 gap-1.5 text-xs"
              >
                <Copy className="h-3.5 w-3.5" />
                Copy Permanent Address
              </Button>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="sm:col-span-2">
                <Input
                  label="Address Line 1 *"
                  placeholder="House / Flat No., Street, Building"
                  value={currentForm.line1}
                  onChange={(e) => updateField('line1', e.target.value)}
                  error={validationErrors.line1}
                />
              </div>

              <div className="sm:col-span-2">
                <Input
                  label="Address Line 2"
                  placeholder="Locality, Sector, Area (Optional)"
                  value={currentForm.line2}
                  onChange={(e) => updateField('line2', e.target.value)}
                />
              </div>

              <div>
                <Input
                  label="Landmark"
                  placeholder="Nearby landmark (Optional)"
                  value={currentForm.landmark}
                  onChange={(e) => updateField('landmark', e.target.value)}
                />
              </div>

              <div>
                <Input
                  label="City *"
                  placeholder="City / Town"
                  value={currentForm.city}
                  onChange={(e) => updateField('city', e.target.value)}
                  error={validationErrors.city}
                />
              </div>

              <div>
                <Input
                  label="District"
                  placeholder="District (Optional)"
                  value={currentForm.district}
                  onChange={(e) => updateField('district', e.target.value)}
                />
              </div>

              <div>
                <Input
                  label="State / Province *"
                  placeholder="State name or code"
                  value={currentForm.stateCode}
                  onChange={(e) => updateField('stateCode', e.target.value)}
                  error={validationErrors.stateCode}
                />
              </div>

              <div>
                <Input
                  label="Postal Code (PIN) *"
                  placeholder="6-digit PIN code"
                  maxLength={6}
                  value={currentForm.postalCode}
                  onChange={(e) => updateField('postalCode', e.target.value.replace(/\D/g, ''))}
                  error={validationErrors.postalCode}
                  hint="Must be exactly 6 numeric digits"
                />
              </div>

              <div>
                <Input
                  label="Country Code"
                  placeholder="IND"
                  maxLength={3}
                  value={currentForm.countryCode}
                  disabled
                  hint="Default country code is IND"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#E3E8EF]">
              <Link href="/student">
                <Button type="button" variant="outline" size="sm">
                  Cancel
                </Button>
              </Link>
              <Button type="submit" size="sm" disabled={saving} className="gap-1.5">
                {saving ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4" />
                    Save {activeTab === 'PERMANENT' ? 'Permanent' : 'Current'} Address
                  </>
                )}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
