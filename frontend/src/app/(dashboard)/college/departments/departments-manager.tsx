'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Building2,
  BookOpen,
  Plus,
  Layers,
  CheckCircle2,
  AlertCircle,
  GraduationCap,
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
  status: string;
  created_at: string;
}

interface Program {
  id: string;
  department_id: string;
  name: string;
  code: string;
  level: string;
  duration_years: number;
  total_terms: number;
  status: string;
}

interface Props {
  initialDepartments: Department[];
  initialPrograms: Program[];
}

export function DepartmentsManager({ initialDepartments, initialPrograms }: Props) {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<'departments' | 'programs'>('departments');
  const [showAddDept, setShowAddDept] = useState(false);
  const [showAddProg, setShowAddProg] = useState(false);

  // Form states
  const [deptName, setDeptName] = useState('');
  const [deptCode, setDeptCode] = useState('');
  const [progName, setProgName] = useState('');
  const [progCode, setProgCode] = useState('');
  const [progDeptId, setProgDeptId] = useState(initialDepartments[0]?.id || '');
  const [progLevel, setProgLevel] = useState<'UG' | 'PG' | 'DIPLOMA' | 'PHD'>('UG');
  const [progDuration, setProgDuration] = useState(4);
  const [progTerms, setProgTerms] = useState(8);

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const handleCreateDept = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/departments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: deptName, code: deptCode }),
      });
      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to create department.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setDeptName('');
        setDeptCode('');
        setShowAddDept(false);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateProg = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/programs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          departmentId: progDeptId,
          name: progName,
          code: progCode,
          level: progLevel,
          durationYears: Number(progDuration),
          totalTerms: Number(progTerms),
        }),
      });
      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to create program.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setProgName('');
        setProgCode('');
        setShowAddProg(false);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Tab Switcher & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#E3E8EF] pb-4">
        <div className="flex gap-2">
          <button
            onClick={() => setActiveTab('departments')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-[8px] text-xs font-semibold transition-all ${
              activeTab === 'departments'
                ? 'bg-[#1F6FEB] text-white shadow-sm'
                : 'bg-white border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A]'
            }`}
          >
            <Building2 className="h-3.5 w-3.5" />
            Departments ({initialDepartments.length})
          </button>
          <button
            onClick={() => setActiveTab('programs')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-[8px] text-xs font-semibold transition-all ${
              activeTab === 'programs'
                ? 'bg-[#1F6FEB] text-white shadow-sm'
                : 'bg-white border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A]'
            }`}
          >
            <GraduationCap className="h-3.5 w-3.5" />
            Academic Programs ({initialPrograms.length})
          </button>
        </div>

        <div>
          {activeTab === 'departments' ? (
            <Button variant="primary" size="sm" onClick={() => setShowAddDept(!showAddDept)}>
              <Plus className="mr-1.5 h-4 w-4" />
              {showAddDept ? 'Close Form' : 'Add Department'}
            </Button>
          ) : (
            <Button
              variant="primary"
              size="sm"
              disabled={initialDepartments.length === 0}
              onClick={() => setShowAddProg(!showAddProg)}
            >
              <Plus className="mr-1.5 h-4 w-4" />
              {showAddProg ? 'Close Form' : 'Add Program'}
            </Button>
          )}
        </div>
      </div>

      {/* Status Alert */}
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

      {/* Add Department Form Panel */}
      {showAddDept && (
        <Card className="border-[#1F6FEB]/40 bg-[#F6F8FB]/50">
          <CardHeader>
            <CardTitle>Create New Academic Department</CardTitle>
            <CardDescription>
              Adds a departmental organizational unit within your tenant workspace.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateDept} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                  Department Name <span className="text-[#D64545]">*</span>
                </label>
                <Input
                  placeholder="e.g. Computer Science & Engineering"
                  value={deptName}
                  onChange={(e) => setDeptName(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                  Department Code <span className="text-[#D64545]">*</span>
                </label>
                <Input
                  placeholder="e.g. CSE"
                  value={deptCode}
                  onChange={(e) => setDeptCode(e.target.value.toUpperCase())}
                  className="font-mono uppercase"
                  required
                />
              </div>
              <div className="flex items-end gap-2">
                <Button type="submit" variant="primary" isLoading={isLoading} className="w-full">
                  Save Department
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Add Program Form Panel */}
      {showAddProg && (
        <Card className="border-[#1F6FEB]/40 bg-[#F6F8FB]/50">
          <CardHeader>
            <CardTitle>Create Academic Degree Program</CardTitle>
            <CardDescription>
              Degree or diploma program anchored to a department and curriculum.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateProg} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Department <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={progDeptId}
                    onChange={(e) => setProgDeptId(e.target.value)}
                    required
                  >
                    {initialDepartments.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} ({d.code})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Program Name <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. B.Tech Computer Science"
                    value={progName}
                    onChange={(e) => setProgName(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Program Code <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. BT_CSE"
                    value={progCode}
                    onChange={(e) => setProgCode(e.target.value.toUpperCase())}
                    className="font-mono uppercase"
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Level</label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={progLevel}
                    onChange={(e: any) => setProgLevel(e.target.value)}
                  >
                    <option value="UG">Undergraduate (UG)</option>
                    <option value="PG">Postgraduate (PG)</option>
                    <option value="DIPLOMA">Diploma</option>
                    <option value="PHD">Doctoral (PhD)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Duration (Years)
                  </label>
                  <Input
                    type="number"
                    min={1}
                    max={6}
                    value={progDuration}
                    onChange={(e) => setProgDuration(Number(e.target.value))}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Total Semesters</label>
                  <Input
                    type="number"
                    min={1}
                    max={12}
                    value={progTerms}
                    onChange={(e) => setProgTerms(Number(e.target.value))}
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="submit" variant="primary" isLoading={isLoading}>
                  Save Academic Program
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Active Tab Content */}
      {activeTab === 'departments' ? (
        <Card>
          <CardHeader>
            <CardTitle>Academic Departments</CardTitle>
            <CardDescription>
              Configured department structures in core.departments
            </CardDescription>
          </CardHeader>
          <CardContent>
            {initialDepartments.length === 0 ? (
              <EmptyState
                icon={Building2}
                title="No Departments Configured"
                description="Configure your academic departments to begin organizing faculty, degree programs, and curriculum tracks."
                action={{
                  label: 'Add First Department',
                  onClick: () => setShowAddDept(true),
                }}
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-3 font-semibold uppercase tracking-wider">Department Name</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Code</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Programs</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Department UUID</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E3E8EF]">
                    {initialDepartments.map((dept) => {
                      const progCount = initialPrograms.filter((p) => p.department_id === dept.id).length;
                      return (
                        <tr key={dept.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3.5 font-medium text-[#0B1F3A]">
                            <div className="flex items-center gap-2">
                              <Building2 className="h-4 w-4 text-[#1F6FEB]" />
                              <span>{dept.name}</span>
                            </div>
                          </td>
                          <td className="py-3.5 font-mono text-[#1B2433]">{dept.code}</td>
                          <td className="py-3.5">
                            <Badge status={dept.status || 'ACTIVE'} />
                          </td>
                          <td className="py-3.5 text-[#6B7688] font-medium">
                            {progCount} {progCount === 1 ? 'Program' : 'Programs'}
                          </td>
                          <td className="py-3.5 font-mono text-[#6B7688] text-[11px]">{dept.id}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Academic Degree Programs</CardTitle>
            <CardDescription>
              Registered curriculum degrees in acd.programs
            </CardDescription>
          </CardHeader>
          <CardContent>
            {initialPrograms.length === 0 ? (
              <EmptyState
                icon={GraduationCap}
                title="No Academic Programs Configured"
                description="Create undergraduate or postgraduate programs linked to your academic departments."
                action={{
                  label: 'Add First Program',
                  onClick: () => setShowAddProg(true),
                }}
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-3 font-semibold uppercase tracking-wider">Program Name</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Code</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Department</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Level</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Duration</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E3E8EF]">
                    {initialPrograms.map((prog) => {
                      const dept = initialDepartments.find((d) => d.id === prog.department_id);
                      return (
                        <tr key={prog.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3.5 font-medium text-[#0B1F3A]">
                            <div className="flex items-center gap-2">
                              <BookOpen className="h-4 w-4 text-[#1F6FEB]" />
                              <span>{prog.name}</span>
                            </div>
                          </td>
                          <td className="py-3.5 font-mono text-[#1B2433]">{prog.code}</td>
                          <td className="py-3.5 text-[#6B7688]">{dept?.name || 'Department'}</td>
                          <td className="py-3.5">
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#E8F0FE] text-[#1F6FEB]">
                              {prog.level}
                            </span>
                          </td>
                          <td className="py-3.5 text-[#6B7688]">
                            {prog.duration_years} Years ({prog.total_terms} Semesters)
                          </td>
                          <td className="py-3.5">
                            <Badge status={prog.status || 'ACTIVE'} />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
