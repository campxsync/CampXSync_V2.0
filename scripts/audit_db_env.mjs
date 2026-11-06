import fs from 'node:fs';
import path from 'node:path';

function getEnvVar(varName) {
  let val = process.env[varName];
  if (!val) {
    try {
      const envPath = path.resolve('frontend/.env.local');
      if (fs.existsSync(envPath)) {
        const content = fs.readFileSync(envPath, 'utf8');
        const match = content.match(new RegExp(`^${varName}=(.+)`, 'm'));
        if (match) {
          val = match[1].trim().replace(/^['"]|['"]$/g, '');
        }
      }
    } catch {}
  }
  return val;
}

const jdbc = getEnvVar('SUPABASE_JDBC_URL') || getEnvVar('CAMPX_JDBC_URL');
const user = getEnvVar('SUPABASE_USER') || getEnvVar('CAMPX_DB_USER');
const pass = getEnvVar('SUPABASE_PASSWORD') || getEnvVar('CAMPX_DB_PASSWORD');

console.log('=== SANITIZED DATABASE ENV VAR AUDIT ===');
console.log('JDBC URL defined:', !!jdbc);
if (jdbc) {
  const sanitizedJdbc = jdbc.replace(/:[^:@/]+@/, ':***@');
  console.log('Sanitized JDBC URL:', sanitizedJdbc);
}
console.log('User defined:', !!user);
if (user) {
  console.log('Username (sanitized):', user.split('.')[0] + '.[ref_redacted]');
  console.log('User contains ref azulbaxszsqxymqmlwfe:', user.includes('azulbaxszsqxymqmlwfe'));
}
console.log('Password defined:', !!pass);

