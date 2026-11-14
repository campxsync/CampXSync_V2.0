'use client';

import { useState, useEffect } from 'react';
import { RefreshCw } from 'lucide-react';

interface DocumentTypeItem {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  category: string;
  module: string;
  defaultClassification: string;
  requiresSignature: boolean;
  retentionPolicyCode: string;
  isActive: boolean;
  createdAt: string;
  rowVersion: number;
}

interface StageRequirementItem {
  id: string;
  tenantId: string;
  programId: string | null;
  programCode: string | null;
  programName: string | null;
  academicStage: string;
  documentTypeId: string;
  documentTypeCode: string;
  documentTypeName: string;
  isMandatory: boolean;
  minCount: number;
  maxCount: number | null;
  submissionDeadlineDays: number | null;
  description: string | null;
  isActive: boolean;
  createdAt: string;
  rowVersion: number;
}

interface VerificationQueueItem {
  documentId: string;
  tenantId: string;
  collegeId: string | null;
  studentId: string;
  studentName: string;
  documentTypeId: string;
  documentTypeCode: string;
  documentTypeName: string;
  title: string;
  classification: string;
  status: string;
  mimeType: string;
  sizeBytes: number;
  checksumSha256: string;
  storagePath: string;
  currentVersionNo: number;
  submittedAt: string | null;
  latestVerificationResult: string | null;
  latestVerificationCode: string | null;
  latestVerifiedAt: string | null;
}

interface ProgramItem {
  id: string;
  code: string;
  name: string;
}

const ACADEMIC_STAGES = [
  { value: 'ADMISSION', label: 'Admission' },
  { value: 'ENROLLMENT', label: 'Course Enrollment' },
  { value: 'ANNUAL_PROGRESSION', label: 'Annual Progression' },
  { value: 'EXAMINATION', label: 'Examination & Hall Ticket' },
  { value: 'GRADUATION', label: 'Graduation & Convocation' },
];

/**
 * College Admin Document Governance & Stage Requirements Desk (STM-06).
 * US-001: Configure document types.
 * US-002: Define mandatory and optional documents for programmes and stages.
 * US-004: Institutional officer verifies and approves/rejects submitted student documents.
 */
