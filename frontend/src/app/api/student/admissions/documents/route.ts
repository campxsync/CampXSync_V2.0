import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { query } from '@/lib/db';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'] as const;
const MAX_DOC_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

const AttachDocumentSchema = z.object({
  applicationId: z.string().uuid('Valid application UUID is required'),
  documentType: z.enum([
    'MARKSHEET_10',
    'MARKSHEET_12',
    'HIGH_SCHOOL_TRANSCRIPT',
    'ID_PROOF',
    'TRANSFER_CERT',
    'ENTRANCE_SCORECARD',
    'GOVT_ID',
  ]),
  fileName: z.string().min(1, 'File name is required').max(255),
  mimeType: z.enum(ALLOWED_MIME_TYPES, {
    errorMap: () => ({ message: 'Invalid MIME type. Allowed: PDF, JPEG, PNG, WEBP' }),
  }),
  fileSizeBytes: z.number().int().positive().max(MAX_DOC_SIZE_BYTES, 'File size cannot exceed 10MB'),
  storagePath: z.string().max(512).optional(),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 },
        { status: 401 }
      );
    }

    const { tenantId, userId, role } = resolution.session;
    if (!tenantId || !userId) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 },
        { status: 400 }
      );
    }

    if (role !== 'student') {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'Student role required' },
        { status: 403 }
      );
    }

    // Resolve student's own application
    const appRes = await query(
      `SELECT a.id, a.application_number, a.status, a.document_verification_status,
              i.intake_name, i.is_active, i.application_window_start, i.application_window_end
       FROM stu.applications a
       LEFT JOIN stu.admission_intakes i ON i.id = a.intake_id AND i.tenant_id = a.tenant_id
       WHERE a.applicant_person_id = $1 AND a.tenant_id = $2
       ORDER BY a.created_at DESC
       LIMIT 1`,
      [userId, tenantId]
    );

    if (appRes.rows.length === 0) {
      return NextResponse.json({
        application: null,
        documents: [],
        canUpload: false,
        message: 'No active application found for this profile.',
      });
    }

    const application = appRes.rows[0];
    const uploadableStatuses = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW'];
    const canUpload = uploadableStatuses.includes(application.status) && application.is_active !== false;

    // Fetch documents for the student's own application
    const docsRes = await query(
      `SELECT id, document_id, document_type, storage_path, file_name, mime_type,
              file_size_bytes, verification_status, rejection_reason, verified_at, created_at
       FROM stu.application_documents
       WHERE application_id = $1 AND tenant_id = $2
       ORDER BY created_at DESC`,
      [application.id, tenantId]
    );

    return NextResponse.json({
      application: {
        id: application.id,
        applicationNumber: application.application_number,
        status: application.status,
        documentVerificationStatus: application.document_verification_status,
        intakeName: application.intake_name,
        isReadonly: !canUpload,
      },
      documents: docsRes.rows.map(row => ({
        id: row.id,
        documentId: row.document_id,
        documentType: row.document_type,
        fileName: row.file_name,
        mimeType: row.mime_type,
        fileSizeBytes: Number(row.file_size_bytes),
        verificationStatus: row.verification_status,
        rejectionReason: row.rejection_reason,
        verifiedAt: row.verified_at,
        createdAt: row.created_at,
      })),
      canUpload,
    });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 },
        { status: 401 }
      );
    }

    const { tenantId, userId, role } = resolution.session;
    if (!tenantId || !userId) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 },
        { status: 400 }
      );
    }

    if (role !== 'student') {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'Student role required' },
        { status: 403 }
      );
    }

    const body = await req.json();
    const parsed = AttachDocumentSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        { error: 'Validation failed', details: parsed.error.format() },
        { status: 400 }
      );
    }

    const { applicationId, documentType, fileName, mimeType, fileSizeBytes, storagePath } = parsed.data;

    // Strict ownership verification: the application must belong to caller
    const appCheck = await query(
      `SELECT a.id, a.status, i.is_active, i.application_window_start, i.application_window_end
       FROM stu.applications a
       JOIN stu.admission_intakes i ON i.id = a.intake_id AND i.tenant_id = a.tenant_id
       WHERE a.id = $1 AND a.applicant_person_id = $2 AND a.tenant_id = $3`,
      [applicationId, userId, tenantId]
    );

    if (appCheck.rows.length === 0) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'Foreign application access denied' },
        { status: 403 }
      );
    }

    const appRow = appCheck.rows[0];
    const uploadableStatuses = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW'];
    if (!uploadableStatuses.includes(appRow.status)) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'Invalid Application State', status: 400, detail: `Application status is ${appRow.status}. Document uploads are locked.` },
        { status: 400 }
      );
    }

    if (appRow.is_active === false) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'Intake Closed', status: 400, detail: 'Admission intake window is closed for uploads.' },
        { status: 400 }
      );
    }

    // Try Java admissions-service gateway first
    try {
      const headers = await getGatewayHeaders(req, supabase, resolution.session, 'APPLICANT');
      headers['Content-Type'] = 'application/json';

      const attachUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents`;
      const gwRes = await fetch(attachUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          documentType,
          fileName,
          mimeType,
          fileSizeBytes,
          storagePath: storagePath || `admissions/${applicationId}/${Date.now()}_${fileName}`,
        }),
        cache: 'no-store',
      });

      if (gwRes.ok) {
        const gwData = await gwRes.json();
        return NextResponse.json(gwData, { status: 201 });
      }
    } catch {
      // Gateway unreachable or local fallback
    }

    // Direct database insert
    const insertRes = await query(
      `INSERT INTO stu.application_documents (
         tenant_id, id, application_id, document_id, document_type, storage_path,
         file_name, mime_type, file_size_bytes, verification_status, is_fraudulent, created_at, updated_at, row_version
       ) VALUES (
         $1, gen_random_uuid(), $2, gen_random_uuid(), $3, $4, $5, $6, $7, 'PENDING', false, now(), now(), 1
       )
       RETURNING id, document_type, file_name, file_size_bytes, mime_type, verification_status, created_at`,
      [
        tenantId,
        applicationId,
        documentType,
        storagePath || `admissions/${applicationId}/${Date.now()}_${fileName}`,
        fileName,
        mimeType,
        fileSizeBytes,
      ]
    );

    return NextResponse.json(insertRes.rows[0], { status: 201 });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}

