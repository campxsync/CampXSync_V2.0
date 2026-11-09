'use client';

import React, { useState, useEffect } from 'react';
import {
  ClipboardCheck,
  Calendar,
  Clock,
  User,
  CheckCircle2,
  AlertTriangle,
  FileSpreadsheet,
  ArrowLeft,
  Edit3,
  Search,
  Filter,
  RefreshCw,
  Download,
  ShieldAlert,
  History,
  Check,
  X,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { StatCard } from '@/components/ui/stat-card';
import { formatDate } from '@/lib/utils';

interface AttendanceSession {
  id: string;
  session_date: string;
  start_time?: string;
  end_time?: string;
  status: string;
  locked_at?: string;
  subject_name?: string;
  subject_code?: string;
  faculty_name?: string;
  section_code?: string;
  total_records?: number;
  present_count?: number;
}

interface StudentRecord {
  id: string;
  session_id: string;
  student_id: string;
  status: string;
  method: string;
  remarks?: string;
  marked_at: string;
  row_version: number;
  enrollment_no: string;
  display_name: string;
  primary_email?: string;
}

interface CorrectionHistoryItem {
  id: string;
  recordId: string;
  studentName: string;
  enrollmentNo: string;
  oldStatus: string;
  newStatus: string;
  reason: string;
  timestamp: string;
  oldVersion?: number;
  newVersion?: number;
}

interface ReportStudent {
  studentId: string;
  enrollmentNo: string;
  studentName: string;
  primaryEmail: string;
  programName: string;
  programCode: string;
  totalSessions: number;
  attendedSessions: number;
  absentSessions: number;
  leaveSessions: number;
  attendancePercentage: number;
  hasShortage: boolean;
}

interface Props {
  sessions: AttendanceSession[];
}

export function AttendanceManager({ sessions: initialSessions }: Props) {
  const [activeTab, setActiveTab] = useState<'sessions' | 'report'>('sessions');
  const [sessions, setSessions] = useState<AttendanceSession[]>(initialSessions);
  const [filterStatus, setFilterStatus] = useState<string>('ALL');

  // Selected session detailed view state
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [sessionDetail, setSessionDetail] = useState<any | null>(null);
  const [records, setRecords] = useState<StudentRecord[]>([]);
  const [correctionHistory, setCorrectionHistory] = useState<CorrectionHistoryItem[]>([]);
  const [isLoadingSession, setIsLoadingSession] = useState(false);
  const [studentSearch, setStudentSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Correction Drawer State
  const [correctionRecord, setCorrectionRecord] = useState<StudentRecord | null>(null);
  const [newStatus, setNewStatus] = useState<string>('PRESENT');
  const [correctionReason, setCorrectionReason] = useState<string>('');
  const [isSubmittingCorrection, setIsSubmittingCorrection] = useState(false);
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [correctionSuccess, setCorrectionSuccess] = useState<string | null>(null);

  // Cumulative Report State
  const [reportStudents, setReportStudents] = useState<ReportStudent[]>([]);
  const [reportSummary, setReportSummary] = useState<any | null>(null);
  const [isLoadingReport, setIsLoadingReport] = useState(false);
  const [reportSearch, setReportSearch] = useState('');
  const [shortageOnly, setShortageOnly] = useState(false);

  // Filtered session list
  const filteredSessions = sessions.filter((s) => {
    if (filterStatus === 'ALL') return true;
    return s.status === filterStatus;
  });

  // Fetch Session Records & Corrections
  const loadSessionRecords = async (sessionId: string) => {
    setIsLoadingSession(true);
    setSelectedSessionId(sessionId);
    setCorrectionError(null);
    setCorrectionSuccess(null);

    try {
      const res = await fetch(`/api/college/attendance/records?sessionId=${sessionId}`);
      const data = await res.json();
      if (res.ok && data.success) {
        setSessionDetail(data.session);
        setRecords(data.records);
        setCorrectionHistory(data.correctionHistory || []);
      } else {
        console.error('Failed to load session details:', data);
      }
    } catch (err) {
      console.error('Error fetching session records:', err);
    } finally {
      setIsLoadingSession(false);
    }
  };

  // Fetch Cumulative Report
  const loadReport = async () => {
    setIsLoadingReport(true);
    try {
      const res = await fetch('/api/college/attendance/report');
      const data = await res.json();
      if (res.ok && data.success) {
        setReportStudents(data.students);
        setReportSummary(data.summary);
      }
    } catch (err) {
      console.error('Error fetching attendance report:', err);
    } finally {
      setIsLoadingReport(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'report' && reportStudents.length === 0) {
      loadReport();
    }
  }, [activeTab]);

  // Handle Correction Submission (ACD-06)
  const handleSubmitCorrection = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!correctionRecord || !selectedSessionId) return;

    if (correctionReason.trim().length < 5) {
      setCorrectionError('Mandatory correction reason must be at least 5 characters.');
      return;
    }

    setIsSubmittingCorrection(true);
    setCorrectionError(null);
    setCorrectionSuccess(null);

    try {
      const res = await fetch('/api/college/attendance/corrections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recordId: correctionRecord.id,
          sessionId: selectedSessionId,
          newStatus,
          reason: correctionReason.trim(),
          expectedVersion: correctionRecord.row_version,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        if (res.status === 409) {
          setCorrectionError(
            `Concurrency Conflict: ${data.detail || 'This record was modified by another user. Reloading...'}`
          );
          // Reload latest state
          await loadSessionRecords(selectedSessionId);
        } else {
          setCorrectionError(data.detail || 'Failed to apply attendance correction.');
        }
      } else {
        setCorrectionSuccess(data.message);
        // Refresh local session records
        await loadSessionRecords(selectedSessionId);

        // Update sessions table overview
        setSessions((prev) =>
          prev.map((s) =>
            s.id === selectedSessionId
              ? {
                  ...s,
                  present_count: data.summary.presentCount,
                  total_records: data.summary.total,
                }
              : s
          )
        );

        // Close drawer after brief feedback
        setTimeout(() => {
          setCorrectionRecord(null);
          setCorrectionReason('');
          setCorrectionSuccess(null);
        }, 1200);
      }
    } catch (err: any) {
      setCorrectionError(err.message || 'Network error occurred while submitting correction.');
    } finally {
      setIsSubmittingCorrection(false);
    }
  };

  // Export report to CSV
  const handleExportCSV = () => {
    if (reportStudents.length === 0) return;

    const headers = [
      'Enrollment Number',
      'Student Name',
      'Primary Email',
      'Program',
      'Total Sessions',
      'Attended Sessions',
      'Absent Sessions',
      'Attendance Percentage',
      'Shortage Flag (<75%)',
    ];

    const rows = filteredReportStudents.map((s) => [
      s.enrollmentNo,
      `"${s.studentName}"`,
      s.primaryEmail,
      s.programCode,
      s.totalSessions,
      s.attendedSessions,
      s.absentSessions,
      `${s.attendancePercentage}%`,
      s.hasShortage ? 'YES' : 'NO',
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `Attendance_Report_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Filtered students for report
  const filteredReportStudents = reportStudents.filter((s) => {
    if (shortageOnly && !s.hasShortage) return false;
    if (reportSearch) {
      const q = reportSearch.toLowerCase();
      return (
        s.studentName.toLowerCase().includes(q) ||
        s.enrollmentNo.toLowerCase().includes(q) ||
        s.programCode.toLowerCase().includes(q)
      );
    }
    return true;
  });

  // Filtered records in session inspection
  const filteredRecords = records.filter((r) => {
    if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
    if (studentSearch) {
      const q = studentSearch.toLowerCase();
      return (
        r.display_name.toLowerCase().includes(q) ||
        r.enrollment_no.toLowerCase().includes(q)
      );
    }
    return true;
  });

  return (
    <div className="space-y-6">
      {/* Primary Tab Switcher */}
      <div className="flex border-b border-[#E3E8EF]">
        <button
          onClick={() => {
            setActiveTab('sessions');
            setSelectedSessionId(null);
          }}
          className={`px-4 py-2.5 text-xs font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'sessions'
              ? 'border-[#1F6FEB] text-[#1F6FEB]'
              : 'border-transparent text-[#6B7688] hover:text-[#0B1F3A]'
          }`}
        >
          <ClipboardCheck className="h-4 w-4" />
          Sessions Oversight & Governance
        </button>

        <button
          onClick={() => {
            setActiveTab('report');
            setSelectedSessionId(null);
          }}
          className={`px-4 py-2.5 text-xs font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'report'
              ? 'border-[#1F6FEB] text-[#1F6FEB]'
              : 'border-transparent text-[#6B7688] hover:text-[#0B1F3A]'
          }`}
        >
          <FileSpreadsheet className="h-4 w-4" />
          Institutional Attendance & Shortage Report
        </button>
      </div>

      {/* VIEW 1: SESSIONS OVERSIGHT */}
      {activeTab === 'sessions' && !selectedSessionId && (
        <div className="space-y-6">
          {/* Filter Toolbar */}
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              {['ALL', 'CLOSED', 'OPEN', 'SCHEDULED'].map((st) => (
                <button
                  key={st}
                  onClick={() => setFilterStatus(st)}
                  className={`px-3 py-1.5 rounded-[8px] text-xs font-semibold transition-all ${
                    filterStatus === st
                      ? 'bg-[#1F6FEB] text-white shadow-sm'
                      : 'bg-white border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A]'
                  }`}
                >
                  {st === 'ALL' ? 'All Sessions' : st}
                </button>
              ))}
            </div>
            <p className="text-xs text-[#6B7688]">
              Click any session row to inspect student roster, review attendance, and perform audited corrections.
            </p>
          </div>

          {/* Sessions Table */}
          <Card>
            <CardHeader>
              <CardTitle>Attendance Sessions ({filteredSessions.length})</CardTitle>
              <CardDescription>
                Live class session logs from acd.attendance_sessions and acd.attendance_records
              </CardDescription>
            </CardHeader>
            <CardContent>
              {filteredSessions.length === 0 ? (
                <EmptyState
                  icon={ClipboardCheck}
                  title="No Attendance Sessions Recorded"
                  description="Sessions are generated when faculty open class slots from their teaching timetable."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                        <th className="pb-3 font-semibold uppercase tracking-wider">Date</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Time Slot</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Subject & Section</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Instructor</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Attendance %</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {filteredSessions.map((sess) => {
                        const total = sess.total_records || 0;
                        const present = sess.present_count || 0;
                        const percentage = total > 0 ? Math.round((present / total) * 100) : 0;

                        return (
                          <tr
                            key={sess.id}
                            onClick={() => loadSessionRecords(sess.id)}
                            className="hover:bg-[#F6F8FB] cursor-pointer transition-colors"
                          >
                            <td className="py-3.5 font-medium text-[#0B1F3A]">
                              <div className="flex items-center gap-1.5">
                                <Calendar className="h-3.5 w-3.5 text-[#1F6FEB]" />
                                <span>{formatDate(sess.session_date)}</span>
                              </div>
                            </td>
                            <td className="py-3.5 font-mono text-[#6B7688]">
                              {sess.start_time?.substring(0, 5)} - {sess.end_time?.substring(0, 5)}
                            </td>
                            <td className="py-3.5">
                              <p className="font-semibold text-[#0B1F3A]">
                                {sess.subject_name || sess.subject_code || 'Subject'}
                              </p>
                              <span className="text-[11px] font-mono text-[#6B7688]">
                                {sess.section_code || 'Section'}
                              </span>
                            </td>
                            <td className="py-3.5 text-[#1B2433]">
                              <div className="flex items-center gap-1">
                                <User className="h-3.5 w-3.5 text-[#6B7688]" />
                                <span>{sess.faculty_name || 'Faculty'}</span>
                              </div>
                            </td>
                            <td className="py-3.5">
                              <Badge status={sess.status} />
                            </td>
                            <td className="py-3.5">
                              {sess.status === 'CLOSED' ? (
                                <div className="flex items-center gap-2">
                                  <span className={`font-bold ${percentage < 75 ? 'text-[#D64545]' : 'text-[#107C41]'}`}>
                                    {percentage}%
                                  </span>
                                  <span className="text-[10px] text-[#6B7688]">
                                    ({present}/{total})
                                  </span>
                                </div>
                              ) : (
                                <span className="text-[11px] text-[#6B7688] italic">Pending closure</span>
                              )}
                            </td>
                            <td className="py-3.5 text-right">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  loadSessionRecords(sess.id);
                                }}
                              >
                                View Records & Correct
                              </Button>
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
        </div>
      )}

      {/* VIEW 2: SELECTED SESSION DETAILED VIEW & CORRECTION WORKFLOW */}
      {activeTab === 'sessions' && selectedSessionId && (
        <div className="space-y-6">
          {/* Back & Breadcrumb Header */}
          <div className="flex items-center justify-between">
            <button
              onClick={() => setSelectedSessionId(null)}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#1F6FEB] hover:underline"
            >
              <ArrowLeft className="h-4 w-4" />
              Back to All Sessions
            </button>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => loadSessionRecords(selectedSessionId)}
                disabled={isLoadingSession}
              >
                <RefreshCw className={`h-3.5 w-3.5 mr-1 ${isLoadingSession ? 'animate-spin' : ''}`} />
                Refresh Records
              </Button>
            </div>
          </div>

          {sessionDetail && (
            <Card className="border-l-4 border-l-[#1F6FEB]">
              <CardContent className="pt-4 pb-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-lg font-bold text-[#0B1F3A]">
                        {sessionDetail.subject_name} ({sessionDetail.subject_code})
                      </h2>
                      <Badge status={sessionDetail.status} />
                    </div>
                    <p className="text-xs text-[#6B7688] mt-1">
                      Date: <span className="font-semibold text-[#0B1F3A]">{formatDate(sessionDetail.session_date)}</span> · 
                      Time: <span className="font-mono">{sessionDetail.start_time?.substring(0, 5)} - {sessionDetail.end_time?.substring(0, 5)}</span> · 
                      Section: <span className="font-semibold text-[#0B1F3A]">{sessionDetail.section_code}</span> · 
                      Instructor: <span className="font-semibold text-[#0B1F3A]">{sessionDetail.faculty_name}</span>
                    </p>
                  </div>

                  <div className="flex items-center gap-4 bg-[#F6F8FB] px-4 py-2.5 rounded-[8px] border border-[#E3E8EF]">
                    <div className="text-center">
                      <span className="text-[10px] text-[#6B7688] uppercase font-bold tracking-wider block">Total</span>
                      <span className="text-base font-bold text-[#0B1F3A]">{records.length}</span>
                    </div>
                    <div className="w-[1px] h-6 bg-[#E3E8EF]" />
                    <div className="text-center">
                      <span className="text-[10px] text-[#107C41] uppercase font-bold tracking-wider block">Present</span>
                      <span className="text-base font-bold text-[#107C41]">
                        {records.filter((r) => r.status === 'PRESENT').length}
                      </span>
                    </div>
                    <div className="w-[1px] h-6 bg-[#E3E8EF]" />
                    <div className="text-center">
                      <span className="text-[10px] text-[#D64545] uppercase font-bold tracking-wider block">Absent</span>
                      <span className="text-base font-bold text-[#D64545]">
                        {records.filter((r) => r.status === 'ABSENT').length}
                      </span>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Student Roster & Record Correction Table */}
          <Card>
            <CardHeader>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div>
                  <CardTitle>Session Attendance Roster ({records.length} Students)</CardTitle>
                  <CardDescription>
                    Per-student attendance records for this period. College administrators may correct errors with mandatory justification.
                  </CardDescription>
                </div>

                {/* Filter and Search */}
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-[#6B7688]" />
                    <Input
                      placeholder="Search student or roll no..."
                      value={studentSearch}
                      onChange={(e) => setStudentSearch(e.target.value)}
                      className="pl-8 h-8 text-xs w-48"
                    />
                  </div>

                  <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="h-8 px-2.5 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433]"
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="PRESENT">Present</option>
                    <option value="ABSENT">Absent</option>
                    <option value="LEAVE">Leave</option>
                  </select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {filteredRecords.length === 0 ? (
                <EmptyState
                  icon={ClipboardCheck}
                  title="No Student Records Found"
                  description="No records match your search filter or session is empty."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                        <th className="pb-3 font-semibold uppercase tracking-wider">Roll No</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Student Name</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Attendance Status</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Marked Time</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Version</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Justification / Notes</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider text-right">Administrative Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {filteredRecords.map((r) => (
                        <tr key={r.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3 font-mono font-semibold text-[#0B1F3A]">
                            {r.enrollment_no}
                          </td>
                          <td className="py-3">
                            <span className="font-semibold text-[#0B1F3A] block">{r.display_name}</span>
                            <span className="text-[10px] text-[#6B7688] font-mono">{r.primary_email}</span>
                          </td>
                          <td className="py-3">
                            <Badge status={r.status} />
                          </td>
                          <td className="py-3 text-[11px] text-[#6B7688] font-mono">
                            {r.marked_at ? new Date(r.marked_at).toLocaleTimeString() : 'N/A'}
                          </td>
                          <td className="py-3 text-[11px] font-mono text-[#6B7688]">
                            v{r.row_version}
                          </td>
                          <td className="py-3 text-[11px] text-[#6B7688] max-w-xs truncate">
                            {r.remarks || '—'}
                          </td>
                          <td className="py-3 text-right">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => {
                                setCorrectionRecord(r);
                                setNewStatus(r.status === 'PRESENT' ? 'ABSENT' : 'PRESENT');
                                setCorrectionReason('');
                                setCorrectionError(null);
                                setCorrectionSuccess(null);
                              }}
                            >
                              <Edit3 className="h-3 w-3 mr-1" />
                              Correct Record
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Session Correction History Timeline */}
          {correctionHistory.length > 0 && (
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <History className="h-4 w-4 text-[#1F6FEB]" />
                  <CardTitle>Session Correction Audit Trail ({correctionHistory.length})</CardTitle>
                </div>
                <CardDescription>
                  Immutable change logs from audit.change_log & audit.events for this session
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  {correctionHistory.map((item) => (
                    <div
                      key={item.id}
                      className="flex flex-col sm:flex-row sm:items-center justify-between p-3 rounded-[8px] border border-[#E3E8EF] bg-[#F6F8FB] text-xs gap-2"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-[#0B1F3A]">{item.studentName}</span>
                          <span className="font-mono text-[10px] text-[#6B7688]">({item.enrollmentNo})</span>
                          <span className="text-[#6B7688]">status changed:</span>
                          <span className="font-bold text-[#D64545]">{item.oldStatus}</span>
                          <span>→</span>
                          <span className="font-bold text-[#107C41]">{item.newStatus}</span>
                        </div>
                        <p className="text-[11px] text-[#6B7688]">
                          Reason: <span className="italic text-[#0B1F3A]">"{item.reason}"</span>
                        </p>
                      </div>

                      <div className="text-right text-[10px] text-[#6B7688] font-mono shrink-0">
                        <div>{new Date(item.timestamp).toLocaleString()}</div>
                        <div className="text-[#107C41]">Audit Verified</div>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {/* CORRECTION DRAWER / MODAL DIALOG */}
          {correctionRecord && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in">
              <div className="bg-white rounded-[12px] border border-[#E3E8EF] shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto animate-in zoom-in-95">
                <div className="px-5 py-4 border-b border-[#E3E8EF] flex items-center justify-between bg-[#F6F8FB] sticky top-0 z-10">
                  <div className="flex items-center gap-2">
                    <Edit3 className="h-4 w-4 text-[#1F6FEB]" />
                    <h3 className="text-sm font-bold text-[#0B1F3A]">Administrative Attendance Correction</h3>
                  </div>
                  <button
                    onClick={() => setCorrectionRecord(null)}
                    aria-label="Close correction modal"
                    className="text-[#6B7688] hover:text-[#0B1F3A] p-1.5 rounded-[6px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>

                <form onSubmit={handleSubmitCorrection} className="p-5 space-y-4 text-xs">
                  {/* Student Details Summary */}
                  <div className="p-3 rounded-[8px] bg-[#E8F0FE]/40 border border-[#1F6FEB]/20 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-[#6B7688]">Student:</span>
                      <span className="font-semibold text-[#0B1F3A]">{correctionRecord.display_name}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#6B7688]">Roll / Enrollment:</span>
                      <span className="font-mono font-semibold text-[#0B1F3A]">{correctionRecord.enrollment_no}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#6B7688]">Current Recorded Status:</span>
                      <Badge status={correctionRecord.status} />
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#6B7688]">Expected Concurrency Version:</span>
                      <span className="font-mono text-[#6B7688]">v{correctionRecord.row_version}</span>
                    </div>
                  </div>

                  {/* New Status Selection */}
                  <div>
                    <label className="block text-xs font-semibold text-[#0B1F3A] mb-1.5">
                      New Corrected Status <span className="text-[#D64545]">*</span>
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                      {['PRESENT', 'ABSENT', 'LEAVE', 'LATE', 'ON_DUTY', 'EXCUSED'].map((st) => (
                        <button
                          key={st}
                          type="button"
                          onClick={() => setNewStatus(st)}
                          className={`py-2 px-3 rounded-[8px] font-semibold text-center border transition-all text-xs ${
                            newStatus === st
                              ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB] font-bold ring-1 ring-[#1F6FEB]'
                              : 'border-[#E3E8EF] bg-white text-[#1B2433] hover:bg-[#F6F8FB]'
                          }`}
                        >
                          {st}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Mandatory Reason */}
                  <div>
                    <label className="block text-xs font-semibold text-[#0B1F3A] mb-1.5">
                      Mandatory Correction Justification <span className="text-[#D64545]">*</span>
                    </label>
                    <textarea
                      required
                      rows={3}
                      value={correctionReason}
                      onChange={(e) => setCorrectionReason(e.target.value)}
                      placeholder="e.g. Medical certificate approved by HOD, official sports representation duty, faculty marking discrepancy rectified."
                      className="w-full p-2.5 text-xs rounded-[8px] border border-[#E3E8EF] text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    />
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {[
                        'Medical certificate submitted',
                        'Official university sports duty',
                        'Faculty correction request',
                        'Late arrival admitted',
                      ].map((preset) => (
                        <button
                          key={preset}
                          type="button"
                          onClick={() => setCorrectionReason(preset)}
                          className="text-[10px] bg-[#F6F8FB] hover:bg-[#E3E8EF] text-[#6B7688] px-2 py-0.5 rounded border border-[#E3E8EF]"
                        >
                          + {preset}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Feedback Messages */}
                  {correctionError && (
                    <div className="p-3 rounded-[8px] bg-[#FDF2F2] border border-[#D64545]/20 text-[#D64545] flex items-start gap-2">
                      <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                      <div>
                        <p className="font-semibold">Correction Blocked</p>
                        <p className="text-[11px] mt-0.5">{correctionError}</p>
                      </div>
                    </div>
                  )}

                  {correctionSuccess && (
                    <div className="p-3 rounded-[8px] bg-[#EAF8F1] border border-[#1E9E6A]/20 text-[#1E9E6A] flex items-start gap-2">
                      <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
                      <p className="font-semibold">{correctionSuccess}</p>
                    </div>
                  )}

                  {/* Actions */}
                  <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#E3E8EF]">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setCorrectionRecord(null)}
                      disabled={isSubmittingCorrection}
                    >
                      Cancel
                    </Button>
                    <Button
                      type="submit"
                      variant="primary"
                      size="sm"
                      disabled={isSubmittingCorrection || correctionReason.trim().length < 5}
                    >
                      {isSubmittingCorrection ? 'Auditing & Saving...' : 'Submit Audited Correction'}
                    </Button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* VIEW 3: CUMULATIVE ATTENDANCE REPORT & SHORTAGE ANALYTICS */}
      {activeTab === 'report' && (
        <div className="space-y-6">
          {/* Summary KPIs */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatCard
              title="Total Enrolled Students"
              value={reportSummary ? reportSummary.totalStudents.toString() : '...'}
              icon={User}
              description="Active institutional enrollment"
            />
            <StatCard
              title="Average Attendance"
              value={reportSummary ? `${reportSummary.averageAttendance}%` : '...'}
              icon={CheckCircle2}
              description="Across all classes and batches"
            />
            <StatCard
              title="Students with Shortage (<75%)"
              value={reportSummary ? reportSummary.shortageCount.toString() : '...'}
              icon={AlertTriangle}
              description="Statutory shortage threshold alert"
            />
          </div>

          {/* Report Toolbar */}
          <Card>
            <CardHeader>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <CardTitle>Institutional Attendance Roster & Shortage Tracker</CardTitle>
                  <CardDescription>
                    Per-student cumulative attendance percentages across all published lectures.
                  </CardDescription>
                </div>

                <div className="flex items-center gap-3">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-[#6B7688]" />
                    <Input
                      placeholder="Search student or program..."
                      value={reportSearch}
                      onChange={(e) => setReportSearch(e.target.value)}
                      className="pl-8 h-8 text-xs w-48"
                    />
                  </div>

                  <label className="flex items-center gap-1.5 text-xs text-[#0B1F3A] font-medium cursor-pointer">
                    <input
                      type="checkbox"
                      checked={shortageOnly}
                      onChange={(e) => setShortageOnly(e.target.checked)}
                      className="rounded border-[#E3E8EF] text-[#1F6FEB] focus:ring-[#1F6FEB]"
                    />
                    <span>Shortage Only (&lt;75%)</span>
                  </label>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleExportCSV}
                    disabled={filteredReportStudents.length === 0}
                  >
                    <Download className="h-3.5 w-3.5 mr-1" />
                    Export CSV
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {isLoadingReport ? (
                <div className="text-center py-12">
                  <RefreshCw className="mx-auto h-8 w-8 text-[#1F6FEB] animate-spin" />
                  <p className="text-xs text-[#6B7688] mt-2">Computing cumulative attendance analytics...</p>
                </div>
              ) : filteredReportStudents.length === 0 ? (
                <EmptyState
                  icon={ClipboardCheck}
                  title="No Student Records in Scope"
                  description="No students match the selected filter criteria."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                        <th className="pb-3 font-semibold uppercase tracking-wider">Roll No</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Student Name</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Program</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider text-center">Conducted</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider text-center">Attended</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Percentage</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider">Governance Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {filteredReportStudents.map((s) => (
                        <tr key={s.studentId} className="hover:bg-[#F6F8FB]">
                          <td className="py-3.5 font-mono font-semibold text-[#0B1F3A]">
                            {s.enrollmentNo}
                          </td>
                          <td className="py-3.5">
                            <span className="font-semibold text-[#0B1F3A] block">{s.studentName}</span>
                            <span className="text-[10px] text-[#6B7688] font-mono">{s.primaryEmail}</span>
                          </td>
                          <td className="py-3.5 text-[#1B2433]">
                            <span className="font-medium">{s.programName}</span>
                            <span className="text-[10px] text-[#6B7688] font-mono block">{s.programCode}</span>
                          </td>
                          <td className="py-3.5 text-center font-mono text-[#6B7688]">
                            {s.totalSessions}
                          </td>
                          <td className="py-3.5 text-center font-mono font-semibold text-[#107C41]">
                            {s.attendedSessions}
                          </td>
                          <td className="py-3.5">
                            <div className="flex items-center gap-2">
                              <div className="w-16 bg-[#E3E8EF] rounded-full h-2 overflow-hidden">
                                <div
                                  className={`h-full rounded-full ${
                                    s.hasShortage ? 'bg-[#D64545]' : 'bg-[#107C41]'
                                  }`}
                                  style={{ width: `${Math.min(s.attendancePercentage, 100)}%` }}
                                />
                              </div>
                              <span
                                className={`font-bold font-mono ${
                                  s.hasShortage ? 'text-[#D64545]' : 'text-[#107C41]'
                                }`}
                              >
                                {s.attendancePercentage}%
                              </span>
                            </div>
                          </td>
                          <td className="py-3.5">
                            {s.hasShortage ? (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-[#FDF2F2] text-[#D64545] border border-[#D64545]/20">
                                <AlertTriangle className="h-3 w-3" />
                                Shortage (&lt;75%)
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-[#EAF8F1] text-[#107C41] border border-[#107C41]/20">
                                <Check className="h-3 w-3" />
                                Normal
                              </span>
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
        </div>
      )}
    </div>
  );
}