export default function CollegeDocumentsPage() {
  const [activeTab, setActiveTab] = useState<'verifications' | 'requirements' | 'catalog' | 'access'>('verifications');
  const [accessDocId, setAccessDocId] = useState('');
  const [accessGranteeId, setAccessGranteeId] = useState('');
  const [accessGranteeType, setAccessGranteeType] = useState('STAFF');
  const [accessPurpose, setAccessPurpose] = useState('VERIFICATION');
  const [accessGrants, setAccessGrants] = useState<any[]>([]);
  const [accessBusy, setAccessBusy] = useState(false);

  // Verification Queue State (US-004)
  const [queueItems, setQueueItems] = useState<VerificationQueueItem[]>([]);
  const [queueFilter, setQueueFilter] = useState<'ALL' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'>('ALL');
  const [selectedQueueItem, setSelectedQueueItem] = useState<VerificationQueueItem | null>(null);
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [verificationCode, setVerificationCode] = useState('');
  const [verificationNotes, setVerificationNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [deciding, setDeciding] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const [modalRefId, setModalRefId] = useState<string | null>(null);

  // Document Type Form State (US-001)
  const [code, setCode] = useState('DOC-TRANSCRIPT');
  const [name, setName] = useState('Official Academic Transcript');
  const [category, setCategory] = useState('ACADEMIC');
  const [classification, setClassification] = useState('CONFIDENTIAL');
  const [requiresSignature, setRequiresSignature] = useState(true);
  const [retentionPolicyCode, setRetentionPolicyCode] = useState('RET_PERMANENT');
  const [isActive, setIsActive] = useState(true);

  // Stage Requirement Form State (US-002)
  const [reqStage, setReqStage] = useState('ADMISSION');
  const [reqDocTypeId, setReqDocTypeId] = useState('');
  const [reqProgramId, setReqProgramId] = useState('');
  const [reqIsMandatory, setReqIsMandatory] = useState(true);
  const [reqMinCount, setReqMinCount] = useState(1);
  const [reqMaxCount, setReqMaxCount] = useState(1);
  const [reqDeadlineDays, setReqDeadlineDays] = useState(15);
  const [reqDescription, setReqDescription] = useState('Required for admission eligibility verification');
  const [stageFilter, setStageFilter] = useState('');

  // Data State
  const [documentTypes, setDocumentTypes] = useState<DocumentTypeItem[]>([]);
  const [requirements, setRequirements] = useState<StageRequirementItem[]>([]);
  const [programs, setPrograms] = useState<ProgramItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    loadVerificationQueue();
    loadDocumentTypes();
    loadRequirements();
    loadPrograms();
  }, []);

  async function loadVerificationQueue(filter = queueFilter) {
    setLoading(true);
    setErrorMessage('');
    try {
      const url = filter && filter !== 'ALL'
        ? `/api/college/documents/verifications?status=${filter}`
        : '/api/college/documents/verifications';
      const res = await fetch(url, { method: 'GET', cache: 'no-store' });
      const data = await res.json();
      if (!res.ok) {
        setErrorMessage(`Error (${res.status}): ${data.title || data.message || 'Failed to load verification queue'}`);
        return;
      }
      setQueueItems(data.items || []);
    } catch (err: any) {
      setErrorMessage(err.message || 'Network error fetching verification queue');
    } finally {
      setLoading(false);
    }
  }

  async function handleVerificationDecision(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedQueueItem) return;
    setModalError(null);
    setModalRefId(null);

    if (decision === 'REJECT' && (!rejectionReason || rejectionReason.trim().length < 3)) {
      setModalError('Rejection reason (minimum 3 characters) is mandatory when rejecting a document');
      return;
    }

    setDeciding(true);
    try {
      const payload = {
        documentId: selectedQueueItem.documentId,
        decision,
        verificationCode: verificationCode.trim() || undefined,
        verificationNotes: verificationNotes.trim() || undefined,
        rejectionReason: rejectionReason.trim() || undefined,
      };

      const res = await fetch('/api/college/documents/verifications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      const traceId = res.headers.get('x-trace-id') || data.traceId || data.correlationId;

      if (!res.ok) {
        setModalError(`Error (${res.status}): ${data.message || data.title || 'Decision failed'}`);
        if (traceId) setModalRefId(traceId);
        return;
      }

      setStatusMessage(`Document '${selectedQueueItem.title}' decision recorded: ${decision === 'APPROVE' ? 'APPROVED' : 'REJECTED'}`);
      setIsReviewModalOpen(false);
      setSelectedQueueItem(null);
      setRejectionReason('');
      setVerificationNotes('');
      loadVerificationQueue(queueFilter);
    } catch (err: any) {
      setModalError(err.message || 'Network error processing decision');
    } finally {
      setDeciding(false);
    }
  }

  async function loadDocumentTypes() {
    setLoading(true);
    setErrorMessage('');
    try {
      const res = await fetch('/api/college/documents/types', {
        method: 'GET',
        cache: 'no-store',
      });
      const data = await res.json();
      if (!res.ok) {
        setErrorMessage(data.title || data.message || 'Failed to load document types');
        return;
      }
      const items = data.items || [];
      setDocumentTypes(items);
      if (items.length > 0 && !reqDocTypeId) {
        setReqDocTypeId(items[0].id);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Network error fetching document types');
    } finally {
      setLoading(false);
    }
  }

  async function loadRequirements(stage?: string) {
    setLoading(true);
    setErrorMessage('');
    try {
      const url = stage
        ? `/api/college/documents/requirements?stage=${encodeURIComponent(stage)}`
        : '/api/college/documents/requirements';
      const res = await fetch(url, {
        method: 'GET',
        cache: 'no-store',
      });
      const data = await res.json();
      if (!res.ok) {
        setErrorMessage(data.title || data.message || 'Failed to load requirements');
        return;
      }
      setRequirements(data.items || []);
    } catch (err: any) {
      setErrorMessage(err.message || 'Network error fetching requirements');
    } finally {
      setLoading(false);
    }
  }

  async function loadPrograms() {
    try {
      const res = await fetch('/api/college/programs', { method: 'GET', cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setPrograms(data.programs || data.items || []);
      }
    } catch {}
  }

  async function handleConfigureDocumentType(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setStatusMessage('');
    setErrorMessage('');
    try {
      const payload = {
        code,
        name,
        category,
        module: 'STUDENT',
        defaultClassification: classification,
        requiresSignature,
        retentionPolicyCode,
        isActive,
      };

      const res = await fetch('/api/college/documents/types', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok) {
        setErrorMessage(`Error (${res.status}): ${data.title || data.message || 'Validation error'}`);
        return;
      }

      setStatusMessage(`Successfully configured document type '${data.code}' with ID: ${data.id}`);
      setDocumentTypes(prev => [data, ...prev.filter(d => d.id !== data.id)]);
      setCode('');
      setName('');
      loadDocumentTypes();
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to submit document type configuration');
    } finally {
      setLoading(false);
    }
  }

  async function handleDefineStageRequirement(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setStatusMessage('');
    setErrorMessage('');
    try {
      const payload = {
        academicStage: reqStage,
        documentTypeId: reqDocTypeId,
        programId: reqProgramId ? reqProgramId : null,
        isMandatory: reqIsMandatory,
        minCount: Number(reqMinCount),
        maxCount: reqMaxCount ? Number(reqMaxCount) : null,
        submissionDeadlineDays: reqDeadlineDays ? Number(reqDeadlineDays) : null,
        description: reqDescription,
        isActive: true,
      };

      const res = await fetch('/api/college/documents/requirements', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok) {
        setErrorMessage(`Error (${res.status}): ${data.title || data.message || 'Validation error'}`);
        return;
      }

      setStatusMessage(`Successfully defined ${data.isMandatory ? 'MANDATORY' : 'OPTIONAL'} document requirement for stage '${data.academicStage}' (${data.documentTypeCode})`);
      setRequirements(prev => [data, ...prev.filter(r => r.id !== data.id)]);
      loadRequirements(stageFilter);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to define document requirement');
    } finally {
      setLoading(false);
    }
  }

  const filteredRequirements = stageFilter
    ? requirements.filter(r => r.academicStage === stageFilter)
    : requirements;

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-8 font-sans">
      {/* Top Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between border-b pb-6 gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 data-testid="college-documents-heading" className="text-3xl font-bold tracking-tight text-gray-900">Student Document Governance</h1>
          </div>
          <p className="text-gray-500 mt-2 text-sm">
            Institutional document catalog, stage-wise mandatory requirements, and compliance rules.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>
            Gateway & PostgreSQL Connected
          </span>
          <button
            id="btn-refresh-catalog"
            onClick={() => {
              loadDocumentTypes();
              loadRequirements(stageFilter);
            }}
            disabled={loading}
            className="px-4 py-2 text-sm font-medium bg-white border border-gray-300 rounded-lg hover:bg-gray-50 text-gray-700 transition"
          >
            {loading ? 'Refreshing...' : 'Refresh All'}
          </button>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex border-b border-gray-200 space-x-6">
        <button
          id="tab-verification-queue"
          onClick={() => {
            setActiveTab('verifications');
            loadVerificationQueue();
          }}
          className={`pb-4 px-2 text-sm font-semibold transition border-b-2 flex items-center gap-2 ${
            activeTab === 'verifications'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <span>Verification Queue</span>
          <span className="px-2 py-0.5 text-xs rounded-full bg-blue-100 text-blue-700 font-mono">
            {queueItems.length}
          </span>
        </button>
        <button
          id="tab-stage-requirements"
          data-testid="college-documents-tab-requirements"
          onClick={() => setActiveTab('requirements')}
          className={`pb-4 px-2 text-sm font-semibold transition border-b-2 flex items-center gap-2 ${
            activeTab === 'requirements'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <span>Stage & Programme Requirements</span>
          <span className="px-2 py-0.5 text-xs rounded-full bg-blue-100 text-blue-700 font-mono">
            {requirements.length}
          </span>
        </button>
        <button
          id="tab-document-catalog"
          data-testid="college-documents-tab-catalog"
          onClick={() => setActiveTab('catalog')}
          className={`pb-4 px-2 text-sm font-semibold transition border-b-2 flex items-center gap-2 ${
            activeTab === 'catalog'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <span>Document Types Catalog</span>
          <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-700 font-mono">
            {documentTypes.length}
          </span>
        </button>
        <button
          data-testid="college-documents-tab-access"
          onClick={() => setActiveTab('access')}
          className={`pb-4 px-2 text-sm font-semibold transition border-b-2 flex items-center gap-2 ${
            activeTab === 'access'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <span>Access Permissions</span>
        </button>
      </div>

      {/* Alerts */}
      {statusMessage && (
        <div id="status-success-banner" className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-sm flex items-start gap-3">
          <span className="font-bold text-emerald-900">Success:</span>
          <span>{statusMessage}</span>
        </div>
      )}
      {errorMessage && (
        <div id="status-error-banner" className="p-4 bg-red-50 border border-red-200 text-red-800 rounded-lg text-sm flex items-start gap-3">
          <span className="font-bold text-red-900">Alert:</span>
          <span>{errorMessage}</span>
        </div>
      )}

      {/* STM-06-US-012 Access Permissions */}
      {activeTab === 'access' && (
        <section data-testid="college-documents-access-section" className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-gray-900">Grant / revoke document access</h2>
          <p className="text-sm text-gray-500">Registrar grants staff or external agency access for a stated purpose (persisted via document links).</p>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="text-xs font-medium text-gray-700">
              Document ID
              <input
                data-testid="college-documents-access-doc-input"
                className="mt-1 w-full rounded border px-2 py-1.5 text-sm"
                value={accessDocId}
                onChange={(e) => setAccessDocId(e.target.value)}
              />
            </label>
            <label className="text-xs font-medium text-gray-700">
              Grantee ID
              <input
                data-testid="college-documents-access-grantee-input"
                className="mt-1 w-full rounded border px-2 py-1.5 text-sm"
                value={accessGranteeId}
                onChange={(e) => setAccessGranteeId(e.target.value)}
              />
            </label>
            <label className="text-xs font-medium text-gray-700">
              Grantee type
              <select
                data-testid="college-documents-access-grantee-type"
                className="mt-1 w-full rounded border px-2 py-1.5 text-sm"
                value={accessGranteeType}
                onChange={(e) => setAccessGranteeType(e.target.value)}
              >
                <option value="STAFF">STAFF</option>
                <option value="EXTERNAL_AGENCY">EXTERNAL_AGENCY</option>
              </select>
            </label>
            <label className="text-xs font-medium text-gray-700">
              Purpose
              <input
                data-testid="college-documents-access-purpose-input"
                className="mt-1 w-full rounded border px-2 py-1.5 text-sm"
                value={accessPurpose}
                onChange={(e) => setAccessPurpose(e.target.value)}
              />
            </label>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              data-testid="college-documents-access-grant-btn"
              disabled={accessBusy}
              className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              onClick={async () => {
                setAccessBusy(true);
                setErrorMessage('');
                try {
                  const res = await fetch('/api/college/documents/access-grants', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                      documentId: accessDocId,
                      granteeId: accessGranteeId,
                      granteeType: accessGranteeType,
                      purpose: accessPurpose,
                    }),
                  });
                  const data = await res.json().catch(() => ({}));
                  if (!res.ok) {
                    setErrorMessage(data.message || data.title || `Grant failed (${res.status})`);
                    return;
                  }
                  setStatusMessage(`Access granted: ${data.relation || 'ACCESS_GRANTED'}`);
                  const listRes = await fetch(`/api/college/documents/access-grants?documentId=${encodeURIComponent(accessDocId)}`);
                  const listData = await listRes.json();
                  setAccessGrants(listData.items || []);
                } catch (err: any) {
                  setErrorMessage(err.message || 'Grant failed');
                } finally {
                  setAccessBusy(false);
                }
              }}
            >
              Grant access
            </button>
            <button
              data-testid="college-documents-access-refresh-btn"
              disabled={accessBusy}
              className="rounded border px-3 py-1.5 text-sm font-medium text-gray-700"
              onClick={async () => {
                setAccessBusy(true);
                try {
                  const listRes = await fetch(`/api/college/documents/access-grants?documentId=${encodeURIComponent(accessDocId)}`);
                  const listData = await listRes.json();
                  setAccessGrants(listData.items || []);
                } finally {
                  setAccessBusy(false);
                }
              }}
            >
              Refresh grants
            </button>
          </div>
          <ul data-testid="college-documents-access-list" className="divide-y text-xs text-slate-700">
            {accessGrants.map((g, idx) => (
              <li key={g.id || idx} data-testid={`college-documents-access-row-${idx}`} className="py-2">
                {g.relation} · {g.entityType} · {g.entityId}
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* TAB 0: Document Verification Queue (STM-06-US-004) */}
      {activeTab === 'verifications' && (
        <div className="space-y-6">
          {/* Filter Toolbar */}
          <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Filter Status:</span>
              {(['ALL', 'SUBMITTED', 'APPROVED', 'REJECTED'] as const).map((st) => (
                <button
                  key={st}
                  id={`btn-filter-${st.toLowerCase()}`}
                  onClick={() => {
                    setQueueFilter(st);
                    loadVerificationQueue(st);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                    queueFilter === st
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>

            <button
              id="btn-refresh-queue"
              onClick={() => loadVerificationQueue(queueFilter)}
              disabled={loading}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-50 flex items-center gap-1.5"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
              Refresh Queue
            </button>
          </div>

          {/* Verification Table */}
          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
            <div className="px-6 py-4 border-b flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">Document Verification Queue</h2>
                <p className="text-xs text-gray-500 mt-0.5">
                  Review student submissions, inspect cryptographic version progression, and record authoritative institutional decisions.
                </p>
              </div>
              <span className="text-xs text-gray-500 font-mono">{queueItems.length} records pending/verified</span>
            </div>

            <div className="overflow-x-auto">
              <table id="table-verification-queue" className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="bg-gray-50 text-gray-600 text-xs uppercase tracking-wider border-b">
                    <th className="py-3 px-4 font-semibold">Student / ID</th>
                    <th className="py-3 px-4 font-semibold">Document Title</th>
                    <th className="py-3 px-4 font-semibold">Type Code</th>
                    <th className="py-3 px-4 font-semibold">Version</th>
                    <th className="py-3 px-4 font-semibold">Status</th>
                    <th className="py-3 px-4 font-semibold">Format & Size</th>
                    <th className="py-3 px-4 font-semibold">Submitted</th>
                    <th className="py-3 px-4 font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {queueItems.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-gray-400 text-sm">
                        No submissions currently in queue for the selected filter.
                      </td>
                    </tr>
                  ) : (
                    queueItems.map((item, idx) => (
                      <tr key={item.documentId} id={`row-queue-${idx}`} className="hover:bg-gray-50/75 transition">
                        <td className="py-3 px-4">
                          <div className="font-semibold text-gray-900">{item.studentName || 'Student'}</div>
                          <div className="font-mono text-[11px] text-gray-500 truncate max-w-[140px]">{item.studentId}</div>
                        </td>
                        <td className="py-3 px-4 font-medium text-gray-900">
                          {item.title}
                          {item.latestVerificationCode && (
                            <div className="text-[11px] text-red-600 mt-0.5">
                              Rejection Code: <span className="font-mono">{item.latestVerificationCode}</span>
                            </div>
                          )}
                        </td>
                        <td className="py-3 px-4 font-mono text-xs text-blue-700 font-medium">
                          {item.documentTypeCode}
                        </td>
                        <td className="py-3 px-4">
                          <span className="px-2 py-0.5 text-xs font-mono font-semibold rounded bg-gray-100 text-gray-700 border">
                            v{item.currentVersionNo || 1}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          {item.status === 'APPROVED' ? (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-emerald-100 text-emerald-800 rounded-full">
                              APPROVED
                            </span>
                          ) : item.status === 'REJECTED' ? (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-rose-100 text-rose-800 rounded-full">
                              REJECTED
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-blue-100 text-blue-800 rounded-full">
                              {item.status || 'SUBMITTED'}
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-xs text-gray-500">
                          {item.mimeType} ({(item.sizeBytes / 1024).toFixed(0)} KB)
                        </td>
                        <td className="py-3 px-4 text-xs text-gray-500">
                          {item.submittedAt ? new Date(item.submittedAt).toLocaleDateString() : '—'}
                        </td>
                        <td className="py-3 px-4">
                          <button
                            id={`btn-review-${idx}`}
                            onClick={() => {
                              setSelectedQueueItem(item);
                              setDecision('APPROVE');
                              setVerificationCode('');
                              setVerificationNotes('');
                              setRejectionReason('');
                              setModalError(null);
                              setIsReviewModalOpen(true);
                            }}
                            className="px-3 py-1 bg-white hover:bg-gray-50 border border-gray-300 rounded text-xs font-medium text-gray-700 shadow-2xs transition"
                          >
                            Review / Verify
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Review & Decision Modal */}
      {isReviewModalOpen && selectedQueueItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" id="modal-review-doc">
          <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl border border-gray-200">
            <h3 className="text-lg font-bold text-gray-900">Institutional Document Verification</h3>
            <p className="text-xs text-gray-500 mt-1">
              Authoritatively certify student submission compliance or reject with detailed remediation reason.
            </p>

            {modalError && (
              <div className="mt-3 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs" id="modal-error-banner">
                <p className="font-semibold">{modalError}</p>
                {modalRefId && <p className="font-mono text-[10px] text-red-500 mt-0.5">Ref: {modalRefId}</p>}
              </div>
            )}

            <div className="mt-4 p-3 bg-gray-50 rounded-lg border border-gray-200 space-y-1 text-xs">
              <div className="flex justify-between">
                <span className="text-gray-500">Document:</span>
                <span className="font-medium text-gray-900">{selectedQueueItem.title}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Student ID:</span>
                <span className="font-mono text-gray-800">{selectedQueueItem.studentId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Version:</span>
                <span className="font-mono font-bold text-blue-700">v{selectedQueueItem.currentVersionNo}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Type / Classification:</span>
                <span className="font-mono text-gray-700">{selectedQueueItem.documentTypeCode} ({selectedQueueItem.classification})</span>
              </div>
            </div>

            <form onSubmit={handleVerificationDecision} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                  Verification Decision *
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    id="btn-select-approve"
                    onClick={() => setDecision('APPROVE')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold border transition ${
                      decision === 'APPROVE'
                        ? 'bg-emerald-600 text-white border-emerald-600 shadow-xs'
                        : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    ✓ APPROVE DOCUMENT
                  </button>
                  <button
                    type="button"
                    id="btn-select-reject"
                    onClick={() => setDecision('REJECT')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold border transition ${
                      decision === 'REJECT'
                        ? 'bg-rose-600 text-white border-rose-600 shadow-xs'
                        : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    ✕ REJECT SUBMISSION
                  </button>
                </div>
              </div>

              {decision === 'REJECT' && (
                <div>
                  <label className="block text-xs font-semibold text-rose-700 uppercase tracking-wider mb-1">
                    Rejection Remediation Reason *
                  </label>
                  <input
                    id="input-rejection-reason"
                    type="text"
                    value={rejectionReason}
                    onChange={(e) => setRejectionReason(e.target.value)}
                    placeholder="e.g. REJ-SEAL-BLURRY: High-resolution 300 DPI scan with embossed seal required"
                    required
                    className="w-full px-3 py-2 border border-rose-300 rounded-lg text-xs focus:ring-2 focus:ring-rose-500"
                  />
                  <span className="text-[11px] text-gray-400 mt-1 block">
                    This reason is immediately visible to the student to guide correction.
                  </span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                  Institutional Verification Code (Optional)
                </label>
                <input
                  id="input-verification-code"
                  type="text"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value)}
                  placeholder="e.g. VER-OFFICIAL-STAMP-OK"
                  className="w-full px-3 py-2 border rounded-lg text-xs font-mono focus:ring-2 focus:ring-blue-500 uppercase"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                  Verification Audit Notes (Optional)
                </label>
                <textarea
                  id="input-verification-notes"
                  value={verificationNotes}
                  onChange={(e) => setVerificationNotes(e.target.value)}
                  rows={2}
                  placeholder="Internal audit log notes..."
                  className="w-full px-3 py-2 border rounded-lg text-xs focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="pt-3 border-t flex justify-end gap-3">
                <button
                  type="button"
                  id="btn-cancel-review"
                  onClick={() => setIsReviewModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  id="btn-confirm-review"
                  disabled={deciding}
                  className={`px-5 py-2 rounded-lg text-xs font-bold text-white shadow-xs transition disabled:opacity-50 ${
                    decision === 'APPROVE' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                  }`}
                >
                  {deciding ? 'Recording Decision...' : decision === 'APPROVE' ? 'Confirm Approval' : 'Confirm Rejection'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* TAB 1: Stage & Programme Requirements (STM-06-US-002) */}
      {activeTab === 'requirements' && (
        <div className="space-y-8">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Form Panel */}
            <div className="lg:col-span-2 bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-gray-900 mb-4 border-b pb-3">
                Define Mandatory / Optional Document Requirement
              </h2>
              <form onSubmit={handleDefineStageRequirement} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Academic Stage *
                    </label>
                    <select
                      id="select-req-stage"
                      value={reqStage}
                      onChange={(e) => setReqStage(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      {ACADEMIC_STAGES.map(s => (
                        <option key={s.value} value={s.value}>{s.label} ({s.value})</option>
                      ))}
                    </select>
                    <span className="text-xs text-gray-400 mt-1 block">Stage at which document is required</span>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Document Type *
                    </label>
                    <select
                      id="select-req-doctype"
                      value={reqDocTypeId}
                      onChange={(e) => setReqDocTypeId(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      {documentTypes.length === 0 ? (
                        <option value="">No document types loaded</option>
                      ) : (
                        documentTypes.map(dt => (
                          <option key={dt.id} value={dt.id}>
                            {dt.code} — {dt.name}
                          </option>
                        ))
                      )}
                    </select>
                    <span className="text-xs text-gray-400 mt-1 block">From institutional document catalog</span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Programme Scope
                    </label>
                    <select
                      id="select-req-program"
                      value={reqProgramId}
                      onChange={(e) => setReqProgramId(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">Institution-wide (All Programmes)</option>
                      {programs.map(p => (
                        <option key={p.id} value={p.id}>
                          {p.code} — {p.name}
                        </option>
                      ))}
                    </select>
                    <span className="text-xs text-gray-400 mt-1 block">Leave empty to apply across all programmes</span>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Submission Deadline (Days from stage start)
                    </label>
                    <input
                      id="input-req-deadline"
                      type="number"
                      min="1"
                      max="365"
                      value={reqDeadlineDays}
                      onChange={(e) => setReqDeadlineDays(Number(e.target.value))}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                      placeholder="e.g. 15"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Min Submissions
                    </label>
                    <input
                      id="input-req-min"
                      type="number"
                      min="0"
                      max="10"
                      value={reqMinCount}
                      onChange={(e) => setReqMinCount(Number(e.target.value))}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Max Submissions
                    </label>
                    <input
                      id="input-req-max"
                      type="number"
                      min="1"
                      max="20"
                      value={reqMaxCount}
                      onChange={(e) => setReqMaxCount(Number(e.target.value))}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div className="flex items-center pt-5">
                    <label className="inline-flex items-center gap-2 cursor-pointer">
                      <input
                        id="checkbox-is-mandatory"
                        type="checkbox"
                        checked={reqIsMandatory}
                        onChange={(e) => setReqIsMandatory(e.target.checked)}
                        className="rounded text-blue-600 focus:ring-blue-500 h-5 w-5"
                      />
                      <span className="text-sm font-bold text-gray-800">
                        {reqIsMandatory ? 'Mandatory Requirement' : 'Optional Document'}
                      </span>
                    </label>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                    Guidance / Instructions for Students & Reviewers
                  </label>
                  <input
                    id="input-req-description"
                    type="text"
                    value={reqDescription}
                    onChange={(e) => setReqDescription(e.target.value)}
                    placeholder="e.g. Certified attested copy required before matriculation"
                    className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="pt-4 border-t flex justify-end gap-3">
                  <button
                    id="btn-define-requirement"
                    type="submit"
                    disabled={loading || documentTypes.length === 0}
                    className="px-6 py-2.5 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 shadow-sm transition disabled:opacity-50"
                  >
                    {loading ? 'Processing...' : 'Define Stage Requirement'}
                  </button>
                </div>
              </form>
            </div>

            {/* Overview Panel */}
            <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4 shadow-sm">
              <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider">
                Stage Requirements Summary
              </h3>
              <div className="space-y-3">
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                  <div className="text-xs font-medium text-gray-500">Total Stage Rules Defined</div>
                  <div className="text-2xl font-bold text-gray-900 mt-1">{requirements.length}</div>
                </div>
                <div className="p-3 bg-rose-50/60 rounded-lg border border-rose-100">
                  <div className="text-xs font-medium text-rose-800">Mandatory Requirements</div>
                  <div className="text-2xl font-bold text-rose-700 mt-1">
                    {requirements.filter(r => r.isMandatory).length}
                  </div>
                </div>
                <div className="p-3 bg-blue-50/60 rounded-lg border border-blue-100">
                  <div className="text-xs font-medium text-blue-800">Optional Submissions</div>
                  <div className="text-2xl font-bold text-blue-700 mt-1">
                    {requirements.filter(r => !r.isMandatory).length}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Requirements Table */}
          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
            <div className="px-6 py-4 border-b flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">Configured Stage Requirements</h2>
                <p className="text-xs text-gray-500 mt-0.5">Rules enforced during admission, enrollment, progression, and graduation.</p>
              </div>
              <div className="flex items-center gap-3">
                <label className="text-xs font-semibold text-gray-600">Filter Stage:</label>
                <select
                  id="select-filter-stage"
                  value={stageFilter}
                  onChange={(e) => {
                    setStageFilter(e.target.value);
                    loadRequirements(e.target.value);
                  }}
                  className="px-3 py-1.5 border rounded-lg text-xs font-medium focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">All Stages</option>
                  {ACADEMIC_STAGES.map(s => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table id="table-stage-requirements" className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="bg-gray-50 text-gray-600 text-xs uppercase tracking-wider border-b">
                    <th className="py-3 px-4 font-semibold">Stage</th>
                    <th className="py-3 px-4 font-semibold">Document Code & Name</th>
                    <th className="py-3 px-4 font-semibold">Requirement Rule</th>
                    <th className="py-3 px-4 font-semibold">Programme Scope</th>
                    <th className="py-3 px-4 font-semibold">Copies (Min/Max)</th>
                    <th className="py-3 px-4 font-semibold">Deadline</th>
                    <th className="py-3 px-4 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredRequirements.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-gray-400 text-sm">
                        No requirements defined for this stage. Use the form above to add a document requirement.
                      </td>
                    </tr>
                  ) : (
                    filteredRequirements.map((r) => (
                      <tr key={r.id} className="hover:bg-gray-50/75 transition">
                        <td className="py-3 px-4">
                          <span className="px-2.5 py-1 text-xs font-bold rounded-md bg-blue-50 text-blue-800 border border-blue-200">
                            {r.academicStage}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <div className="font-mono text-xs font-bold text-gray-900">{r.documentTypeCode}</div>
                          <div className="text-xs text-gray-500 mt-0.5">{r.documentTypeName}</div>
                        </td>
                        <td className="py-3 px-4">
                          {r.isMandatory ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rose-100 text-rose-800">
                              MANDATORY
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700">
                              OPTIONAL
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-xs">
                          {r.programCode ? (
                            <span className="font-mono text-indigo-700 font-semibold">{r.programCode}</span>
                          ) : (
                            <span className="text-gray-400 italic">Institution-Wide</span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-xs font-mono text-gray-600">
                          Min: {r.minCount} / Max: {r.maxCount ?? 'Unlimited'}
                        </td>
                        <td className="py-3 px-4 text-xs text-gray-600">
                          {r.submissionDeadlineDays ? `${r.submissionDeadlineDays} days` : 'No limit'}
                        </td>
                        <td className="py-3 px-4">
                          {r.isActive ? (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-emerald-100 text-emerald-800 rounded-full">
                              ACTIVE
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-gray-200 text-gray-600 rounded-full">
                              INACTIVE
                            </span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: Document Catalog & Types (STM-06-US-001) */}
      {activeTab === 'catalog' && (
        <div className="space-y-8">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Form Panel */}
            <div className="lg:col-span-2 bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-gray-900 mb-4 border-b pb-3">
                Configure Document Type
              </h2>
              <form onSubmit={handleConfigureDocumentType} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Document Type Code *
                    </label>
                    <input
                      id="input-doc-code"
                      type="text"
                      value={code}
                      onChange={(e) => setCode(e.target.value.toUpperCase())}
                      placeholder="e.g. DOC-TRANSCRIPT"
                      required
                      className="w-full px-3 py-2 border rounded-lg text-sm font-mono focus:ring-2 focus:ring-blue-500 uppercase"
                    />
                    <span className="text-xs text-gray-400 mt-1 block">Unique uppercase identifier</span>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Document Name *
                    </label>
                    <input
                      id="input-doc-name"
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="e.g. Official Academic Transcript"
                      required
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Category
                    </label>
                    <select
                      id="select-doc-category"
                      value={category}
                      onChange={(e) => setCategory(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="ACADEMIC">ACADEMIC</option>
                      <option value="IDENTIFICATION">IDENTIFICATION</option>
                      <option value="FINANCIAL">FINANCIAL</option>
                      <option value="LEGAL">LEGAL</option>
                      <option value="GENERAL">GENERAL</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Default Classification
                    </label>
                    <select
                      id="select-doc-classification"
                      value={classification}
                      onChange={(e) => setClassification(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="INTERNAL">INTERNAL (Default)</option>
                      <option value="PUBLIC">PUBLIC</option>
                      <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                      <option value="RESTRICTED">RESTRICTED</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                      Retention Policy Code
                    </label>
                    <input
                      id="input-doc-retention"
                      type="text"
                      value={retentionPolicyCode}
                      onChange={(e) => setRetentionPolicyCode(e.target.value.toUpperCase())}
                      placeholder="e.g. RET_PERMANENT"
                      className="w-full px-3 py-2 border rounded-lg text-sm font-mono focus:ring-2 focus:ring-blue-500 uppercase"
                    />
                  </div>

                  <div className="flex items-center gap-6 pt-5">
                    <label className="inline-flex items-center gap-2 cursor-pointer">
                      <input
                        id="checkbox-requires-signature"
                        type="checkbox"
                        checked={requiresSignature}
                        onChange={(e) => setRequiresSignature(e.target.checked)}
                        className="rounded text-blue-600 focus:ring-blue-500 h-4 w-4"
                      />
                      <span className="text-sm font-medium text-gray-700">Requires Signature</span>
                    </label>

                    <label className="inline-flex items-center gap-2 cursor-pointer">
                      <input
                        id="checkbox-is-active"
                        type="checkbox"
                        checked={isActive}
                        onChange={(e) => setIsActive(e.target.checked)}
                        className="rounded text-blue-600 focus:ring-blue-500 h-4 w-4"
                      />
                      <span className="text-sm font-medium text-gray-700">Active</span>
                    </label>
                  </div>
                </div>

                <div className="pt-4 border-t flex justify-end gap-3">
                  <button
                    id="btn-configure-doc-type"
                    type="submit"
                    disabled={loading}
                    className="px-6 py-2.5 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 shadow-sm transition disabled:opacity-50"
                  >
                    {loading ? 'Processing...' : 'Configure Document Type'}
                  </button>
                </div>
              </form>
            </div>

            {/* Catalog Overview Panel */}
            <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4 shadow-sm">
              <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider">
                Catalog Overview
              </h3>
              <div className="space-y-3">
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                  <div className="text-xs font-medium text-gray-500">Total Configured Types</div>
                  <div className="text-2xl font-bold text-gray-900 mt-1">{documentTypes.length}</div>
                </div>
                <div className="p-3 bg-emerald-50/50 rounded-lg border border-emerald-100">
                  <div className="text-xs font-medium text-emerald-800">Active Document Types</div>
                  <div className="text-2xl font-bold text-emerald-700 mt-1">
                    {documentTypes.filter(d => d.isActive).length}
                  </div>
                </div>
                <div className="p-3 bg-blue-50/50 rounded-lg border border-blue-100">
                  <div className="text-xs font-medium text-blue-800">Signature Required</div>
                  <div className="text-2xl font-bold text-blue-700 mt-1">
                    {documentTypes.filter(d => d.requiresSignature).length}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Catalog Table */}
          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
            <div className="px-6 py-4 border-b flex items-center justify-between">
              <h2 className="text-lg font-semibold text-gray-900">Institutional Document Types Catalog</h2>
              <span className="text-xs text-gray-500">{documentTypes.length} types registered</span>
            </div>
            <div className="overflow-x-auto">
              <table id="table-doc-types" className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="bg-gray-50 text-gray-600 text-xs uppercase tracking-wider border-b">
                    <th className="py-3 px-4 font-semibold">Code</th>
                    <th className="py-3 px-4 font-semibold">Name</th>
                    <th className="py-3 px-4 font-semibold">Category</th>
                    <th className="py-3 px-4 font-semibold">Classification</th>
                    <th className="py-3 px-4 font-semibold">Signature</th>
                    <th className="py-3 px-4 font-semibold">Retention</th>
                    <th className="py-3 px-4 font-semibold">Status</th>
                    <th className="py-3 px-4 font-semibold">Version</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {documentTypes.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-gray-400 text-sm">
                        No document types configured yet. Use the form above to configure the first type.
                      </td>
                    </tr>
                  ) : (
                    documentTypes.map((dt) => (
                      <tr key={dt.id} className="hover:bg-gray-50/75 transition">
                        <td className="py-3 px-4 font-mono font-medium text-blue-700">{dt.code}</td>
                        <td className="py-3 px-4 font-medium text-gray-900">{dt.name}</td>
                        <td className="py-3 px-4">
                          <span className="px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-700 rounded border">
                            {dt.category}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`px-2 py-0.5 text-xs font-medium rounded ${
                            dt.defaultClassification === 'RESTRICTED'
                              ? 'bg-rose-100 text-rose-800'
                              : dt.defaultClassification === 'CONFIDENTIAL'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-slate-100 text-slate-700'
                          }`}>
                            {dt.defaultClassification}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-xs">
                          {dt.requiresSignature ? (
                            <span className="text-indigo-700 font-semibold">Required</span>
                          ) : (
                            <span className="text-gray-400">Optional</span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-xs font-mono text-gray-500">{dt.retentionPolicyCode}</td>
                        <td className="py-3 px-4">
                          {dt.isActive ? (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-emerald-100 text-emerald-800 rounded-full">
                              ACTIVE
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 text-xs font-semibold bg-gray-200 text-gray-600 rounded-full">
                              INACTIVE
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-xs font-mono text-gray-400">v{dt.rowVersion}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
