/**
 * Client-side helpers for STM-06 student document uploads (contentBase64).
 * Never used for service-role keys; bytes stay in the JSON body to the BFF only.
 */

export const ALLOWED_DOCUMENT_MIME_TYPES = [
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/webp',
] as const;

export type AllowedDocumentMime = (typeof ALLOWED_DOCUMENT_MIME_TYPES)[number];

export const MAX_DOCUMENT_UPLOAD_BYTES = 10 * 1024 * 1024;

const EXT_TO_MIME: Record<string, AllowedDocumentMime> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};

export function isAllowedDocumentMime(mime: string | null | undefined): mime is AllowedDocumentMime {
  return !!mime && (ALLOWED_DOCUMENT_MIME_TYPES as readonly string[]).includes(mime);
}

export function resolveDocumentMime(file: File): AllowedDocumentMime | null {
  if (isAllowedDocumentMime(file.type)) {
    return file.type;
  }
  const ext = file.name.includes('.')
    ? file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
    : '';
  return EXT_TO_MIME[ext] || null;
}

export function estimateDecodedBytesFromBase64(b64: string): number {
  const cleaned = b64.replace(/\s/g, '');
  const padding = cleaned.endsWith('==') ? 2 : cleaned.endsWith('=') ? 1 : 0;
  return Math.max(0, Math.floor((cleaned.length * 3) / 4) - padding);
}

export function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + chunk, bytes.length)));
  }
  return btoa(binary);
}

export async function sha256Hex(buffer: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export type PreparedDocumentUpload = {
  fileName: string;
  mimeType: AllowedDocumentMime;
  sizeBytes: number;
  checksumSha256: string;
  contentBase64: string;
};

export async function prepareDocumentFile(file: File): Promise<PreparedDocumentUpload> {
  if (!file || file.size <= 0) {
    throw new Error('Please choose a non-empty file to upload');
  }
  if (file.size > MAX_DOCUMENT_UPLOAD_BYTES) {
    throw new Error('File size exceeds maximum institutional limit of 10 MB');
  }
  const mimeType = resolveDocumentMime(file);
  if (!mimeType) {
    throw new Error('Unsupported format. Allowed: PDF, JPEG, PNG, WEBP');
  }
  const name = file.name?.trim() || defaultNameForMime(mimeType);
  if (name.includes('..') || name.includes('/') || name.includes('\\')) {
    throw new Error('File name must not contain path separators');
  }
  const buffer = await file.arrayBuffer();
  if (buffer.byteLength !== file.size) {
    throw new Error('Unable to read file bytes consistently');
  }
  const contentBase64 = arrayBufferToBase64(buffer);
  const checksumSha256 = await sha256Hex(buffer);
  return {
    fileName: name,
    mimeType,
    sizeBytes: file.size,
    checksumSha256,
    contentBase64,
  };
}

export function formatUploadBytes(bytes?: number): string {
  if (!bytes || bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function defaultNameForMime(mime: AllowedDocumentMime): string {
  switch (mime) {
    case 'image/jpeg':
      return 'document.jpg';
    case 'image/png':
      return 'document.png';
    case 'image/webp':
      return 'document.webp';
    default:
      return 'document.pdf';
  }
}
