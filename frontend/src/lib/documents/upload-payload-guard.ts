/**
 * BFF-side guards for student document upload JSON.
 * Rejects oversized / disallowed payloads without logging file bytes or secrets.
 */

import {
  ALLOWED_DOCUMENT_MIME_TYPES,
  estimateDecodedBytesFromBase64,
  isAllowedDocumentMime,
  MAX_DOCUMENT_UPLOAD_BYTES,
} from './file-upload';

export type UploadPayloadGuardResult =
  | { ok: true; sizeBytes: number; mimeType: string }
  | { ok: false; status: number; code: string; message: string };

export function guardDocumentUploadPayload(payload: Record<string, unknown> | null | undefined): UploadPayloadGuardResult {
  if (!payload || typeof payload !== 'object') {
    return { ok: false, status: 400, code: 'INVALID_JSON', message: 'Upload payload is required' };
  }

  // Never accept client-supplied storage paths or secrets
  if ('storagePath' in payload && payload.storagePath != null && String(payload.storagePath).trim() !== '') {
    return {
      ok: false,
      status: 422,
      code: 'CLIENT_STORAGE_PATH_FORBIDDEN',
      message: 'Client-supplied storagePath is not allowed; the service constructs object paths',
    };
  }
  for (const banned of [
    'serviceRoleKey',
    'service_role',
    'CAMPX_SUPABASE_SERVICE_ROLE_KEY',
    'supabaseServiceRoleKey',
  ]) {
    if (banned in payload) {
      return {
        ok: false,
        status: 422,
        code: 'SECRET_FIELD_FORBIDDEN',
        message: 'Upload payload must not include credential fields',
      };
    }
  }

  const mimeType = typeof payload.mimeType === 'string' ? payload.mimeType.trim().toLowerCase() : '';
  if (!isAllowedDocumentMime(mimeType)) {
    return {
      ok: false,
      status: 422,
      code: 'UNSUPPORTED_MIME',
      message: `Unsupported format. Allowed: ${ALLOWED_DOCUMENT_MIME_TYPES.join(', ')}`,
    };
  }

  const contentBase64 =
    typeof payload.contentBase64 === 'string' ? payload.contentBase64.trim() : '';
  if (!contentBase64) {
    return {
      ok: false,
      status: 422,
      code: 'CONTENT_REQUIRED',
      message: 'contentBase64 is required for document upload',
    };
  }

  const estimated = estimateDecodedBytesFromBase64(contentBase64);
  if (estimated <= 0) {
    return { ok: false, status: 422, code: 'INVALID_CONTENT_BASE64', message: 'contentBase64 is empty or invalid' };
  }
  if (estimated > MAX_DOCUMENT_UPLOAD_BYTES) {
    return {
      ok: false,
      status: 422,
      code: 'FILE_TOO_LARGE',
      message: 'File size exceeds maximum institutional limit of 10 MB',
    };
  }

  const declared =
    typeof payload.sizeBytes === 'number'
      ? payload.sizeBytes
      : typeof payload.sizeBytes === 'string'
        ? Number(payload.sizeBytes)
        : NaN;
  if (!Number.isFinite(declared) || declared <= 0) {
    return { ok: false, status: 422, code: 'INVALID_FILE_SIZE', message: 'sizeBytes must be a positive number' };
  }
  if (declared > MAX_DOCUMENT_UPLOAD_BYTES) {
    return {
      ok: false,
      status: 422,
      code: 'FILE_TOO_LARGE',
      message: 'File size exceeds maximum institutional limit of 10 MB',
    };
  }
  // Allow small base64 padding variance (±2) but catch clear mismatches
  if (Math.abs(declared - estimated) > 2) {
    return {
      ok: false,
      status: 422,
      code: 'SIZE_MISMATCH',
      message: 'Declared sizeBytes does not match contentBase64 length',
    };
  }

  const checksum =
    typeof payload.checksumSha256 === 'string' ? payload.checksumSha256.trim() : '';
  if (!checksum || checksum.length < 32) {
    return {
      ok: false,
      status: 422,
      code: 'MISSING_CHECKSUM',
      message: 'checksumSha256 is required',
    };
  }

  return { ok: true, sizeBytes: declared, mimeType };
}
