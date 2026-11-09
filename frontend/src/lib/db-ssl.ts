import { readFileSync } from 'fs';
import type { ConnectionOptions } from 'tls';

export type DbSslEnv = {
  CAMPX_DB_SSL_CA?: string;
  CAMPX_DB_SSL_CA_FILE?: string;
  CAMPX_DB_SSL_STRICT?: string;
  [key: string]: string | undefined;
};

function readCaPem(filePath: string): string {
  let pem: string;
  try {
    pem = readFileSync(filePath, 'utf8');
  } catch (err) {
    const code =
      err !== null && typeof err === 'object' && 'code' in err
        ? String((err as { code: unknown }).code)
        : '';
    if (code === 'ENOENT') {
      throw new Error(`Database TLS CA file not found: ${filePath}`);
    }
    throw new Error(`Unable to read database TLS CA file: ${filePath}`);
  }

  const ca = pem.trim();
  if (!ca) {
    throw new Error(`Database TLS CA file is empty: ${filePath}`);
  }
  return ca;
}

/**
 * pg `ssl` option for the database pool.
 * Verifies the server only when a CA PEM is configured. Strict mode with no CA is refused.
 */
export function resolveDbSsl(env: DbSslEnv): ConnectionOptions {
  const inline = env.CAMPX_DB_SSL_CA?.trim() ?? '';
  const caFile = env.CAMPX_DB_SSL_CA_FILE?.trim() ?? '';

  if (inline) {
    return { rejectUnauthorized: true, ca: inline };
  }

  if (caFile) {
    return { rejectUnauthorized: true, ca: readCaPem(caFile) };
  }

  if (env.CAMPX_DB_SSL_STRICT === 'true') {
    throw new Error(
      'CAMPX_DB_SSL_STRICT=true requires CAMPX_DB_SSL_CA or CAMPX_DB_SSL_CA_FILE. Refusing an unverified database connection.',
    );
  }

  return { rejectUnauthorized: false };
}
