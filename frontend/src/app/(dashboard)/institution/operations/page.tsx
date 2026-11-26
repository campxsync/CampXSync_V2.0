'use client';

import { useEffect, useState } from 'react';

type Row = Record<string, unknown>;

function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 86400000).toISOString();
}

/**
 * IADM-08 Institution Operations desk (R03).
 * Plain language: own-institute audit activity only; no service health.
 */
export default function InstitutionOperationsPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [healthDenied, setHealthDenied] = useState<string | null>(null);
  const [from] = useState(daysAgoIso(14));
  const [to] = useState(new Date().toISOString());

  useEffect(() => {
    void (async () => {
      try {
        const q = new URLSearchParams({ view: 'audit-search', from, to });
        const res = await fetch(`/api/institution/operations?${q.toString()}`);
        const body = await res.json();
        if (!res.ok) {
          setError(body.detail || 'Unable to load institute activity');
          return;
        }
        setRows(Array.isArray(body.items) ? body.items : []);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Load failed');
      }
      const h = await fetch('/api/institution/operations?view=health-overview');
      const hb = await h.json();
      if (h.status === 403) {
        setHealthDenied(hb.detail || 'Health denied for institution desk');
      }
    })();
  }, [from, to]);

  return (
    <main className="mx-auto max-w-5xl p-6" data-testid="iadm08-institution-desk">
      <h1 className="text-2xl font-semibold" data-testid="iadm08-institution-title">
        My Institute Activity
      </h1>
      <p className="mt-1 text-sm text-neutral-600">
        Own-institute audit activity only. Platform service health is not available here.
      </p>
      {error && (
        <p className="mt-4 text-red-700" data-testid="iadm08-institution-error">
          {error}
        </p>
      )}
      {healthDenied && (
        <p className="mt-2 text-sm text-amber-800" data-testid="iadm08-health-denied">
          {healthDenied}
        </p>
      )}
      <table className="mt-4 w-full text-left text-sm" data-testid="iadm08-institute-audit">
        <thead>
          <tr className="border-b">
            <th className="py-2">Time</th>
            <th>Action</th>
            <th>Outcome</th>
            <th>Institute</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={String(row.eventId)} className="border-b border-neutral-100" data-testid={`iadm08-inst-row-${row.eventId}`}>
              <td className="py-2">{String(row.eventTime || '')}</td>
              <td>{String(row.action || '')}</td>
              <td>{String(row.outcome || '')}</td>
              <td>{String(row.institute || '')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  );
}
