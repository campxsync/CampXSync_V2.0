'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Users,
  Plus,
  Mail,
  Building2,
  CheckCircle2,
  AlertCircle,
  Briefcase,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

interface Department {
  id: string;
  name: string;
  code: string;
}

interface FacultyMember {
  id: string;
  employee_no: string;
  employment_type: string;
  status: string;
  department_id: string;
  department_name?: string;
  display_name: string;
  primary_email: string;
  created_at: string;
}

interface Props {
  initialFaculty: FacultyMember[];
  departments: Department[];
}

export function FacultyRoster({ initialFaculty, departments }: Props) {
  const router = useRouter();
  const [showAddModal, setShowAddModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterDept, setFilterDept] = useState('');

  // Form states
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [employeeNo, setEmployeeNo] = useState('');
  const [departmentId, setDepartmentId] = useState(departments[0]?.id || '');
  const [employmentType, setEmploymentType] = useState<'FULL_TIME' | 'PART_TIME' | 'ADJUNCT' | 'CONTRACT' | 'VISITING'>('FULL_TIME');

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const handleCreateFaculty = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/faculty', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName,
          lastName,
          email,
          employeeNo,
          departmentId,
          employmentType,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to enroll faculty.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setFirstName('');
        setLastName('');
        setEmail('');
        setEmployeeNo('');
        setShowAddModal(false);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const filtered = initialFaculty.filter((f) => {
    const matchesSearch =
      f.display_name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      f.employee_no.toLowerCase().includes(searchTerm.toLowerCase()) ||
      f.primary_email.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesDept = !filterDept || f.department_id === filterDept;
    return matchesSearch && matchesDept;
  });

  return (
    <div className="space-y-6">
      {/* Header Toolbar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex flex-1 items-center gap-3">
          <Input
            placeholder="Search faculty by name, employee ID, or email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="max-w-xs"
          />

          <select
            className="h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
            value={filterDept}
            onChange={(e) => setFilterDept(e.target.value)}
          >
            <option value="">All Departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} ({d.code})
              </option>
            ))}
          </select>
        </div>

        <Button
          variant="primary"
          size="sm"
          disabled={departments.length === 0}
          onClick={() => setShowAddModal(!showAddModal)}
        >
          <Plus className="mr-1.5 h-4 w-4" />
          {showAddModal ? 'Close Form' : 'Enroll New Faculty'}
        </Button>
      </div>

      {/* Status Message */}
      {statusMessage && (
        <div
          className={`p-3 rounded-[8px] border text-xs flex items-center gap-2 ${
            statusMessage.type === 'error'
              ? 'border-[#D64545] bg-[#FDF3F3] text-[#D64545]'
              : 'border-[#107C41] bg-[#DFF6DD] text-[#107C41]'
          }`}
        >
          {statusMessage.type === 'error' ? (
            <AlertCircle className="h-4 w-4 shrink-0" />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          )}
          <span>{statusMessage.text}</span>
        </div>
      )}

      {/* Add Faculty Form Drawer */}
      {showAddModal && (
        <Card className="border-[#1F6FEB]/40 bg-[#F6F8FB]/50">
          <CardHeader>
            <CardTitle>Enroll New Faculty Member</CardTitle>
            <CardDescription>
              Registers human identity in core.persons and faculty appointment in hrm.employees.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateFaculty} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    First Name <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. John"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Last Name <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. Doe"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Official Email <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    type="email"
                    placeholder="e.g. j.doe@institution.edu"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Employee ID <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. FAC-2026-001"
                    value={employeeNo}
                    onChange={(e) => setEmployeeNo(e.target.value.toUpperCase())}
                    className="font-mono uppercase"
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Department <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={departmentId}
                    onChange={(e) => setDepartmentId(e.target.value)}
                    required
                  >
                    {departments.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} ({d.code})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                  Employment Type
                </label>
                <select
                  className="w-full sm:w-1/3 h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                  value={employmentType}
                  onChange={(e: any) => setEmploymentType(e.target.value)}
                >
                  <option value="FULL_TIME">Full Time</option>
                  <option value="PART_TIME">Part Time</option>
                  <option value="ADJUNCT">Adjunct</option>
                  <option value="CONTRACT">Contract</option>
                  <option value="VISITING">Visiting Faculty</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="ghost" onClick={() => setShowAddModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" isLoading={isLoading}>
                  Confirm Appointment
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Roster Table */}
      <Card>
        <CardHeader>
          <CardTitle>Teaching Faculty Roster ({filtered.length})</CardTitle>
          <CardDescription>
            Active instructional staff mapped to departments in hrm.employees and core.persons
          </CardDescription>
        </CardHeader>
        <CardContent>
          {filtered.length === 0 ? (
            <EmptyState
              icon={Users}
              title="No Faculty Members Enrolled"
              description="Enroll professors, lecturers, and teaching staff to assign them to timetable sessions and attendance workflows."
              action={
                departments.length > 0
                  ? {
                      label: 'Enroll First Faculty Member',
                      onClick: () => setShowAddModal(true),
                    }
                  : undefined
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Faculty Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Employee ID</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Department</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Email</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Type</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {filtered.map((fac) => (
                    <tr key={fac.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3.5 font-medium text-[#0B1F3A]">
                        <div className="flex items-center gap-2">
                          <div className="h-7 w-7 rounded-full bg-[#E8F0FE] text-[#1F6FEB] flex items-center justify-center font-bold text-[10px]">
                            {fac.display_name.charAt(0)}
                          </div>
                          <span>{fac.display_name}</span>
                        </div>
                      </td>
                      <td className="py-3.5 font-mono text-[#1B2433]">{fac.employee_no}</td>
                      <td className="py-3.5 text-[#1B2433]">
                        {fac.department_name || 'Department'}
                      </td>
                      <td className="py-3.5 text-[#6B7688]">
                        <div className="flex items-center gap-1.5">
                          <Mail className="h-3.5 w-3.5 text-[#6B7688]" />
                          <span>{fac.primary_email}</span>
                        </div>
                      </td>
                      <td className="py-3.5">
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-[#F6F8FB] text-[#6B7688]">
                          {fac.employment_type.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="py-3.5">
                        <Badge status={fac.status || 'ACTIVE'} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
