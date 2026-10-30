'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  GraduationCap,
  Plus,
  Mail,
  BookOpen,
  CheckCircle2,
  AlertCircle,
  Layers,
  Trash2,
  UserPlus,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

interface Program {
  id: string;
  name: string;
  code: string;
}

interface Batch {
  id: string;
  name: string;
  code: string;
  program_id?: string;
}

interface Student {
  id: string;
  enrollment_no: string;
  status: string;
  program_id: string;
  program_name?: string;
  batch_id?: string;
  batch_name?: string;
  batch_code?: string;
  roll_no?: string;
  display_name: string;
  primary_email: string;
  created_at: string;
}

interface Props {
  initialStudents: Student[];
  programs: Program[];
  batches?: Batch[];
}

export function StudentRoster({ initialStudents, programs, batches = [] }: Props) {
  const router = useRouter();
  const [showAddModal, setShowAddModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterProg, setFilterProg] = useState('');
  const [filterBatch, setFilterBatch] = useState('');

  // Form states for enroll
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [enrollmentNo, setEnrollmentNo] = useState('');
  const [programId, setProgramId] = useState(programs[0]?.id || '');
  const [batchId, setBatchId] = useState('');

  // Assign batch modal states
  const [assignStudent, setAssignStudent] = useState<Student | null>(null);
  const [assignBatchId, setAssignBatchId] = useState('');
  const [assignRollNo, setAssignRollNo] = useState('');

  // Delete modal state
  const [studentToDelete, setStudentToDelete] = useState<Student | null>(null);

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const handleCreateStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/students', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName,
          lastName,
          email,
          enrollmentNo,
          programId,
          batchId: batchId || undefined,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to enroll student.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setFirstName('');
        setLastName('');
        setEmail('');
        setEnrollmentNo('');
        setBatchId('');
        setShowAddModal(false);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleAssignBatchSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!assignStudent || !assignBatchId) return;

    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/students/assign-batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          studentId: assignStudent.id,
          batchId: assignBatchId,
          rollNo: assignRollNo || undefined,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to assign batch.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setAssignStudent(null);
        setAssignBatchId('');
        setAssignRollNo('');
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteStudentSubmit = async () => {
    if (!studentToDelete) return;

    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/students', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: studentToDelete.id }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to deactivate student.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setStudentToDelete(null);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const filtered = initialStudents.filter((s) => {
    const matchesSearch =
      s.display_name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.enrollment_no.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.primary_email.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesProg = !filterProg || s.program_id === filterProg;
    const matchesBatch = !filterBatch || s.batch_id === filterBatch;
    return matchesSearch && matchesProg && matchesBatch;
  });

  return (
    <div className="space-y-6">
      {/* Header Toolbar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <Input
            placeholder="Search students by name, enrollment number, or email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="max-w-xs"
          />

          <select
            className="h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
            value={filterProg}
            onChange={(e) => setFilterProg(e.target.value)}
          >
            <option value="">All Degree Programs</option>
            {programs.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.code})
              </option>
            ))}
          </select>

          <select
            className="h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
            value={filterBatch}
            onChange={(e) => setFilterBatch(e.target.value)}
          >
            <option value="">All Academic Batches</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name} ({b.code})
              </option>
            ))}
          </select>
        </div>

        <Button
          variant="primary"
          size="sm"
          disabled={programs.length === 0}
          onClick={() => setShowAddModal(!showAddModal)}
        >
          <Plus className="mr-1.5 h-4 w-4" />
          {showAddModal ? 'Close Form' : 'Enroll New Student'}
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

      {/* Add Student Form Drawer */}
      {showAddModal && (
        <Card className="border-[#1F6FEB]/40 bg-[#F6F8FB]/50">
          <CardHeader>
            <CardTitle>Enroll New Student</CardTitle>
            <CardDescription>
              Registers human identity in core.persons, student academic membership in stu.students, and optional batch allocation.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateStudent} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    First Name <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. Alice"
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
                    placeholder="e.g. Smith"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Official / Student Email <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    type="email"
                    placeholder="e.g. a.smith@student.institution.edu"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Enrollment / Roll No. <span className="text-[#D64545]">*</span>
                  </label>
                  <Input
                    placeholder="e.g. 2026-CSE-042"
                    value={enrollmentNo}
                    onChange={(e) => setEnrollmentNo(e.target.value.toUpperCase())}
                    className="font-mono uppercase"
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Degree Program <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={programId}
                    onChange={(e) => setProgramId(e.target.value)}
                    required
                  >
                    {programs.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({p.code})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Initial Academic Batch
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={batchId}
                    onChange={(e) => setBatchId(e.target.value)}
                  >
                    <option value="">Select Batch (Optional)</option>
                    {batches.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name} ({b.code})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="ghost" onClick={() => setShowAddModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" isLoading={isLoading}>
                  Confirm Student Enrollment
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Assign Batch Modal */}
      {assignStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md shadow-xl border-[#1F6FEB]/30 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Layers className="h-5 w-5 text-[#1F6FEB]" />
                Assign Student to Batch
              </CardTitle>
              <CardDescription>
                Assign <strong>{assignStudent.display_name}</strong> ({assignStudent.enrollment_no}) to an academic cohort in acd.batch_students.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleAssignBatchSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Select Target Batch <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={assignBatchId}
                    onChange={(e) => setAssignBatchId(e.target.value)}
                    required
                  >
                    <option value="">Choose Batch...</option>
                    {batches.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name} ({b.code})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Cohort Roll No. (Optional)
                  </label>
                  <Input
                    placeholder={assignStudent.enrollment_no}
                    value={assignRollNo}
                    onChange={(e) => setAssignRollNo(e.target.value.toUpperCase())}
                    className="font-mono uppercase"
                  />
                  <p className="text-[11px] text-[#6B7688] mt-1">
                    Defaults to student enrollment number if left blank.
                  </p>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => {
                      setAssignStudent(null);
                      setAssignBatchId('');
                      setAssignRollNo('');
                    }}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" variant="primary" isLoading={isLoading} disabled={!assignBatchId}>
                    Assign to Batch
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Delete/Deactivate Confirmation Modal */}
      {studentToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md shadow-xl border-[#D64545]/30 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2 text-[#D64545]">
                <Trash2 className="h-5 w-5 text-[#D64545]" />
                Confirm Student Deactivation
              </CardTitle>
              <CardDescription>
                Are you sure you want to deactivate <strong>{studentToDelete.display_name}</strong> ({studentToDelete.enrollment_no})?
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-xs text-[#6B7688]">
                This will soft-delete their academic student record, mark all active batch memberships as inactive, and log an immutable audit event in <code className="font-mono text-[#0B1F3A]">audit.events</code>.
              </p>

              <div className="flex justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setStudentToDelete(null)}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  className="bg-[#D64545] hover:bg-[#B93838] text-white"
                  isLoading={isLoading}
                  onClick={handleDeleteStudentSubmit}
                >
                  Deactivate Student
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Roster Table */}
      <Card>
        <CardHeader>
          <CardTitle>Enrolled Student Roster ({filtered.length})</CardTitle>
          <CardDescription>
            Active student records registered in stu.students and core.persons
          </CardDescription>
        </CardHeader>
        <CardContent>
          {filtered.length === 0 ? (
            <EmptyState
              icon={GraduationCap}
              title="No Students Enrolled"
              description="Enroll undergraduate or graduate students to assign them to batches, subject sections, and track attendance."
              action={
                programs.length > 0
                  ? {
                      label: 'Enroll First Student',
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
                    <th className="pb-3 font-semibold uppercase tracking-wider">Student Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Enrollment No.</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Degree Program</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Assigned Batch</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Student Email</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {filtered.map((stu) => (
                    <tr key={stu.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3.5 font-medium text-[#0B1F3A]">
                        <div className="flex items-center gap-2">
                          <div className="h-7 w-7 rounded-full bg-[#E8F0FE] text-[#1F6FEB] flex items-center justify-center font-bold text-[10px]">
                            {stu.display_name.charAt(0)}
                          </div>
                          <span>{stu.display_name}</span>
                        </div>
                      </td>
                      <td className="py-3.5 font-mono text-[#1B2433]">{stu.enrollment_no}</td>
                      <td className="py-3.5 text-[#1B2433]">
                        {stu.program_name || 'Program'}
                      </td>
                      <td className="py-3.5">
                        {stu.batch_code ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#E8F0FE] text-[#1F6FEB]">
                            <Layers className="h-3 w-3" />
                            {stu.batch_code}
                          </span>
                        ) : (
                          <button
                            type="button"
                            onClick={() => {
                              setAssignStudent(stu);
                              setAssignBatchId(batches[0]?.id || '');
                            }}
                            className="text-[11px] text-[#6B7688] hover:text-[#1F6FEB] underline decoration-dashed flex items-center gap-1"
                          >
                            <UserPlus className="h-3 w-3" />
                            Assign Batch
                          </button>
                        )}
                      </td>
                      <td className="py-3.5 text-[#6B7688]">
                        <div className="flex items-center gap-1.5">
                          <Mail className="h-3.5 w-3.5 text-[#6B7688]" />
                          <span>{stu.primary_email}</span>
                        </div>
                      </td>
                      <td className="py-3.5">
                        <Badge status={stu.status || 'ACTIVE'} />
                      </td>
                      <td className="py-3.5 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-[11px]"
                            title="Assign or Change Batch"
                            onClick={() => {
                              setAssignStudent(stu);
                              setAssignBatchId(stu.batch_id || batches[0]?.id || '');
                              setAssignRollNo(stu.roll_no || '');
                            }}
                          >
                            <Layers className="h-3.5 w-3.5 text-[#1F6FEB]" />
                            <span className="sr-only sm:not-sr-only sm:ml-1 text-[#1F6FEB]">Batch</span>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-[11px] hover:text-[#D64545]"
                            title="Deactivate Student"
                            onClick={() => setStudentToDelete(stu)}
                          >
                            <Trash2 className="h-3.5 w-3.5 text-[#D64545]" />
                          </Button>
                        </div>
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

