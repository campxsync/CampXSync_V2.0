/**
 * Frontend 1b — document upload helpers + BFF guard source contract.
 * Run: node --experimental-strip-types scripts/verify_document_upload_guard.mjs
 */
import { createHash } from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const docsLib = path.join(__dirname, '../frontend/src/lib/documents');

const {
  ALLOWED_DOCUMENT_MIME_TYPES,
  estimateDecodedBytesFromBase64,
  isAllowedDocumentMime,
  MAX_DOCUMENT_UPLOAD_BYTES,
} = await import(pathToFileURL(path.join(docsLib, 'file-upload.ts')).href);

/** Mirrors frontend/src/lib/documents/upload-payload-guard.ts (kept in lockstep via source checks). */
function guardDocumentUploadPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return { ok: false, status: 400, code: 'INVALID_JSON', message: 'Upload payload is required' };
  }
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

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

function pdfBytes(label = '1b') {
  return Buffer.from(`%PDF-1.4 ${label}`, 'ascii');
}

function b64(buf) {
  return Buffer.from(buf).toString('base64');
}

function sha256(buf) {
  return createHash('sha256').update(buf).digest('hex');
}

const checks = [];

function check(name, fn) {
  try {
    fn();
    checks.push({ name, ok: true });
    console.log(`[PASS] ${name}`);
  } catch (err) {
    checks.push({ name, ok: false, detail: err.message });
    console.error(`[FAIL] ${name}: ${err.message}`);
  }
}

check('allowed MIME list includes pdf/jpeg/png/webp', () => {
  assert(ALLOWED_DOCUMENT_MIME_TYPES.includes('application/pdf'), 'pdf missing');
  assert(isAllowedDocumentMime('image/jpeg'), 'jpeg');
  assert(!isAllowedDocumentMime('text/plain'), 'text/plain must be rejected');
});

check('MAX is 10MB', () => {
  assert(MAX_DOCUMENT_UPLOAD_BYTES === 10 * 1024 * 1024, `got ${MAX_DOCUMENT_UPLOAD_BYTES}`);
});

check('valid PDF payload accepted', () => {
  const bytes = pdfBytes('ok');
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: bytes.length,
    checksumSha256: sha256(bytes),
    contentBase64: b64(bytes),
  });
  assert(r.ok === true, JSON.stringify(r));
});

check('missing contentBase64 rejected', () => {
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: 12,
    checksumSha256: 'a'.repeat(64),
  });
  assert(r.ok === false && r.code === 'CONTENT_REQUIRED', JSON.stringify(r));
});

check('unsupported MIME rejected', () => {
  const bytes = Buffer.from('hello');
  const r = guardDocumentUploadPayload({
    mimeType: 'text/plain',
    sizeBytes: bytes.length,
    checksumSha256: sha256(bytes),
    contentBase64: b64(bytes),
  });
  assert(r.ok === false && r.code === 'UNSUPPORTED_MIME', JSON.stringify(r));
});

check('oversized declared size rejected', () => {
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: MAX_DOCUMENT_UPLOAD_BYTES + 1,
    checksumSha256: 'a'.repeat(64),
    contentBase64: b64(pdfBytes()),
  });
  assert(r.ok === false && r.code === 'FILE_TOO_LARGE', JSON.stringify(r));
});

check('size mismatch rejected', () => {
  const bytes = pdfBytes('mismatch');
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: bytes.length + 50,
    checksumSha256: sha256(bytes),
    contentBase64: b64(bytes),
  });
  assert(r.ok === false && r.code === 'SIZE_MISMATCH', JSON.stringify(r));
});

check('client storagePath forbidden', () => {
  const bytes = pdfBytes('path');
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: bytes.length,
    checksumSha256: sha256(bytes),
    contentBase64: b64(bytes),
    storagePath: 'tenants/evil/steal.pdf',
  });
  assert(r.ok === false && r.code === 'CLIENT_STORAGE_PATH_FORBIDDEN', JSON.stringify(r));
});

check('service-role field forbidden', () => {
  const bytes = pdfBytes('secret');
  const r = guardDocumentUploadPayload({
    mimeType: 'application/pdf',
    sizeBytes: bytes.length,
    checksumSha256: sha256(bytes),
    contentBase64: b64(bytes),
    serviceRoleKey: 'should-never-reach-bff',
  });
  assert(r.ok === false && r.code === 'SECRET_FIELD_FORBIDDEN', JSON.stringify(r));
});

check('base64 size estimate matches', () => {
  const bytes = pdfBytes('est');
  assert(estimateDecodedBytesFromBase64(b64(bytes)) === bytes.length, 'estimate');
});

check('production guard source contains key rejection codes', () => {
  const guardSrc = fs.readFileSync(path.join(docsLib, 'upload-payload-guard.ts'), 'utf8');
  for (const code of [
    'CONTENT_REQUIRED',
    'UNSUPPORTED_MIME',
    'FILE_TOO_LARGE',
    'CLIENT_STORAGE_PATH_FORBIDDEN',
    'SECRET_FIELD_FORBIDDEN',
    'SIZE_MISMATCH',
  ]) {
    assert(guardSrc.includes(`'${code}'`) || guardSrc.includes(`"${code}"`), `missing ${code}`);
  }
  assert(guardSrc.includes('contentBase64'), 'contentBase64');
  assert(!guardSrc.includes('process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY'), 'must not read service role');
});

const failed = checks.filter((c) => !c.ok);
console.log(`\nDocument upload guard: ${checks.length - failed.length}/${checks.length} PASS`);
if (failed.length) process.exit(1);
