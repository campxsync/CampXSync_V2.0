import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
// Node's type stripper only resolves this import with a .ts extension.
// @ts-expect-error TS5097 allowImportingTsExtensions is disabled for the Next.js build.
import { resolveDbSsl } from './db-ssl.ts';

const PEM = '-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n';

test('inline PEM verifies the server', () => {
  const ssl = resolveDbSsl({ CAMPX_DB_SSL_CA: PEM });
  assert.equal(ssl.rejectUnauthorized, true);
  assert.equal(ssl.ca, PEM.trim());
});

test('CA file PEM verifies the server', () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'campx-db-ssl-'));
  const file = path.join(dir, 'ca.pem');
  writeFileSync(file, PEM);
  try {
    const ssl = resolveDbSsl({ CAMPX_DB_SSL_CA_FILE: file });
    assert.equal(ssl.rejectUnauthorized, true);
    assert.equal(ssl.ca, PEM.trim());
    assert.equal(String(ssl.ca).includes('BEGIN CERTIFICATE'), true);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('missing CA file throws without reading a certificate into the message', () => {
  const missing = path.join(tmpdir(), 'campx-db-ssl-missing.pem');
  assert.throws(
    () => resolveDbSsl({ CAMPX_DB_SSL_CA_FILE: missing }),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.match(err.message, /not found/);
      assert.equal(err.message.includes('BEGIN CERTIFICATE'), false);
      return true;
    },
  );
});

test('neither CA keeps encryption without server authentication', () => {
  const ssl = resolveDbSsl({});
  assert.equal(ssl.rejectUnauthorized, false);
  assert.equal(ssl.ca, undefined);
});

test('strict mode without a CA throws', () => {
  assert.throws(
    () => resolveDbSsl({ CAMPX_DB_SSL_STRICT: 'true' }),
    /CAMPX_DB_SSL_STRICT=true requires CAMPX_DB_SSL_CA or CAMPX_DB_SSL_CA_FILE/,
  );
});

test('strict mode with an inline CA verifies', () => {
  const ssl = resolveDbSsl({ CAMPX_DB_SSL_STRICT: 'true', CAMPX_DB_SSL_CA: PEM });
  assert.equal(ssl.rejectUnauthorized, true);
  assert.equal(ssl.ca, PEM.trim());
});

test('inline PEM is used when a CA file is also set', () => {
  const ssl = resolveDbSsl({
    CAMPX_DB_SSL_CA: PEM,
    CAMPX_DB_SSL_CA_FILE: path.join(tmpdir(), 'campx-db-ssl-unused.pem'),
  });
  assert.equal(ssl.rejectUnauthorized, true);
  assert.equal(ssl.ca, PEM.trim());
});
