import assert from 'node:assert/strict';
import test from 'node:test';
// Node's type stripper only resolves this import with a .ts extension.
// @ts-expect-error TS5097 allowImportingTsExtensions is disabled for the Next.js build.
import { resolveDbRoleOptions } from './db-role.ts';

test('valid role becomes a startup options string', () => {
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: 'campx_app' }), '-c role=campx_app');
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: 'postgres' }), '-c role=postgres');
  assert.equal(
    resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: 'a'.repeat(63), CAMPX_DB_REQUIRE_APP_ROLE: 'true' }),
    `-c role=${'a'.repeat(63)}`,
  );
});

test('invalid role is rejected', () => {
  const invalid = [
    'Campx',
    'campx-app',
    '1role',
    'role name',
    'role;select',
    '"campx"',
    'campx.app',
    '-c role=other',
    ` ${'a'.repeat(62)}`,
    'a'.repeat(64),
  ];

  for (const role of invalid) {
    assert.throws(
      () => resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: role }),
      /CAMPX_DB_APP_ROLE must match/,
    );
  }
});

test('require without a role throws', () => {
  assert.throws(
    () => resolveDbRoleOptions({ CAMPX_DB_REQUIRE_APP_ROLE: 'true' }),
    /CAMPX_DB_REQUIRE_APP_ROLE=true requires CAMPX_DB_APP_ROLE/,
  );
  assert.throws(
    () => resolveDbRoleOptions({ CAMPX_DB_REQUIRE_APP_ROLE: 'true', CAMPX_DB_APP_ROLE: '' }),
    /CAMPX_DB_REQUIRE_APP_ROLE=true requires CAMPX_DB_APP_ROLE/,
  );
  assert.throws(
    () => resolveDbRoleOptions({ CAMPX_DB_REQUIRE_APP_ROLE: 'true', CAMPX_DB_APP_ROLE: '   ' }),
    /CAMPX_DB_REQUIRE_APP_ROLE=true requires CAMPX_DB_APP_ROLE/,
  );
});

test('unset role returns undefined', () => {
  assert.equal(resolveDbRoleOptions({}), undefined);
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: '' }), undefined);
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_APP_ROLE: '   ' }), undefined);
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_REQUIRE_APP_ROLE: 'false' }), undefined);
  assert.equal(resolveDbRoleOptions({ CAMPX_DB_REQUIRE_APP_ROLE: 'TRUE' }), undefined);
});
