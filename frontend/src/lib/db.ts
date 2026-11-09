import { Pool } from 'pg';
import type { PoolClient, QueryResult, QueryResultRow } from 'pg';
import { resolveDbRoleOptions } from './db-role';
import { resolveDbSsl } from './db-ssl';

let pool: Pool | null = null;

const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';

export function getDbPool(): Pool {
  if (!pool) {
    const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
    const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER;
    const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD;

    if (!jdbcUrl) {
      throw new Error('Database JDBC URL is not configured. Configure SUPABASE_JDBC_URL or CAMPX_JDBC_URL.');
    }

    // Master Prompt Rule 1: Fail-fast assertion refusing forbidden production reference
    if (jdbcUrl.includes(FORBIDDEN_PROD_REF) || (username && username.includes(FORBIDDEN_PROD_REF))) {
      throw new Error('FATAL SECURITY VIOLATION: Database connection string targets forbidden production reference!');
    }

    let connString = jdbcUrl.replace(/^jdbc:/, '');
    const urlObj = new URL(connString);
    if (username) urlObj.username = username;
    if (password) urlObj.password = password;
    urlObj.searchParams.delete('sslmode');

    const roleOptions = resolveDbRoleOptions(process.env);

    pool = new Pool({
      connectionString: urlObj.toString(),
      ssl: resolveDbSsl(process.env),
      ...(roleOptions !== undefined ? { options: roleOptions } : {}),
      max: 4,
      idleTimeoutMillis: 10000,
      connectionTimeoutMillis: 10000,
      keepAlive: true,
    });

    pool.on('error', (err) => {
      console.error('Unexpected error on idle database client:', err.message);
    });
  }
  return pool;
}

type PreparedClient = PoolClient & { __campxRole?: string };

/**
 * The shared pooler drops libpq startup options, so the validated
 * application role is applied on the session itself.
 */
async function prepareClient(client: PoolClient): Promise<void> {
  const roleOptions = resolveDbRoleOptions(process.env);
  if (!roleOptions) return;

  const role = roleOptions.slice('-c role='.length);
  const prepared = client as PreparedClient;
  if (prepared.__campxRole === role) return;

  await client.query(`SET ROLE ${role}`);
  prepared.__campxRole = role;
}

function isDeadConnection(err: unknown): boolean {
  const message = err instanceof Error ? err.message : String(err);
  return /ECONNRESET|EMAXCONNSESSION|ETIMEDOUT|ENOTFOUND|socket disconnected|Connection terminated|Client has encountered a connection error/i.test(message);
}

async function withClient<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  let lastError: unknown;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const client = await getDbPool().connect();
    try {
      await prepareClient(client);
      const result = await fn(client);
      client.release();
      return result;
    } catch (err) {
      lastError = err;
      client.release(err as Error);
      if (!isDeadConnection(err) || attempt === 1) throw err;
    }
  }
  throw lastError;
}

export async function query<T extends QueryResultRow = any>(
  text: string,
  params?: any[]
): Promise<QueryResult<T>> {
  return withClient((client) => client.query<T>(text, params));
}

/** Runs every statement on one pooled connection. Pages use this instead of opening one session per query. */
export async function queryAll(
  jobs: Array<{ text: string; params?: any[] }>
): Promise<QueryResult[]> {
  return withClient(async (client) => {
    const results: QueryResult[] = [];
    for (const job of jobs) {
      results.push(await client.query(job.text, job.params));
    }
    return results;
  });
}

export async function withTransaction<T>(
  fn: (client: PoolClient) => Promise<T>
): Promise<T> {
  const client = await getDbPool().connect();
  try {
    await prepareClient(client);
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    try {
      await client.query('ROLLBACK');
    } catch {
      // The connection already failed. The original error is the one to surface.
    }
    throw err;
  } finally {
    client.release();
  }
}

export async function closeDbPool(): Promise<void> {
  if (pool) {
    await pool.end();
    pool = null;
  }
}
