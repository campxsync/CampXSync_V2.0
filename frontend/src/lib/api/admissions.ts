/**
 * CampXSync Admissions Domain API Client (STM-02)
 * Dispatches all requests to API Gateway (/api/v1/admissions, /api/v1/applications)
 * propagating tenant and user security context headers.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export interface AdmissionIntake {
  id: string;
  tenantId: string;
  collegeId: string;
  programId: string;
  academicYearId: string;
  intakeName: string;
  intakeCode: string;
  applicationWindowStart: string;
  applicationWindowEnd: string;
  capacity: number;
  admittedCount: number;
  active: boolean;
  windowOpen: boolean;
  feeRequired: boolean;
  feeAmount: number | null;
  currency: string;
  feeTiming: string;
}

export interface ApplicationItem {
  id: string;
  tenantId: string;
  applicationNumber: string;
  intakeId: string;
  applicantPersonId: string;
  status: string;
  documentVerificationStatus: string;
  paymentStatus: string;
  paymentAmount: number | null;
  submissionTimestamp: string | null;
  eligibilityResult: string | null;
  declarationAccepted: boolean;
  applicantData: string;
  timelineStatus: string;
  rowVersion: number;
  createdAt: string;
}

export interface ApplicationDocument {
  id: string;
  applicationId: string;
  documentType: string;
  documentRefUrl: string;
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  verificationStatus: string;
  verifiedBy: string | null;
  rejectionReason: string | null;
  verifiedAt: string | null;
}

export interface ApplicationReview {
  id: string;
  applicationId: string;
  reviewerId: string;
  rubricVersion: string;
  totalScore: number;
  recommendation: string;
  confidentialComments: string;
  applicantFacingRemarks: string;
  createdAt: string;
}

export interface AdmissionDecision {
  id: string;
  applicationId: string;
  decisionType: string;
  decisionNotes: string;
  recordedBy: string;
  createdAt: string;
}

export interface AdmissionOffer {
  id: string;
  applicationId: string;
  offerCode: string;
  status: string;
  validTo: string;
  issuedAt: string;
  termsAccepted: boolean;
  acceptanceTimestamp: string | null;
}

export interface HandoverStatus {
  applicationId: string;
  offerId: string | null;
  createdStudentId: string | null;
  enrolmentStatus: string;
  retryCount: number;
  lastError: string | null;
  handoverCompletedAt: string | null;
}

export interface IntelligenceFunnel {
  tenantId: string;
  intakeId: string | null;
  totalApplications: number;
  draftCount: number;
  submittedCount: number;
  eligibleCount: number;
  underReviewCount: number;
  offeredCount: number;
  acceptedCount: number;
  enrolledCount: number;
  conversionRatePercent: number;
  calculatedAt: string;
}

function makeHeaders(tenantId: string, userId: string, role: string, collegeId?: string | null): Record<string, string> {
  const h: Record<string, string> = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'X-Tenant-Id': tenantId,
    'X-User-Id': userId,
    'X-User-Role': role,
  };
  if (collegeId) {
    h['X-College-Id'] = collegeId;
  }
  return h;
}

export async function fetchActiveIntakes(tenantId: string, userId: string, role: string, collegeId?: string | null): Promise<AdmissionIntake[]> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/intakes/active`;
    const res = await fetch(url, {
      method: 'GET',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      cache: 'no-store',
    });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : [];
  } catch (err) {
    console.warn('[AdmissionsAPI] fetchActiveIntakes network warning:', err);
    return [];
  }
}

export async function createIntake(
  tenantId: string,
  userId: string,
  role: string,
  payload: any,
  collegeId?: string | null
): Promise<{ success: boolean; data?: AdmissionIntake; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/intakes`;
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    const data = await res.json();
    return { success: true, data };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] createIntake network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function fetchApplications(
  tenantId: string,
  userId: string,
  role: string,
  filters?: { intakeId?: string; status?: string },
  collegeId?: string | null
): Promise<ApplicationItem[]> {
  try {
    const url = new URL('/api/v1/admissions/applications', GATEWAY_BASE_URL);
    const headers = makeHeaders(tenantId, userId, role, collegeId);
    if (filters?.intakeId) {
      headers['x-intake-id'] = filters.intakeId;
    }
    if (filters?.status) {
      headers['x-status'] = filters.status;
    }
    const res = await fetch(url.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : [];
  } catch (err) {
    console.warn('[AdmissionsAPI] fetchApplications network warning:', err);
    return [];
  }
}

export async function fetchApplicationDetails(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  collegeId?: string | null
): Promise<{
  application: ApplicationItem | null;
  documents: ApplicationDocument[];
  reviews: ApplicationReview[];
  decision: AdmissionDecision | null;
  offer: AdmissionOffer | null;
  handover: HandoverStatus | null;
}> {
  try {
    const headers = makeHeaders(tenantId, userId, role, collegeId);

    const [appRes, docsRes, revsRes, decRes, offRes, handRes] = await Promise.all([
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/reviews`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/decision`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/offer`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
      fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/handover`, { method: 'GET', headers, cache: 'no-store' }).catch(() => null),
    ]);

    const application = appRes && appRes.ok ? await appRes.json() : null;
    const documents = docsRes && docsRes.ok ? await docsRes.json() : [];
    const reviews = revsRes && revsRes.ok ? await revsRes.json() : [];
    const decision = decRes && decRes.ok ? await decRes.json() : null;
    const offer = offRes && offRes.ok ? await offRes.json() : null;
    const handover = handRes && handRes.ok ? await handRes.json() : null;

    return { application, documents, reviews, decision, offer, handover };
  } catch (err) {
    console.warn('[AdmissionsAPI] fetchApplicationDetails network warning:', err);
    return { application: null, documents: [], reviews: [], decision: null, offer: null, handover: null };
  }
}

export async function fetchFunnelIntelligence(
  tenantId: string,
  userId: string,
  role: string,
  intakeId?: string | null,
  collegeId?: string | null
): Promise<IntelligenceFunnel | null> {
  try {
    const headers = makeHeaders(tenantId, userId, role, collegeId);
    if (intakeId) {
      headers['x-intake-id'] = intakeId;
    }
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/admissions/intelligence/funnel`, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (!res.ok) return null;
    return await res.json();
  } catch (err) {
    console.warn('[AdmissionsAPI] fetchFunnelIntelligence network warning:', err);
    return null;
  }
}

export async function submitApplicationForm(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  declarationAccepted: boolean,
  privacyConsentAccepted: boolean,
  collegeId?: string | null
): Promise<{ success: boolean; data?: any; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/submit`;
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify({ declarationAccepted, privacyConsentAccepted }),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    const data = await res.json();
    return { success: true, data };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] submitApplicationForm network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function verifyApplicationDocument(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  documentId: string,
  status: 'VERIFIED' | 'REJECTED',
  rejectionReason?: string,
  collegeId?: string | null
): Promise<{ success: boolean; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents/${documentId}/verify`;
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify({ status, rejectionReason }),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    return { success: true };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] verifyApplicationDocument network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function recordDecisionAction(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  decisionType: string,
  decisionNotes: string,
  collegeId?: string | null
): Promise<{ success: boolean; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/decision`;
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify({ decisionType, decisionNotes }),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    return { success: true };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] recordDecisionAction network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function issueOfferAction(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  offerTermsVersion: string,
  validToDays: number,
  collegeId?: string | null
): Promise<{ success: boolean; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/offers`;
    const validTo = new Date();
    validTo.setDate(validTo.getDate() + validToDays);
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify({ offerTermsVersion, validTo: validTo.toISOString() }),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    return { success: true };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] issueOfferAction network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function acceptOfferAction(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  collegeId?: string | null
): Promise<{ success: boolean; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/offers/accept`;
    const res = await fetch(url, {
      method: 'POST',
      headers: makeHeaders(tenantId, userId, role, collegeId),
      body: JSON.stringify({ confirmAcceptance: true }),
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    return { success: true };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] acceptOfferAction network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

export async function triggerHandoverAction(
  tenantId: string,
  userId: string,
  role: string,
  applicationId: string,
  idempotencyKey?: string,
  collegeId?: string | null
): Promise<{ success: boolean; data?: any; error?: string }> {
  try {
    const url = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/handover`;
    const headers = makeHeaders(tenantId, userId, role, collegeId);
    if (idempotencyKey) {
      headers['idempotency-key'] = idempotencyKey;
    }
    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: '',
    });
    if (!res.ok) {
      const err = await res.text();
      return { success: false, error: err };
    }
    const data = await res.json();
    return { success: true, data };
  } catch (err: any) {
    console.warn('[AdmissionsAPI] triggerHandoverAction network warning:', err);
    return { success: false, error: err.message || 'Service unavailable' };
  }
}

