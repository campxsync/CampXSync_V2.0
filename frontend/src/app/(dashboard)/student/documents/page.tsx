'use client';

import React, { useState, useEffect } from 'react';
import {
  FileText,
  Upload,
  CheckCircle2,
  AlertCircle,
  Clock,
  XCircle,
  Loader2,
  RefreshCw,
  Info,
  ShieldCheck,
  FileCheck,
  PlusCircle,
  X,
  FileCode,
  RotateCcw,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface StageRequirement {
  id: string;
  tenantId: string;
  programId?: string | null;
  programCode?: string | null;
  academicStage: string;
  documentTypeId: string;
  documentTypeCode: string;
  documentTypeName: string;
  isMandatory: boolean;
  minCount: number;
  maxCount: number;
  submissionDeadlineDays?: number | null;
  description?: string | null;
  isActive: boolean;
}

interface SubmittedDocument {
  id: string;
  tenantId: string;
  documentTypeId: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  title: string;
  classification: string;
  status: 'SUBMITTED' | 'VERIFIED' | 'REJECTED' | 'ARCHIVED';
  storageBucket: string;
  storagePath: string;
  mimeType: string;
  sizeBytes: number;
  currentVersionNo: number;
  createdAt: string;
  latestVerificationResult?: string | null;
  latestVerificationCode?: string | null;
  latestVerifiedAt?: string | null;
  expiresAt?: string | null;
  retentionPolicyCode?: string | null;
}

interface DocumentAccessLog {
  id: string;
  documentId: string;
  channel: string;
  status: string;
  clientIp?: string | null;
  deliveredAt: string;
  createdAt: string;
  createdBy?: string | null;
}

interface ChecklistItem {
  requirement: StageRequirement;
  status: 'PENDING' | 'SUBMITTED' | 'VERIFIED' | 'REJECTED';
  submittedDocuments: SubmittedDocument[];
}

const STAGES = [
  { value: 'ADMISSION', label: 'Admission' },
  { value: 'ENROLLMENT', label: 'Enrollment' },
  { value: 'EXAMINATION', label: 'Examination' },
  { value: 'ANNUAL_PROGRESSION', label: 'Annual Progression' },
  { value: 'GRADUATION', label: 'Graduation' },
];

function formatBytes(bytes?: number): string {
  if (!bytes || bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function StudentDocumentsPage() {
  const [activeTab, setActiveTab] = useState<'stage' | 'submissions'>('stage');
  const [selectedStage, setSelectedStage] = useState('ADMISSION');
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [errorRefId, setErrorRefId] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Data states
  const [checklist, setChecklist] = useState<ChecklistItem[]>([]);
  const [submissions, setSubmissions] = useState<SubmittedDocument[]>([]);

  // Modal & Upload states
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const [modalRefId, setModalRefId] = useState<string | null>(null);

  // Form Fields
  const [targetReqId, setTargetReqId] = useState<string>('');
  const [targetDocTypeId, setTargetDocTypeId] = useState<string>('');
  const [docTitle, setDocTitle] = useState('');
  const [changeNote, setChangeNote] = useState('');
  const [fileName, setFileName] = useState('student_document.pdf');
  const [mimeType, setMimeType] = useState('application/pdf');
  const [fileSizeBytes, setFileSizeBytes] = useState<number>(1048576); // ~1MB default

  // Field validation errors
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Resubmission modal states (STM-06-US-005)
  const [isResubmitModalOpen, setIsResubmitModalOpen] = useState(false);
  const [selectedDocForResubmit, setSelectedDocForResubmit] = useState<SubmittedDocument | null>(null);
  const [resubmitFileName, setResubmitFileName] = useState('corrected_document.pdf');
  const [resubmitMimeType, setResubmitMimeType] = useState('application/pdf');
  const [resubmitSizeBytes, setResubmitSizeBytes] = useState<number>(2097152); // ~2MB default
  const [resubmitChangeNote, setResubmitChangeNote] = useState('');
  const [isResubmitting, setIsResubmitting] = useState(false);
  const [resubmitError, setResubmitError] = useState<string | null>(null);
  const [resubmitRefId, setResubmitRefId] = useState<string | null>(null);

  function handleOpenResubmitModal(doc: SubmittedDocument) {
    setSelectedDocForResubmit(doc);
    setResubmitChangeNote('');
    setResubmitFileName(
      doc.title
        ? `${doc.title.toLowerCase().replace(/[^a-z0-9]/g, '_')}_v${(doc.currentVersionNo || 1) + 1}.pdf`
        : 'corrected_document.pdf'
    );
    setResubmitError(null);
    setResubmitRefId(null);
    setIsResubmitModalOpen(true);
  }

  async function handleSubmitResubmission(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedDocForResubmit) return;

    if (!resubmitChangeNote.trim() || resubmitChangeNote.trim().length < 3) {
      setResubmitError('Please provide a detailed note explaining what was corrected (at least 3 characters).');
      return;
    }

    setIsResubmitting(true);
    setResubmitError(null);
    setResubmitRefId(null);

    try {
      const res = await fetch('/api/student/documents/resubmit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          documentId: selectedDocForResubmit.id,
          fileName: resubmitFileName,
          mimeType: resubmitMimeType,
          sizeBytes: resubmitSizeBytes,
          checksumSha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
          changeNote: resubmitChangeNote.trim(),
        }),
      });

      const trace = res.headers.get('x-trace-id') || res.headers.get('x-correlation-id');
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        setResubmitRefId(data.traceId || trace);
        throw new Error(data.message || `Resubmission failed with HTTP ${res.status}`);
      }

      await fetchChecklistAndSubmissions();
      setSuccessMsg(`Document "${selectedDocForResubmit.title}" successfully resubmitted as version v${data.currentVersionNo || (selectedDocForResubmit.currentVersionNo + 1)}! Status reset to Submitted.`);
      setIsResubmitModalOpen(false);
      setSelectedDocForResubmit(null);
    } catch (err: any) {
      setResubmitError(err.message || 'An error occurred during document resubmission');
    } finally {
      setIsResubmitting(false);
    }
  }

  // STM-06-US-006: Audit Trail & Retention States
  const [selectedAuditDoc, setSelectedAuditDoc] = useState<SubmittedDocument | null>(null);
  const [auditLogs, setAuditLogs] = useState<DocumentAccessLog[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditDownloading, setAuditDownloading] = useState(false);
  const [isAuditModalOpen, setIsAuditModalOpen] = useState(false);

  async function openAuditModal(doc: SubmittedDocument) {
    setSelectedAuditDoc(doc);
    setIsAuditModalOpen(true);
    setAuditLoading(true);
    try {
      const res = await fetch(`/api/student/documents/${doc.id}/audit-trail`, { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setAuditLogs(data.items || []);
      }
    } catch (err) {
      console.error('Failed to load audit trail', err);
    } finally {
      setAuditLoading(false);
    }
  }

  async function handleDownloadAndLogAccess() {
    if (!selectedAuditDoc) return;
    setAuditDownloading(true);
    try {
      const res = await fetch(`/api/student/documents/${selectedAuditDoc.id}/access-log`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ channel: 'DOWNLOAD' }),
      });
      if (res.ok) {
        const newLog = await res.json();
        setAuditLogs((prev) => [newLog, ...prev]);
        setSuccessMsg(`Document downloaded & access logged: ID ${newLog.id ? newLog.id.substring(0, 8) : ''}`);
      }
    } catch (err) {
      console.error('Failed to record access log', err);
    } finally {
      setAuditDownloading(false);
    }
  }

  useEffect(() => {
    fetchChecklistAndSubmissions();
  }, [selectedStage]);

  async function fetchChecklistAndSubmissions() {
    setLoading(true);
    setErrorMsg(null);
    setErrorRefId(null);
    try {
      // 1. Fetch stage checklist
      const chkRes = await fetch(`/api/student/documents/checklist?stage=${selectedStage}`, { cache: 'no-store' });
      const chkTrace = chkRes.headers.get('x-trace-id') || chkRes.headers.get('x-correlation-id');
      if (!chkRes.ok) {
        const errJson = await chkRes.json().catch(() => ({}));
        setErrorRefId(errJson.traceId || chkTrace);
        throw new Error(errJson.message || `Failed to fetch checklist (HTTP ${chkRes.status})`);
      }
      const chkData = await chkRes.json();
      setChecklist(chkData.items || []);

      // 2. Fetch all submissions
      const subRes = await fetch(`/api/student/documents`, { cache: 'no-store' });
      if (subRes.ok) {
        const subData = await subRes.json();
        setSubmissions(subData.items || []);
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Error loading stage document checklist');
    } finally {
      setLoading(false);
    }
  }

  function openUploadModal(item?: ChecklistItem) {
    setModalError(null);
    setModalRefId(null);
    setFieldErrors({});
    if (item) {
      setTargetReqId(item.requirement.id);
      setTargetDocTypeId(item.requirement.documentTypeId);
      setDocTitle(`DEMO-STM-06-US-003: ${item.requirement.documentTypeName}`);
    } else {
      setTargetReqId('');
      setTargetDocTypeId(checklist[0]?.requirement?.documentTypeId || '');
      setDocTitle('DEMO-STM-06-US-003: Student Document');
    }
    setChangeNote('Mandatory submission for ' + selectedStage);
    setFileName('student_record.pdf');
    setMimeType('application/pdf');
    setFileSizeBytes(1048576);
    setIsUploadModalOpen(true);
  }

  function validateForm(): boolean {
    const errors: Record<string, string> = {};
    if (!docTitle.trim()) {
      errors.docTitle = 'Document title is required';
    } else if (docTitle.trim().length < 2 || docTitle.trim().length > 200) {
      errors.docTitle = 'Title must be between 2 and 200 characters';
    }

    if (!targetDocTypeId) {
      errors.targetDocTypeId = 'Document type is required';
    }

    if (!['application/pdf', 'image/jpeg', 'image/png', 'image/webp'].includes(mimeType)) {
      errors.mimeType = 'Unsupported format. Allowed: PDF, JPEG, PNG, WEBP';
    }

    if (fileSizeBytes > 10 * 1024 * 1024) {
      errors.fileSizeBytes = 'File size exceeds maximum institutional limit of 10 MB';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleUploadSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validateForm()) return;

    setSubmitting(true);
    setModalError(null);
    setModalRefId(null);

    try {
      const payload = {
        documentTypeId: targetDocTypeId,
        stageRequirementId: targetReqId || undefined,
        title: docTitle.trim(),
        mimeType,
        sizeBytes: fileSizeBytes,
        fileName,
        changeNote,
      };

      const res = await fetch('/api/student/documents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const traceId = res.headers.get('x-trace-id') || res.headers.get('x-correlation-id');
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        setModalRefId(data.traceId || traceId);
        throw new Error(data.message || data.error || `Upload rejected (HTTP ${res.status})`);
      }

      setSuccessMsg(`Document "${data.title || docTitle}" successfully submitted!`);
      setIsUploadModalOpen(false);
      await fetchChecklistAndSubmissions();
    } catch (err: any) {
      setModalError(err.message || 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  }

  // Summary counts
  const totalReqs = checklist.length;
  const mandatoryReqs = checklist.filter((i) => i.requirement.isMandatory).length;
  const submittedCount = checklist.filter((i) => i.status === 'SUBMITTED' || i.status === 'VERIFIED').length;
  const pendingCount = checklist.filter((i) => i.status === 'PENDING').length;

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Student Documents Desk</h1>
          <p className="text-sm text-gray-500">
            Verify stage requirements, upload mandatory certifications, and track verification status.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={fetchChecklistAndSubmissions}
            disabled={loading}
            className="flex items-center gap-1.5"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button
            id="btn-upload-document"
            variant="primary"
            size="sm"
            onClick={() => openUploadModal()}
            className="flex items-center gap-1.5 bg-[#1F6FEB] text-white hover:bg-[#1652B0]"
          >
            <Upload className="h-3.5 w-3.5" />
            Upload Document
          </Button>
        </div>
      </div>

      {/* Global Alerts */}
      {successMsg && (
        <div className="flex items-center justify-between rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-green-600" />
            <span>{successMsg}</span>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setSuccessMsg(null)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {errorMsg && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <div className="flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold">{errorMsg}</p>
              {errorRefId && (
                <p className="mt-1 font-mono text-xs text-red-700">
                  Reference ID: <span className="font-bold underline">{errorRefId}</span>
                </p>
              )}
            </div>
            <Button variant="ghost" size="sm" onClick={() => setErrorMsg(null)}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="flex border-b border-gray-200">
        <button
          id="tab-stage-requirements"
          data-testid="docs-tab-checklist"
          onClick={() => setActiveTab('stage')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'stage'
              ? 'border-[#1F6FEB] text-[#1F6FEB]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          Stage Checklist (STM-06)
        </button>
        <button
          id="tab-my-submissions"
          data-testid="docs-tab-submissions"
          onClick={() => setActiveTab('submissions')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'submissions'
              ? 'border-[#1F6FEB] text-[#1F6FEB]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          My Submissions ({submissions.length})
        </button>
      </div>

      {activeTab === 'stage' && (
        <>
          {/* Stage Selector & Summary Stats */}
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
            <Card className="md:col-span-2">
              <CardContent className="pt-5">
                <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider block mb-1.5">
                  Academic Stage
                </label>
                <select
                  id="select-student-stage"
                  value={selectedStage}
                  onChange={(e) => setSelectedStage(e.target.value)}
                  className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-800 shadow-sm focus:border-[#1F6FEB] focus:outline-none focus:ring-1 focus:ring-[#1F6FEB]"
                >
                  {STAGES.map((s) => (
                    <option key={s.value} value={s.value}>
                      {s.label} ({s.value})
                    </option>
                  ))}
                </select>
                <p className="mt-2 text-xs text-gray-400">
                  Institutional regulations mandate verification before proceeding past this stage.
                </p>
              </CardContent>
            </Card>

            <Card className="flex flex-col justify-center text-center p-4">
              <span className="text-xs text-gray-500 font-medium">Total Requirements</span>
              <span className="text-2xl font-bold text-gray-900 mt-1">{totalReqs}</span>
            </Card>
            <Card className="flex flex-col justify-center text-center p-4">
              <span className="text-xs text-blue-600 font-medium">Mandatory</span>
              <span className="text-2xl font-bold text-blue-700 mt-1">{mandatoryReqs}</span>
            </Card>
            <Card className="flex flex-col justify-center text-center p-4">
              <span className="text-xs text-green-600 font-medium">Submitted / Verified</span>
              <span className="text-2xl font-bold text-green-700 mt-1">{submittedCount}</span>
            </Card>
          </div>

          {/* Checklist Table */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-semibold text-gray-900">
                Stage Requirements: {selectedStage}
              </CardTitle>
              <CardDescription className="text-xs text-gray-500">
                Documents marked mandatory must be submitted and attested by institutional officers.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="flex justify-center items-center py-12">
                  <Loader2 className="h-8 w-8 animate-spin text-[#1F6FEB]" />
                  <span className="ml-2 text-sm text-gray-500">Checking requirements...</span>
                </div>
              ) : checklist.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  <ShieldCheck className="mx-auto h-10 w-10 text-gray-400 mb-2" />
                  <p className="font-medium text-sm">No stage requirements defined for {selectedStage}</p>
                  <p className="text-xs text-gray-400 mt-1">
                    Requirements configured by your college registrar will appear here.
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm" id="table-student-checklist">
                    <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold text-gray-600">
                      <tr>
                        <th className="px-4 py-3">Document Type</th>
                        <th className="px-4 py-3">Requirement Details</th>
                        <th className="px-4 py-3">Mandatory</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {checklist.map((item, idx) => {
                        const isDone = item.status === 'SUBMITTED' || item.status === 'VERIFIED';
                        return (
                          <tr key={item.requirement.id} className="hover:bg-gray-50/50" id={`row-checklist-${idx}`}>
                            <td className="px-4 py-3.5">
                              <div className="flex items-center gap-2.5">
                                <div className="rounded-lg bg-blue-50 p-2 text-blue-600">
                                  <FileText className="h-4 w-4" />
                                </div>
                                <div>
                                  <div className="font-medium text-gray-900">
                                    {item.requirement.documentTypeName || item.requirement.documentTypeCode}
                                  </div>
                                  <span className="font-mono text-[11px] text-gray-400">
                                    {item.requirement.documentTypeCode}
                                  </span>
                                </div>
                              </div>
                            </td>
                            <td className="px-4 py-3.5 text-xs text-gray-600">
                              <p>{item.requirement.description || 'Institutional verification proof'}</p>
                              <span className="text-[11px] text-gray-400">
                                Min: {item.requirement.minCount} · Max: {item.requirement.maxCount}
                                {item.requirement.submissionDeadlineDays
                                  ? ` · Due: ${item.requirement.submissionDeadlineDays} days`
                                  : ''}
                              </span>
                            </td>
                            <td className="px-4 py-3.5">
                              {item.requirement.isMandatory ? (
                                <Badge className="bg-red-50 text-red-700 border-red-200 text-[10px]">
                                  Mandatory
                                </Badge>
                              ) : (
                                <Badge className="bg-gray-100 text-gray-600 border-gray-200 text-[10px]">
                                  Optional
                                </Badge>
                              )}
                            </td>
                            <td className="px-4 py-3.5">
                              {item.status === 'SUBMITTED' && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 border border-blue-200" id={`badge-status-${idx}`}>
                                  <Clock className="h-3 w-3" /> Submitted
                                </span>
                              )}
                              {item.status === 'VERIFIED' && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700 border border-green-200" id={`badge-status-${idx}`}>
                                  <CheckCircle2 className="h-3 w-3" /> Verified
                                </span>
                              )}
                              {item.status === 'PENDING' && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 border border-amber-200" id={`badge-status-${idx}`}>
                                  <AlertCircle className="h-3 w-3" /> Pending
                                </span>
                              )}
                              {item.status === 'REJECTED' && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-700 border border-red-200" id={`badge-status-${idx}`}>
                                  <XCircle className="h-3 w-3" /> Action Required
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-3.5 text-right">
                              <Button
                                id={`btn-upload-row-${idx}`}
                                size="sm"
                                variant={isDone ? 'outline' : 'primary'}
                                onClick={() => openUploadModal(item)}
                                className="text-xs"
                              >
                                {isDone ? 'Re-upload Version' : 'Upload'}
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
        </>
      )}

      {activeTab === 'submissions' && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base font-semibold text-gray-900">
              My Submitted Documents
            </CardTitle>
            <CardDescription className="text-xs text-gray-500">
              Immutable version history and multi-table committed records from PostgreSQL.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {submissions.length === 0 ? (
              <div className="text-center py-12 text-gray-500">
                <FileCode className="mx-auto h-10 w-10 text-gray-400 mb-2" />
                <p className="font-medium text-sm">No documents submitted yet</p>
                <p className="text-xs text-gray-400 mt-1">Upload required documents from the stage checklist.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm" id="table-student-submissions">
                  <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold text-gray-600">
                    <tr>
                      <th className="px-4 py-3">Document Title</th>
                      <th className="px-4 py-3">Type</th>
                      <th className="px-4 py-3">Version</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Expiry & Retention</th>
                      <th className="px-4 py-3">Size & Format</th>
                      <th className="px-4 py-3">Submitted At</th>
                      <th className="px-4 py-3">Verification & Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {submissions.map((doc, idx) => (
                      <tr key={doc.id} className="hover:bg-gray-50/50" id={`row-submission-${idx}`}>
                        <td className="px-4 py-3 font-medium text-gray-900">
                          {doc.title}
                        </td>
                        <td className="px-4 py-3 font-mono text-xs text-gray-600">
                          {doc.documentTypeCode || 'GENERAL'}
                        </td>
                        <td className="px-4 py-3 text-xs">
                          <span className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-gray-700">
                            v{doc.currentVersionNo || 1}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          {((doc.status as string) === 'APPROVED' || doc.status === 'VERIFIED') ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 border border-emerald-200">
                              <CheckCircle2 className="h-3 w-3" /> Verified
                            </span>
                          ) : doc.status === 'REJECTED' ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-700 border border-red-200">
                              <XCircle className="h-3 w-3" /> Rejected
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 border border-blue-200">
                              <Clock className="h-3 w-3" /> {doc.status}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs">
                          {doc.expiresAt ? (
                            (() => {
                              const exp = new Date(doc.expiresAt);
                              const isExp = exp.getTime() < Date.now();
                              const isSoon = !isExp && exp.getTime() < (Date.now() + 30 * 24 * 60 * 60 * 1000);
                              if (isExp) {
                                return (
                                  <span data-testid={`docs-badge-expired-${idx}`} className="inline-flex items-center gap-1 rounded bg-red-100 border border-red-200 text-red-800 px-2 py-0.5 text-[11px] font-semibold">
                                    <AlertCircle className="h-3 w-3" /> EXPIRED
                                  </span>
                                );
                              }
                              if (isSoon) {
                                return (
                                  <span data-testid={`docs-badge-expiring-soon-${idx}`} className="inline-flex items-center gap-1 rounded bg-amber-100 border border-amber-200 text-amber-800 px-2 py-0.5 text-[11px] font-semibold">
                                    <Clock className="h-3 w-3" /> EXPIRING SOON
                                  </span>
                                );
                              }
                              return (
                                <span className="font-mono text-gray-600 text-[11px]">Exp: {exp.toLocaleDateString()}</span>
                              );
                            })()
                          ) : (
                            <span className="text-gray-400 text-[11px]">No expiry</span>
                          )}
                          <span className="text-[10px] text-gray-400 block mt-0.5 font-mono">
                            {doc.retentionPolicyCode || 'RET_PERMANENT'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500">
                          {formatBytes(doc.sizeBytes)} · {doc.mimeType}
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500">
                          {new Date(doc.createdAt).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3">
                          {doc.status === 'REJECTED' ? (
                            <div className="space-y-1.5" id={`actions-rejected-${idx}`}>
                              {doc.latestVerificationCode && (
                                <div className="flex items-start gap-1 text-[11px] text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1" id={`feedback-rejected-${idx}`}>
                                  <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5 text-red-500" />
                                  <div>
                                    <span className="font-semibold">Reason: </span>
                                    <span>{doc.latestVerificationCode}</span>
                                  </div>
                                </div>
                              )}
                              <div className="flex items-center gap-2">
                                <button
                                  id={`btn-resubmit-doc-${idx}`}
                                  onClick={() => handleOpenResubmitModal(doc)}
                                  className="inline-flex items-center gap-1 rounded bg-amber-500 hover:bg-amber-600 text-white px-2.5 py-1 text-xs font-semibold shadow-xs transition"
                                >
                                  <RotateCcw className="h-3 w-3" />
                                  Resubmit
                                </button>
                                <button
                                  data-testid={`docs-audit-btn-${idx}`}
                                  id={`btn-audit-doc-${idx}`}
                                  onClick={() => openAuditModal(doc)}
                                  className="inline-flex items-center gap-1 rounded border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 px-2.5 py-1 text-xs font-semibold shadow-xs transition"
                                >
                                  <ShieldCheck className="h-3 w-3" />
                                  Audit Trail
                                </button>
                              </div>
                            </div>
                          ) : (
                            <div className="space-y-1">
                              {((doc.status as string) === 'APPROVED' || doc.status === 'VERIFIED') ? (
                                <span className="text-xs text-emerald-700 font-medium flex items-center gap-1">
                                  <CheckCircle2 className="h-3 w-3" /> Verified & Approved
                                </span>
                              ) : (
                                <span className="text-xs text-gray-500 block">Awaiting Institutional Review</span>
                              )}
                              <button
                                data-testid={`docs-audit-btn-${idx}`}
                                id={`btn-audit-doc-${idx}`}
                                onClick={() => openAuditModal(doc)}
                                className="inline-flex items-center gap-1 rounded border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 px-2.5 py-1 text-xs font-semibold shadow-xs transition"
                              >
                                <ShieldCheck className="h-3 w-3" />
                                Audit Trail
                              </button>
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
      )}

      {/* Upload Modal */}
      {isUploadModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg rounded-xl bg-white shadow-xl border border-gray-200">
            <div className="flex items-center justify-between border-b border-gray-100 px-6 py-4">
              <h3 className="text-base font-semibold text-gray-900">
                Upload Required Document (STM-06)
              </h3>
              <button
                onClick={() => setIsUploadModalOpen(false)}
                className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleUploadSubmit} className="p-6 space-y-4">
              {modalError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3.5 text-xs text-red-800">
                  <div className="flex items-start gap-2">
                    <AlertCircle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold">{modalError}</p>
                      {modalRefId && (
                        <p className="mt-1 font-mono text-[11px] text-red-700">
                          Reference ID: <span className="font-bold underline">{modalRefId}</span>
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Document Type Selector */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  Document Type *
                </label>
                <select
                  id="modal-select-doc-type"
                  value={targetDocTypeId}
                  onChange={(e) => {
                    setTargetDocTypeId(e.target.value);
                    if (fieldErrors.targetDocTypeId) {
                      setFieldErrors((prev) => ({ ...prev, targetDocTypeId: '' }));
                    }
                  }}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                >
                  {checklist.map((item) => (
                    <option key={item.requirement.documentTypeId} value={item.requirement.documentTypeId}>
                      {item.requirement.documentTypeName} ({item.requirement.documentTypeCode})
                    </option>
                  ))}
                </select>
                {fieldErrors.targetDocTypeId && (
                  <p className="mt-1 text-xs text-red-600 font-medium">{fieldErrors.targetDocTypeId}</p>
                )}
              </div>

              {/* Title Input */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  Document Title *
                </label>
                <input
                  id="modal-input-title"
                  type="text"
                  value={docTitle}
                  onChange={(e) => {
                    setDocTitle(e.target.value);
                    if (fieldErrors.docTitle) {
                      setFieldErrors((prev) => ({ ...prev, docTitle: '' }));
                    }
                  }}
                  placeholder="e.g. 12th Grade Marksheet - Original"
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                />
                {fieldErrors.docTitle && (
                  <p className="mt-1 text-xs text-red-600 font-medium" id="err-field-title">
                    {fieldErrors.docTitle}
                  </p>
                )}
              </div>

              {/* MIME Type & Simulated Size */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-gray-700 block mb-1">
                    File Format
                  </label>
                  <select
                    id="modal-select-mime"
                    value={mimeType}
                    onChange={(e) => setMimeType(e.target.value)}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                  >
                    <option value="application/pdf">PDF Document (.pdf)</option>
                    <option value="image/png">PNG Image (.png)</option>
                    <option value="image/jpeg">JPEG Image (.jpg)</option>
                    <option value="image/webp">WEBP Image (.webp)</option>
                    <option value="application/x-dosexec">Binary Exe (Invalid)</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-700 block mb-1">
                    File Size
                  </label>
                  <select
                    id="modal-select-size"
                    value={fileSizeBytes}
                    onChange={(e) => setFileSizeBytes(Number(e.target.value))}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                  >
                    <option value={512000}>500 KB (Normal)</option>
                    <option value={2097152}>2 MB (Standard)</option>
                    <option value={8388608}>8 MB (Large)</option>
                    <option value={15728640}>15 MB (Over Limit &gt; 10MB)</option>
                  </select>
                  {fieldErrors.fileSizeBytes && (
                    <p className="mt-1 text-xs text-red-600 font-medium" id="err-field-size">
                      {fieldErrors.fileSizeBytes}
                    </p>
                  )}
                </div>
              </div>

              {/* File Name */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  File Name
                </label>
                <input
                  id="modal-input-filename"
                  type="text"
                  value={fileName}
                  onChange={(e) => setFileName(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none font-mono text-xs"
                />
              </div>

              {/* Notes */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  Change Note / Submission Details
                </label>
                <textarea
                  id="modal-input-notes"
                  rows={2}
                  value={changeNote}
                  onChange={(e) => setChangeNote(e.target.value)}
                  placeholder="Optional notes for institutional verifier..."
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsUploadModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  id="modal-btn-submit"
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={submitting}
                  className="bg-[#1F6FEB] text-white hover:bg-[#1652B0]"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                      Uploading...
                    </>
                  ) : (
                    'Confirm & Upload'
                  )}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Resubmit Modal (STM-06-US-005) */}
      {isResubmitModalOpen && selectedDocForResubmit && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" id="modal-resubmit-overlay">
          <div className="w-full max-w-lg rounded-xl bg-white shadow-xl border border-gray-200">
            <div className="flex items-center justify-between border-b border-gray-100 px-6 py-4">
              <div>
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                  <RotateCcw className="h-4 w-4 text-amber-600" />
                  Resubmit Corrected Document (STM-06-US-005)
                </h3>
                <p className="text-xs text-gray-500 mt-0.5">
                  Uploading an updated file increments version to v{(selectedDocForResubmit.currentVersionNo || 1) + 1} and resets status to Submitted.
                </p>
              </div>
              <button
                id="btn-close-resubmit-modal"
                onClick={() => setIsResubmitModalOpen(false)}
                className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitResubmission} className="p-6 space-y-4">
              {resubmitError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700" id="resubmit-error-banner">
                  <div className="flex items-center gap-1.5 font-semibold">
                    <AlertCircle className="h-4 w-4" />
                    <span>Resubmission Failed</span>
                  </div>
                  <p className="mt-1">{resubmitError}</p>
                  {resubmitRefId && (
                    <p className="mt-1 font-mono text-[10px] text-red-500">Ref: {resubmitRefId}</p>
                  )}
                </div>
              )}

              {/* Document Overview */}
              <div className="rounded-lg bg-gray-50 border border-gray-200 p-3 space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-gray-500">Target Document:</span>
                  <span className="font-semibold text-gray-900" id="resubmit-target-title">{selectedDocForResubmit.title}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Document Type:</span>
                  <span className="font-mono text-gray-700">{selectedDocForResubmit.documentTypeCode || 'GENERAL'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Version Progression:</span>
                  <span className="font-semibold text-blue-700" id="resubmit-version-progression">
                    v{selectedDocForResubmit.currentVersionNo || 1} &rarr; v{(selectedDocForResubmit.currentVersionNo || 1) + 1}
                  </span>
                </div>
              </div>

              {/* Rejection Notice Banner */}
              <div className="rounded-lg border border-red-200 bg-red-50/70 p-3 text-xs" id="resubmit-rejection-banner">
                <div className="flex items-center gap-1.5 font-semibold text-red-800">
                  <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
                  <span>Officer Rejection Feedback</span>
                </div>
                <p className="mt-1 text-red-700" id="resubmit-rejection-feedback">
                  {selectedDocForResubmit.latestVerificationCode || "Please upload a corrected version meeting all institutional requirements."}
                </p>
              </div>

              {/* Corrected File Name */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  Updated File (PDF or Image)
                </label>
                <input
                  id="resubmit-file-name"
                  type="text"
                  required
                  value={resubmitFileName}
                  onChange={(e) => setResubmitFileName(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none font-mono text-xs"
                />
              </div>

              {/* File Format / Simulated Select */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-gray-700 block mb-1">
                    Format
                  </label>
                  <select
                    id="resubmit-mime-type"
                    value={resubmitMimeType}
                    onChange={(e) => setResubmitMimeType(e.target.value)}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-xs text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                  >
                    <option value="application/pdf">PDF (application/pdf)</option>
                    <option value="image/jpeg">JPEG (image/jpeg)</option>
                    <option value="image/png">PNG (image/png)</option>
                    <option value="image/webp">WebP (image/webp)</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-700 block mb-1">
                    File Size
                  </label>
                  <select
                    id="resubmit-file-size"
                    value={resubmitSizeBytes}
                    onChange={(e) => setResubmitSizeBytes(Number(e.target.value))}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-xs text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                  >
                    <option value={1048576}>1.0 MB</option>
                    <option value={2097152}>2.0 MB</option>
                    <option value={4194304}>4.0 MB</option>
                  </select>
                </div>
              </div>

              {/* Mandatory Change Note */}
              <div>
                <label className="text-xs font-semibold text-gray-700 block mb-1">
                  Correction Note / Changes Made <span className="text-red-500">*</span>
                </label>
                <textarea
                  id="resubmit-change-note"
                  required
                  rows={2}
                  value={resubmitChangeNote}
                  onChange={(e) => setResubmitChangeNote(e.target.value)}
                  placeholder="Explain what was corrected (e.g. Uploaded high-resolution scan with visible seal and signature)..."
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-800 focus:border-[#1F6FEB] focus:outline-none"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
                <Button
                  id="btn-cancel-resubmit"
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsResubmitModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  id="btn-confirm-resubmit"
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={isResubmitting}
                  className="bg-amber-600 hover:bg-amber-700 text-white"
                >
                  {isResubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                      Resubmitting...
                    </>
                  ) : (
                    'Confirm & Resubmit v' + ((selectedDocForResubmit.currentVersionNo || 1) + 1)
                  )}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* STM-06-US-006: Regulatory Audit Trail & Access History Modal */}
      {isAuditModalOpen && selectedAuditDoc && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" data-testid="docs-audit-modal">
          <div className="w-full max-w-2xl rounded-xl bg-white shadow-xl overflow-hidden">
            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
              <div>
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                  <ShieldCheck className="h-5 w-5 text-indigo-600" />
                  Regulatory Access Audit Trail
                </h3>
                <p className="text-xs text-gray-500 mt-0.5">
                  Document: {selectedAuditDoc.title} ({selectedAuditDoc.documentTypeCode || 'GENERAL'})
                </p>
              </div>
              <button
                data-testid="docs-audit-modal-close"
                onClick={() => setIsAuditModalOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-3 p-3 bg-gray-50 rounded-lg text-xs">
                <div>
                  <span className="text-gray-500">Retention Policy:</span>
                  <p className="font-semibold text-gray-800" data-testid="docs-audit-retention-code">
                    {selectedAuditDoc.retentionPolicyCode || 'RET_PERMANENT'}
                  </p>
                </div>
                <div>
                  <span className="text-gray-500">Expiry Date:</span>
                  <p className="font-semibold text-gray-800" data-testid="docs-audit-expiry-date">
                    {selectedAuditDoc.expiresAt ? new Date(selectedAuditDoc.expiresAt).toLocaleDateString() : 'No expiry set'}
                  </p>
                </div>
              </div>

              <div className="flex justify-between items-center">
                <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">
                  Access History (doc.document_deliveries)
                </h4>
                <Button
                  data-testid="docs-download-log-btn"
                  size="sm"
                  onClick={handleDownloadAndLogAccess}
                  disabled={auditDownloading}
                  className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs"
                >
                  {auditDownloading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
                  ) : (
                    <Upload className="h-3.5 w-3.5 mr-1 rotate-180" />
                  )}
                  Download &amp; Record Access
                </Button>
              </div>

              {auditLoading ? (
                <div className="py-8 text-center text-gray-400">
                  <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2 text-indigo-500" />
                  Loading audit trail...
                </div>
              ) : auditLogs.length === 0 ? (
                <div className="py-8 text-center text-gray-400 text-xs border border-dashed rounded-lg" data-testid="docs-audit-empty">
                  No access logs recorded for this document yet. Click &quot;Download &amp; Record Access&quot; to log the first regulatory event.
                </div>
              ) : (
                <div className="max-h-60 overflow-y-auto border border-gray-200 rounded-lg" data-testid="docs-audit-table">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-gray-50 border-b border-gray-200 text-gray-600 font-semibold">
                      <tr>
                        <th className="px-3 py-2">Channel</th>
                        <th className="px-3 py-2">Status</th>
                        <th className="px-3 py-2">Client IP</th>
                        <th className="px-3 py-2">Delivered At</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {auditLogs.map((log, lIdx) => (
                        <tr key={log.id || lIdx} className="hover:bg-gray-50" data-testid={`docs-audit-row-${lIdx}`}>
                          <td className="px-3 py-2 font-mono font-medium text-indigo-700">{log.channel}</td>
                          <td className="px-3 py-2 font-mono text-emerald-700">{log.status}</td>
                          <td className="px-3 py-2 font-mono text-gray-600">{log.clientIp || '127.0.0.1'}</td>
                          <td className="px-3 py-2 text-gray-500">{new Date(log.deliveredAt || log.createdAt).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="bg-gray-50 border-t border-gray-200 px-6 py-3 flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsAuditModalOpen(false)}
                className="text-xs"
              >
                Close
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
