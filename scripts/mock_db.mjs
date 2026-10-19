// scripts/mock_db.mjs
// In-memory mock database query handler for unit regression testing of resolveUserRole

let mockHandler = null;

export function setMockQueryHandler(fn) {
  mockHandler = fn;
}

export async function query(sql, params) {
  if (mockHandler) {
    return mockHandler(sql, params);
  }
  return { rows: [] };
}
