// scripts/resolve_role_loader.mjs
import { pathToFileURL } from 'node:url';
import path from 'node:path';

export async function resolve(specifier, context, nextResolve) {
  if (specifier === '@/lib/db') {
    return {
      format: 'module',
      shortCircuit: true,
      url: pathToFileURL(path.resolve('scripts/mock_db.mjs')).href,
    };
  }
  if (specifier === '@/lib/auth/test-session-gate') {
    return {
      format: 'module',
      shortCircuit: true,
      url: pathToFileURL(path.resolve('scripts/mock_test_session_gate.mjs')).href,
    };
  }
  return nextResolve(specifier, context);
}
