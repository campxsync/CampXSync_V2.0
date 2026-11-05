'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  FileText,
  Upload,
  CheckCircle2,
  AlertCircle,
  Clock,
  XCircle,
  Loader2,
  ArrowLeft,
  FileCheck,
  RefreshCw,
  Info,
  ShieldCheck,
  Lock,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface ApplicationInfo {
  id: string;
  applicationNumber: string;
  status: string;
  documentVerificationStatus: string;
  intakeName?: string;
  isReadonly: boolean;
}

interface ApplicationDoc {
  id: string;
  documentId: string;
  documentType: string;
  fileName: string;
  mimeType: string;
  fileSizeBytes: number;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'WAIVED';
  rejectionReason?: string | null;
  verifiedAt?: string | null;
  createdAt: string;
}

const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  HIGH_SCHOOL_TRANSCRIPT: 'High School Transcript / Cumulative Record',
  MARKSHEET_10: '10th Standard Marksheet / Secondary Certificate',
  MARKSHEET_12: '12th Standard Marksheet / Higher Secondary',
  ID_PROOF: 'Government Photo ID / Identity Proof',
  GOVT_ID: 'Official National ID Card',
  TRANSFER_CERT: 'Transfer Certificate (TC) / Migration Certificate',
  ENTRANCE_SCORECARD: 'Entrance Examination Scorecard',
};

const ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
const MAX_DOC_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function StudentDocumentsPage() {
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [application, setApplication] = useState<ApplicationInfo | null>(null);
  const [documents, setDocuments] = useState<ApplicationDoc[]>([]);
  const [canUpload, setCanUpload] = useState(false);

  // Upload Form State
  const [selectedDocType, setSelectedDocType] = useState<string>('HIGH_SCHOOL_TRANSCRIPT');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [formValidation, setFormValidation] = useState<string | null>(null);

  useEffect(() => {
    fetchDocuments();
  }, []);

  async function fetchDocuments() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/student/admissions/documents', { cache: 'no-store' });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || `HTTP error ${res.status}`);
      }
      const data = await res.json();
      setApplication(data.application);
      setDocuments(data.documents || []);
      setCanUpload(Boolean(data.canUpload));
    } catch (err: any) {
      setError(err.message || 'Failed to load application documents.');
    } finally {
      setLoading(false);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    setFormValidation(null);
    if (!e.target.files || e.target.files.length === 0) {
      setSelectedFile(null);
      return;
    }

    const file = e.target.files[0];
    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      setFormValidation('Invalid format. Only PDF, JPEG, PNG, and WEBP documents are accepted.');
      setSelectedFile(null);
      return;
    }

    if (file.size > MAX_DOC_SIZE_BYTES) {
      setFormValidation(`File is too large (${formatBytes(file.size)}). Maximum allowed file size is 10 MB.`);
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!application) {
      setError('No active application found to attach documents to.');
      return;
    }
    if (!selectedFile) {
      setFormValidation('Please choose a file to upload.');
      return;
    }

    setUploading(true);
    setError(null);
    setSuccessMsg(null);
    setFormValidation(null);

    try {
      const payload = {
        applicationId: application.id,
        documentType: selectedDocType,
        fileName: selectedFile.name,
        mimeType: selectedFile.type,
        fileSizeBytes: selectedFile.size,
      };

      const res = await fetch('/api/student/admissions/documents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || data.detail || `Upload failed with status ${res.status}`);
      }

      setSuccessMsg(`Document "${selectedFile.name}" attached successfully and queued for verification.`);
      setSelectedFile(null);
      // Reset input element
      const fileInput = document.getElementById('document-file-input') as HTMLInputElement | null;
      if (fileInput) fileInput.value = '';

      // Refresh document list
      await fetchDocuments();
    } catch (err: any) {
      setError(err.message || 'Failed to attach document.');
    } finally {
      setUploading(false);
    }
  }

  function handleReuploadClick(docType: string) {
    setSelectedDocType(docType);
    setFormValidation(null);
    const formEl = document.getElementById('document-upload-card');
    if (formEl) {
      formEl.scrollIntoView({ behavior: 'smooth' });
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Loading your application documents and verification status...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-5xl mx-auto pb-12">
      {/* Breadcrumb Navigation */}
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/student" className="flex items-center gap-1 hover:text-foreground transition-colors">
          <ArrowLeft className="h-4 w-4" />
          <span>Dashboard</span>
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Application Documents</span>
      </div>

      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Application Documents & Verification
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Track verification progress and upload mandatory admission certificates.
          </p>
        </div>

        {application && (
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline" className="font-mono text-xs">
              {application.applicationNumber}
            </Badge>
            <Badge
              className={
                application.status === 'HANDED_OVER'
                  ? 'bg-emerald-600 text-white'
                  : application.status === 'SUBMITTED'
                  ? 'bg-blue-600 text-white'
                  : 'bg-amber-600 text-white'
              }
            >
              {application.status}
            </Badge>
          </div>
        )}
      </div>

      {/* Alert Notices */}
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 flex items-start gap-3">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-medium">Error processing request</p>
            <p className="mt-1 text-red-700">{error}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setError(null)}>
            Dismiss
          </Button>
        </div>
      )}

      {successMsg && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 flex items-start gap-3">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-medium">Operation Successful</p>
            <p className="mt-1 text-emerald-700">{successMsg}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setSuccessMsg(null)}>
            Dismiss
          </Button>
        </div>
      )}

      {/* Read-Only Notice for Enrolled Students */}
      {application?.isReadonly && (
        <div className="rounded-lg border border-blue-200 bg-blue-50/80 p-4 text-sm text-blue-900 flex items-start gap-3">
          <Lock className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-blue-950">Enrollment Completed & Documents Locked</p>
            <p className="mt-0.5 text-blue-800">
              Your application has concluded with enrollment handover (status: <span className="font-mono">{application.status}</span>). Application document uploads are locked. All records below are preserved for historical compliance.
            </p>
          </div>
        </div>
      )}

      {/* Upload Zone (Visible only when application allows upload) */}
      {canUpload && (
        <Card id="document-upload-card" className="border-primary/20 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg flex items-center gap-2">
              <Upload className="h-5 w-5 text-primary" />
              Upload Supporting Document
            </CardTitle>
            <CardDescription>
              Accepted formats: PDF, JPEG, PNG, WEBP (Maximum size: 10 MB per file).
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleUpload} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium mb-1.5 text-foreground">
                    Document Category <span className="text-red-500">*</span>
                  </label>
                  <select
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring"
                    value={selectedDocType}
                    onChange={(e) => setSelectedDocType(e.target.value)}
                    disabled={uploading}
                  >
                    {Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1.5 text-foreground">
                    Select File <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="document-file-input"
                    type="file"
                    accept=".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"
                    onChange={handleFileChange}
                    disabled={uploading}
                    className="w-full text-sm text-foreground file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-primary/10 file:text-primary hover:file:bg-primary/20 cursor-pointer"
                  />
                  {formValidation && (
                    <p className="text-xs text-red-600 mt-1 font-medium">{formValidation}</p>
                  )}
                </div>
              </div>

              {selectedFile && !formValidation && (
                <div className="rounded-md bg-muted/50 p-3 text-xs flex items-center justify-between text-muted-foreground border">
                  <span>Selected: <strong className="text-foreground">{selectedFile.name}</strong> ({formatBytes(selectedFile.size)})</span>
                  <Badge variant="outline">{selectedFile.type || 'Document'}</Badge>
                </div>
              )}

              <div className="flex justify-end pt-2">
                <Button type="submit" disabled={uploading || !selectedFile || Boolean(formValidation)} className="gap-2">
                  {uploading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Uploading & Registering...
                    </>
                  ) : (
                    <>
                      <Upload className="h-4 w-4" />
                      Attach Document
                    </>
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Uploaded Documents List */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-lg flex items-center gap-2">
              <FileCheck className="h-5 w-5 text-primary" />
              Attached Application Documents
            </CardTitle>
            <CardDescription>
              {documents.length} document{documents.length === 1 ? '' : 's'} registered on this application.
            </CardDescription>
          </div>
          <Button variant="outline" size="sm" onClick={fetchDocuments} className="gap-1.5 text-xs">
            <RefreshCw className="h-3.5 w-3.5" />
            Refresh
          </Button>
        </CardHeader>
        <CardContent>
          {documents.length === 0 ? (
            <div className="text-center py-12 border border-dashed rounded-lg bg-muted/20">
              <FileText className="h-10 w-10 text-muted-foreground/60 mx-auto mb-3" />
              <h3 className="text-sm font-semibold text-foreground">No documents uploaded yet</h3>
              <p className="text-xs text-muted-foreground mt-1 max-w-sm mx-auto">
                {canUpload
                  ? 'Attach your transcripts, marksheets, and identity proof using the form above.'
                  : 'No application documents were attached for this intake.'}
              </p>
            </div>
          ) : (
            <div className="divide-y rounded-md border">
              {documents.map((doc) => {
                const isRejected = doc.verificationStatus === 'REJECTED';
                const isVerified = doc.verificationStatus === 'VERIFIED';
                const isPending = doc.verificationStatus === 'PENDING';

                return (
                  <div key={doc.id} className="p-4 space-y-3 hover:bg-muted/10 transition-colors">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                      <div className="flex items-start gap-3">
                        <div className="p-2 rounded-md bg-primary/10 text-primary mt-0.5">
                          <FileText className="h-5 w-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-sm text-foreground">
                              {DOCUMENT_TYPE_LABELS[doc.documentType] || doc.documentType}
                            </span>
                            <Badge variant="outline" className="text-[11px] font-mono">
                              {doc.documentType}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5 font-mono">
                            {doc.fileName} • {formatBytes(doc.fileSizeBytes)} • {doc.mimeType}
                          </p>
                          <p className="text-[11px] text-muted-foreground mt-0.5">
                            Uploaded: {new Date(doc.createdAt).toLocaleString()}
                          </p>
                        </div>
                      </div>

                      {/* Status Badges */}
                      <div className="flex items-center gap-2 self-start sm:self-center">
                        {isVerified && (
                          <Badge className="bg-emerald-100 text-emerald-800 border-emerald-300 gap-1 py-1">
                            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                            Verified
                          </Badge>
                        )}
                        {isPending && (
                          <Badge className="bg-amber-100 text-amber-800 border-amber-300 gap-1 py-1">
                            <Clock className="h-3.5 w-3.5 text-amber-600" />
                            Pending Verification
                          </Badge>
                        )}
                        {isRejected && (
                          <Badge className="bg-red-100 text-red-800 border-red-300 gap-1 py-1">
                            <XCircle className="h-3.5 w-3.5 text-red-600" />
                            Verification Rejected
                          </Badge>
                        )}
                        {doc.verificationStatus === 'WAIVED' && (
                          <Badge variant="outline" className="gap-1 py-1">
                            <ShieldCheck className="h-3.5 w-3.5 text-muted-foreground" />
                            Waived
                          </Badge>
                        )}
                      </div>
                    </div>

                    {/* Rejection Alert Banner with Reason and Re-upload action */}
                    {isRejected && (
                      <div className="rounded-md border border-red-200 bg-red-50/90 p-3 text-xs text-red-900 space-y-2">
                        <div className="flex items-start gap-2">
                          <AlertCircle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
                          <div className="flex-1">
                            <span className="font-semibold text-red-950">Rejection Reason: </span>
                            <span className="text-red-900 font-medium">{doc.rejectionReason || 'No specific reason provided by admissions reviewer.'}</span>
                          </div>
                        </div>

                        {canUpload && (
                          <div className="flex justify-end pt-1">
                            <Button
                              type="button"
                              size="sm"
                              variant="danger"
                              className="h-7 text-xs gap-1.5"
                              onClick={() => handleReuploadClick(doc.documentType)}
                            >
                              <RefreshCw className="h-3 w-3" />
                              Re-upload Corrected Document
                            </Button>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Verified Details */}
                    {isVerified && doc.verifiedAt && (
                      <div className="text-[11px] text-emerald-800 bg-emerald-50/50 rounded px-2.5 py-1 border border-emerald-100">
                        Official verification confirmed on {new Date(doc.verifiedAt).toLocaleString()}.
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

