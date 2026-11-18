/**
 * STM-10-EN-002 unit tests for portal dashboard aggregation.
 * Run: node --experimental-strip-types --test src/lib/portal/dashboard-aggregator.test.ts
 * (from frontend/)
 */
import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  aggregateStudentPortalDashboard,
  clearPortalDashboardCache,
  mapLifecycleHistoryToActivities,
  mapProfileHistoryToActivities,
  mergeRecentPortalActivities,
  sanitizeGatewayError,
  type AggregateFetchResult,
  type AggregateFetcher,
} from './dashboard-aggregator.ts';

function jsonResult(status: number, body: unknown): AggregateFetchResult {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: body,
    text: JSON.stringify(body),
  };
}

describe('sanitizeGatewayError', () => {
  it('stm10En002_neverExposesInternalBodyText', () => {
    const err = sanitizeGatewayError(500, 'SQLException at line 42 stack=...');
    assert.equal(err.code, 'SERVICE_UNAVAILABLE');
    assert.equal(
      err.message,
      'This section is temporarily unavailable. Other sections may still work.'
    );
    assert.equal(err.message.includes('SQLException'), false);
  });

  it('stm10En002_unauthenticatedPlainMessage', () => {
    const err = sanitizeGatewayError(401);
    assert.equal(err.code, 'UNAUTHENTICATED');
    assert.match(err.message, /Sign-in/i);
  });
});

