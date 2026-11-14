import fs from 'fs';
import path from 'path';

export type AuditPersona = 'platform_admin' | 'college_admin' | 'faculty' | 'student';

const PERSONA_ENV: Record<AuditPersona, { emailKey: string; passwordKey: string }> = {
  platform_admin: { emailKey: 'PLATFORM_ADMIN_EMAIL', passwordKey: 'PLATFORM_ADMIN_PW' },
  college_admin: { emailKey: 'COLLEGE_ADMIN_EMAIL', passwordKey: 'COLLEGE_ADMIN_PW' },
  faculty: { emailKey: 'FACULTY_EMAIL', passwordKey: 'FACULTY_PW' },
  student: { emailKey: 'STUDENT1_EMAIL', passwordKey: 'STUDENT1_PW' },
};

/**
 * Loads persona email/password from process.env or repo-root `.env.audit`.
 * Never logs or returns secrets in error messages.
 */
export function loadAuditPersonaCredentials(persona: AuditPersona): { email: string; password: string } | null {
  const keys = PERSONA_ENV[persona];
  let email = process.env[keys.emailKey];
  let password = process.env[keys.passwordKey];

  if (!email || !password) {
    try {
      const auditPath = path.resolve(process.cwd(), '..', '.env.audit');
      const altPath = path.resolve(process.cwd(), '.env.audit');
      const filePath = fs.existsSync(auditPath) ? auditPath : altPath;
      if (fs.existsSync(filePath)) {
        const raw = fs.readFileSync(filePath, 'utf8');
        for (const line of raw.split(/\r?\n/)) {
          const trimmed = line.trim();
          if (!trimmed || trimmed.startsWith('#')) continue;
          const idx = trimmed.indexOf('=');
          if (idx <= 0) continue;
          const k = trimmed.slice(0, idx).trim();
          const v = trimmed.slice(idx + 1).trim();
          if (k === keys.emailKey) email = v;
          if (k === keys.passwordKey) password = v;
        }
      }
    } catch {
      return null;
    }
  }

  if (!email || !password) return null;
  return { email, password };
}
