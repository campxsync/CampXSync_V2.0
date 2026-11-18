import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { decidePortalDashboardAccess } from './portal-dashboard-access.ts';

describe('decidePortalDashboardAccess', () => {
  it('stm10En002_unauthenticatedDenied', () => {
    const d = decidePortalDashboardAccess({ authenticated: false });
    assert.equal(d.allow, false);
    if (!d.allow) assert.equal(d.status, 401);
  });

  it('stm10En002_wrongRoleDenied', () => {
    const d = decidePortalDashboardAccess({
      authenticated: true,
      role: 'faculty',
      tenantId: 't1',
      userId: 'u1',
    });
    assert.equal(d.allow, false);
    if (!d.allow) assert.equal(d.status, 403);
  });

  it('stm10En002_clientStudentIdIdorDenied', () => {
    const d = decidePortalDashboardAccess({
      authenticated: true,
      role: 'student',
      tenantId: 't1',
      userId: 'u1',
      clientStudentId: 'other-student',
    });
    assert.equal(d.allow, false);
    if (!d.allow) {
      assert.equal(d.status, 403);
      assert.match(d.detail, /must not be supplied/i);
    }
  });

  it('stm10En002_studentAllowedWithoutClientStudentId', () => {
    const d = decidePortalDashboardAccess({
      authenticated: true,
      role: 'student',
      tenantId: 't1',
      userId: 'u1',
    });
    assert.equal(d.allow, true);
  });
});