describe('aggregateStudentPortalDashboard', () => {
  beforeEach(() => {
    clearPortalDashboardCache();
  });

  it('stm10En002_partialFailureWhenOneServiceDown', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) {
        return jsonResult(200, { id: 'stu-own', firstName: 'Asha' });
      }
      if (url.includes('/documents/submissions')) {
        return { ok: false, status: 0, networkError: true };
      }
      if (url.includes('/link-status')) {
        return jsonResult(200, { linkStatus: 'LINKED', portalAccessActive: true });
      }
      if (url.includes('/portal-preferences')) {
        return jsonResult(200, { version: 1, widgets: { order: [], hidden: [] } });
      }
      if (url.includes('/enrollment/enrolments')) {
        return jsonResult(200, [{ id: 'enr-1' }]);
      }
      if (url.includes('/lifecycle/')) {
        return jsonResult(200, { status: 'ACTIVE' });
      }
      if (url.includes('/admissions/applications')) {
        return jsonResult(200, []);
      }
      if (url.includes('/attendance/summaries/student/')) {
        return jsonResult(200, { overallPercentage: 90 });
      }
      return jsonResult(404, { title: 'missing' });
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.sections.profile.status, 'ok');
    assert.equal(agg.sections.documents.status, 'failed');
    assert.equal(agg.sections.documents.error?.code, 'NETWORK');
    assert.equal(agg.sections.enrollment.status, 'ok');
    assert.ok(agg.summary.loaded.includes('enrollment'));
    assert.ok(agg.summary.failed.some((f) => f.section === 'documents'));
    assert.match(
      agg.summary.failed.find((f) => f.section === 'documents')!.reason,
      /Could not reach/i
    );
    // Failed section must not leak another student's data
    assert.equal(agg.sections.documents.data, undefined);
  });

  it('stm10En002_timeoutMarksSectionFailedOthersContinue', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) {
        return jsonResult(200, { id: 'stu-own' });
      }
      if (url.includes('/enrollment/enrolments')) {
        return { ok: false, status: 408, timedOut: true };
      }
      if (url.includes('/link-status')) return jsonResult(200, { linkStatus: 'LINKED' });
      if (url.includes('/portal-preferences')) return jsonResult(200, { version: 1 });
      if (url.includes('/documents/submissions')) return jsonResult(200, []);
      if (url.includes('/lifecycle/')) return jsonResult(200, { status: 'ACTIVE' });
      if (url.includes('/admissions/applications')) return jsonResult(200, []);
      if (url.includes('/attendance/summaries/student/')) return jsonResult(200, {});
      return jsonResult(500, { detail: 'boom' });
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.sections.enrollment.status, 'failed');
    assert.equal(agg.sections.enrollment.error?.code, 'TIMEOUT');
    assert.equal(agg.sections.linkStatus.status, 'ok');
    assert.equal(agg.sections.preferences.status, 'ok');
  });

  it('stm10En002_shortLivedCacheHit', async () => {
    let calls = 0;
    const fetcher: AggregateFetcher = async (url) => {
      calls += 1;
      if (url.includes('/students/me')) return jsonResult(200, { id: 'stu-own' });
      return jsonResult(200, { ok: true });
    };

    const opts = {
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      cacheKey: 'tenant-a:user-a',
      cacheTtlMs: 60_000,
    };

    const first = await aggregateStudentPortalDashboard(opts);
    const second = await aggregateStudentPortalDashboard(opts);
    assert.equal(first.cacheHit, false);
    assert.equal(second.cacheHit, true);
    assert.ok(calls < 20, `expected cache to reduce fan-out calls, got ${calls}`);
  });

  it('stm10En002_bypassCacheOnRefresh', async () => {
    let meCalls = 0;
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) {
        meCalls += 1;
        return jsonResult(200, { id: 'stu-own' });
      }
      return jsonResult(200, {});
    };

    await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      cacheKey: 'tenant-a:user-b',
    });
    await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      cacheKey: 'tenant-a:user-b',
      bypassCache: true,
    });
    assert.equal(meCalls, 2);
  });

  it('stm10En002_profileFailureSkipsDependentSections', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) {
        return { ok: false, status: 503, text: 'upstream down' };
      }
      // Must never be called for other student resources without profile
      assert.fail(`unexpected call without profile: ${url}`);
      return jsonResult(500, {});
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.studentId, null);
    assert.equal(agg.sections.profile.status, 'failed');
    assert.equal(agg.sections.enrollment.status, 'failed');
    assert.equal(agg.sections.enrollment.error?.code, 'PROFILE_UNAVAILABLE');
    assert.equal(agg.sections.attendance.data, undefined);
  });

  it('stm10En002_onlyOwnStudentIdUsedInDownstreamUrls', async () => {
    const seen: string[] = [];
    const fetcher: AggregateFetcher = async (url) => {
      seen.push(url);
      if (url.includes('/students/me')) {
        return jsonResult(200, { id: 'stu-OWN-001' });
      }
      return jsonResult(200, { ok: true });
    };

    await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    const foreign = seen.filter((u) => /stu-(?!OWN-001)/.test(u) && u.includes('stu-'));
    assert.equal(foreign.length, 0);
    assert.ok(seen.some((u) => u.includes('/students/stu-OWN-001/link-status')));
    assert.ok(
      seen.some((u) => u.includes('/attendance/summaries/student/stu-OWN-001'))
    );
  });

  it('stm10En002_fiveHundredDoesNotLeakDetail', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) return jsonResult(200, { id: 'stu-own' });
      if (url.includes('/lifecycle/')) {
        return {
          ok: false,
          status: 500,
          text: '{"detail":"Connection refused to 10.0.0.5:5432 password=secret"}',
        };
      }
      return jsonResult(200, {});
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.sections.lifecycle.status, 'failed');
    assert.equal(agg.sections.lifecycle.error?.code, 'SERVICE_UNAVAILABLE');
    assert.equal(JSON.stringify(agg).includes('password'), false);
    assert.equal(JSON.stringify(agg).includes('10.0.0.5'), false);
  });

  it('stm10Us018_oneServiceOutageLeavesOthersUsable', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) return jsonResult(200, { id: 'stu-own' });
      if (url.includes('/documents/submissions')) {
        return { ok: false, status: 503, text: 'documents down' };
      }
      if (url.includes('/link-status')) return jsonResult(200, { linkStatus: 'LINKED' });
      if (url.includes('/portal-preferences')) return jsonResult(200, { version: 1 });
      if (url.includes('/enrollment/enrolments')) return jsonResult(200, [{ id: 'e1' }]);
      if (url.includes('/lifecycle/')) return jsonResult(200, { status: 'ACTIVE' });
      if (url.includes('/admissions/applications')) return jsonResult(200, []);
      if (url.includes('/attendance/summaries/student/')) {
        return jsonResult(200, { overallPercentage: 88 });
      }
      return jsonResult(404, {});
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.sections.documents.status, 'failed');
    assert.match(agg.sections.documents.error!.message, /temporarily unavailable|could not be loaded/i);
    assert.ok(agg.summary.loaded.includes('enrollment'));
    assert.ok(agg.summary.loaded.includes('attendance'));
    assert.ok(agg.summary.failed.some((f) => f.section === 'documents'));
    // Outage must not surface another student's payload
    assert.equal(agg.sections.documents.data, undefined);
    assert.equal(JSON.stringify(agg).includes('documents down'), false);
  });

  it('stm10Us018_retryAfterOutageRecoversSection', async () => {
    let documentsDown = true;
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) return jsonResult(200, { id: 'stu-own' });
      if (url.includes('/documents/submissions')) {
        if (documentsDown) return { ok: false, status: 503, text: 'down' };
        return jsonResult(200, [{ id: 'doc-1', name: 'ID card' }]);
      }
      return jsonResult(200, { ok: true });
    };

    const first = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      cacheKey: 'tenant:retry-user',
      bypassCache: true,
    });
    assert.equal(first.sections.documents.status, 'failed');

    documentsDown = false;
    const second = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      cacheKey: 'tenant:retry-user',
      bypassCache: true,
    });
    assert.equal(second.sections.documents.status, 'ok');
    assert.equal((second.sections.documents.data as { id: string }[])[0].id, 'doc-1');
    assert.equal(second.summary.failed.length, 0);
  });

  it('stm10Us015_activitiesFromOwnHistoryOnly', async () => {
    const fetcher: AggregateFetcher = async (url) => {
      if (url.includes('/students/me')) return jsonResult(200, { id: 'stu-own' });
      // Match lifecycle history before the shorter students/.../history suffix.
      if (url.includes('/lifecycle/students/stu-own/history')) {
        return jsonResult(200, [
          {
            id: 'l1',
            studentId: 'stu-own',
            action: 'STATUS_CHANGED',
            timestamp: '2026-11-18T10:00:00Z',
            summary: 'Lifecycle bump',
          },
        ]);
      }
      if (url.includes('/api/v1/students/stu-own/history')) {
        return jsonResult(200, [
          {
            id: 'h1',
            studentId: 'stu-own',
            action: 'PROFILE_UPDATED',
            timestamp: '2026-11-18T08:00:00Z',
            summary: 'Own change',
          },
          {
            id: 'h2',
            studentId: 'stu-other',
            action: 'PROFILE_UPDATED',
            timestamp: '2026-11-18T09:00:00Z',
            summary: 'Other student leak',
          },
        ]);
      }
      return jsonResult(200, { ok: true });
    };

    const agg = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: 'http://gateway.test',
      headers: { Authorization: 'Bearer t' },
      fetcher,
      bypassCache: true,
    });

    assert.equal(agg.sections.activities.status, 'ok');
    const data = agg.sections.activities.data as {
      items: Array<{ id: string; studentId: string }>;
      studentId: string;
    };
    assert.equal(data.studentId, 'stu-own');
    assert.ok(data.items.some((i) => i.id === 'h1'));
    assert.ok(data.items.some((i) => i.id === 'l1'));
    assert.equal(
      data.items.some((i) => i.id === 'h2' || i.studentId === 'stu-other'),
      false
    );
  });
});

