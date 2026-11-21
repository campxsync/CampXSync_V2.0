import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway } from '@/lib/api/gateway-client';
import { IADM_SERVICES } from '@/lib/iadm/story-catalog';

export const dynamic = 'force-dynamic';

/**
 * Live health probe for IADM-01…08 via API Gateway (UI → BFF → Gateway → Java).
 * Platform admin only.
 */
export async function GET(_req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'IADM_DEMO_FORBIDDEN', detail: 'Platform admin session required' },
      { status: 403 },
    );
  }

  const results = [];
  for (const svc of IADM_SERVICES) {
    const started = Date.now();
    try {
      const res = await fetchFromGateway(svc.healthPath, { method: 'GET', requireAuth: true });
      const text = await res.text();
      let body: unknown = text;
      try {
        body = JSON.parse(text);
      } catch {
        /* keep text */
      }
      // 200 = healthy; 401 = service listening behind HMAC (same as check_stack.ps1 AcceptAuthChallenge)
      const reachable = res.ok || res.status === 401;
      results.push({
        code: svc.code,
        name: svc.name,
        port: svc.port,
        path: svc.healthPath,
        httpStatus: res.status,
        ok: reachable,
        authGated: res.status === 401,
        latencyMs: Date.now() - started,
        body,
        storyCount: svc.stories.length,
        completedStories: svc.stories.filter((s) => s.status === 'completed').length,
        deferredStories: svc.stories.filter((s) => s.status === 'DEFERRED').length,
      });
    } catch (err) {
      results.push({
        code: svc.code,
        name: svc.name,
        port: svc.port,
        path: svc.healthPath,
        httpStatus: 0,
        ok: false,
        authGated: false,
        latencyMs: Date.now() - started,
        body: { error: err instanceof Error ? err.message : 'probe failed' },
        storyCount: svc.stories.length,
        completedStories: svc.stories.filter((s) => s.status === 'completed').length,
        deferredStories: svc.stories.filter((s) => s.status === 'DEFERRED').length,
      });
    }
  }

  const up = results.filter((r) => r.ok).length;
  return NextResponse.json({
    probedAt: new Date().toISOString(),
    servicesUp: up,
    servicesTotal: results.length,
    results,
  });
}
