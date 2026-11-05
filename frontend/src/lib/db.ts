import { Pool } from 'pg';
import type { QueryResult, QueryResultRow } from 'pg';

let pool: Pool | null = null;

const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';
const FORBIDDEN_LEGACY_STAGING_REF = 'kpwdvvcschankgggitnj';
const AUTHORIZED_DEV_REF = 'azulbaxszsqxymqmlwfe';

export function getDbPool(): Pool {
  if (!pool) {
    const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
    const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER;
    const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD;

    if (!jdbcUrl) {
      throw new Error('Database JDBC URL is not configured. Configure SUPABASE_JDBC_URL or CAMPX_JDBC_URL.');
    }

    // Fail-fast assertions refusing forbidden production or obsolete legacy staging references
    if (jdbcUrl.includes(FORBIDDEN_PROD_REF) || (username && username.includes(FORBIDDEN_PROD_REF))) {
      throw new Error('FATAL SECURITY VIOLATION: Database connection string targets forbidden production reference!');
    }
    if (jdbcUrl.includes(FORBIDDEN_LEGACY_STAGING_REF) || (username && username.includes(FORBIDDEN_LEGACY_STAGING_REF))) {
      throw new Error('FATAL SECURITY VIOLATION: Database connection string targets obsolete legacy staging reference!');
    }

    let connString = jdbcUrl.replace(/^jdbc:/, '');
    if (connString.includes('ap-southeast-1')) {
      connString = connString.replace('ap-southeast-1', 'ap-south-1');
    }
    const urlObj = new URL(connString);
    if (username) urlObj.username = username;
    if (password) urlObj.password = password;
    urlObj.searchParams.delete('sslmode');

    if (urlObj.hostname.includes('ap-southeast-1')) {
      urlObj.hostname = urlObj.hostname.replace('ap-southeast-1', 'ap-south-1');
    }

    pool = new Pool({
      connectionString: urlObj.toString(),
      ssl: { rejectUnauthorized: false },
      max: 3,
      connectionTimeoutMillis: 10000,
      idleTimeoutMillis: 30000,
    });

    pool.on('error', (err) => {
      console.error('Unexpected error on idle database client:', err.message);
    });
  }
  return pool;
}

export async function query<T extends QueryResultRow = any>(
  text: string,
  params?: any[]
): Promise<QueryResult<T>> {
  const p = getDbPool();
  return p.query<T>(text, params);
}

export async function closeDbPool(): Promise<void> {
  if (pool) {
    await pool.end();
    pool = null;
  }
}