describe('STM-10-US-015 activity mappers', () => {
  it('stm10Us015_mapsOwnProfileHistoryOnly', () => {
    const items = mapProfileHistoryToActivities(
      [
        {
          id: 'h1',
          studentId: 'stu-own',
          action: 'PROFILE_UPDATED',
          timestamp: '2026-11-18T08:00:00Z',
          summary: 'Name corrected',
        },
        {
          id: 'h2',
          studentId: 'stu-other',
          action: 'PROFILE_UPDATED',
          timestamp: '2026-11-18T09:00:00Z',
          summary: 'Should drop',
        },
      ],
      'stu-own'
    );
    assert.equal(items.length, 1);
    assert.equal(items[0].id, 'h1');
  });

  it('stm10Us015_mergesAndSortsNewestFirst', () => {
    const merged = mergeRecentPortalActivities([
      ...mapProfileHistoryToActivities(
        [
          {
            id: 'p1',
            studentId: 'stu-own',
            action: 'PROFILE_UPDATED',
            timestamp: '2026-11-17T10:00:00Z',
            summary: 'Older',
          },
        ],
        'stu-own'
      ),
      ...mapLifecycleHistoryToActivities(
        [
          {
            id: 'l1',
            studentId: 'stu-own',
            action: 'STATUS_CHANGED',
            timestamp: '2026-11-18T12:00:00Z',
            summary: 'Newer',
          },
        ],
        'stu-own'
      ),
    ]);
    assert.equal(merged[0].id, 'l1');
    assert.equal(merged[1].id, 'p1');
  });
});
