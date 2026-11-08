import React from 'react';

const HUES = ['#2563eb', '#7c3aed', '#db2777', '#0891b2', '#f97316', '#4f46e5', '#0d9488', '#e11d48'];

const PATHS: Record<string, string> = {
  students: 'M12 12a3.2 3.2 0 1 0-3.2-3.2A3.2 3.2 0 0 0 12 12zm-6.2 7.2a6.2 6.2 0 0 1 12.4 0',
  faculty: 'M4 18V8l8-4 8 4v10M8 18v-4h8v4',
  administrators: 'M12 3l7 3v6c0 4.2-2.8 7.2-7 9-4.2-1.8-7-4.8-7-9V6z',
  parents: 'M12 19s-6-3.6-6-8a3.4 3.4 0 0 1 6-2 3.4 3.4 0 0 1 6 2c0 4.4-6 8-6 8z',
  management: 'M4 18h16M6 18V9h4v9M14 18V6h4v12',
  alumni: 'M12 3l2.2 4.6 5 .7-3.6 3.5.9 5L12 14.8 7.5 16.8l.9-5L4.8 8.3l5-.7z',
  partners: 'M8 12h8M7 8h4l2 4-2 4H7M17 8h-4',
  agents: 'M12 3l1.4 4.2L18 8.6l-4.2 1.4L12 14l-1.4-4L6.4 8.6 10.6 7.2zM18 15l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z',
  web: 'M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm0 0c2 2.2 3 4.8 3 8s-1 5.8-3 8c-2-2.2-3-4.8-3-8s1-5.8 3-8zM4 12h16',
  mobile: 'M9 4h6a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm3 13h.1',
  chat: 'M5 6h14v9H8l-3 3z',
  email: 'M4 7h16v10H4zm0 0l8 6 8-6',
  sms: 'M6 6h12v8H9l-3 3z',
  whatsapp: 'M6 18l1.2-3A6.5 6.5 0 1 1 9.2 17z',
  push: 'M12 4a5 5 0 0 1 5 5v2.5l1 2H6l1-2V9a5 5 0 0 1 5-5zm-2 12a2 2 0 0 0 4 0',
  api: 'M8 8l-3 4 3 4M16 8l3 4-3 4M13 6l-2 12',
  academic: 'M3 10l9-5 9 5-9 5zm3 3v4c2 1.4 4 2 6 2s4-.6 6-2v-4',
  examination: 'M7 4h10v16H7zm2 4h6M9 12h6M9 16h4',
  lms: 'M5 5h10a3 3 0 0 1 3 3v12H8a3 3 0 0 0-3 3z',
  finance: 'M5 8h14v9H5zm0 3h14M8 14h2',
  hr: 'M8 11a2.4 2.4 0 1 0-2.4-2.4A2.4 2.4 0 0 0 8 11zm8 0a2.4 2.4 0 1 0-2.4-2.4A2.4 2.4 0 0 0 16 11zM4 18a4 4 0 0 1 8 0M13 18a3.5 3.5 0 0 1 7 0',
  library: 'M6 5h3v14H6zm5 0h3v14h-3zm5 2h3v12h-3z',
  hostel: 'M4 19V9l8-4 8 4v10M10 19v-5h4v5',
  placement: 'M8 8V6h8v2M6 8h12v10H6z',
  transport: 'M5 16h14l-1-6H6zm1 0v2m12-2v2M8 13h.1M16 13h.1',
  facilities: 'M4 19V9h6v10M10 19V5h10v14M13 9h4M13 13h4',
  communication: 'M5 9a7 7 0 0 1 14 0c0 4-3 6-3 6H8s-3-2-3-6zm4 8a3 3 0 0 0 6 0',
  context: 'M12 4v16M4 12h16M7 7l10 10M17 7L7 17',
  graph: 'M7 16a2 2 0 1 0-2-2 2 2 0 0 0 2 2zm10-8a2 2 0 1 0-2-2 2 2 0 0 0 2 2zm0 12a2 2 0 1 0-2-2 2 2 0 0 0 2 2zM8.5 14.5l7-6M8.5 13.5l7 5',
  decision: 'M12 4l7 4v5c0 4-3 6.5-7 8-4-1.5-7-4-7-8V8z',
  insight: 'M12 4a5 5 0 0 1 2 9.6V16h-4v-2.4A5 5 0 0 1 12 4zm-2 14h4',
  action: 'M8 6h8l-2 5h4l-8 8 2-6H6z',
  notification: 'M6 16h12l-1.2-2.2A6 6 0 0 1 8 9V8a4 4 0 0 1 8 0v1',
  workflow: 'M6 7h4v4H6zm8 0h4v4h-4zM8 11v3h8v-3',
  audit: 'M8 4h8v16H8zm2 4h4m-4 3h4m-4 3h3',
  unified: 'M5 12a7 7 0 0 1 14 0M8 12h8',
  automation: 'M12 4v3m0 10v3M4 12h3m10 0h3M6.5 6.5l2 2m7 7 2 2m0-11-2 2m-7 7-2 2',
  insights: 'M5 16l4-5 3 3 6-7',
  secure: 'M8 11V8a4 4 0 0 1 8 0v3M7 11h10v8H7z',
  scale: 'M4 16l4-8 4 5 3-3 5 6',
  payment: 'M4 8h16v8H4zm0 3h16M7 14h3',
  government: 'M4 18h16M6 18V10h12v8M12 4l8 5H4z',
  providers: 'M5 8h14v8H5zm0 0l7 5 7-5',
  bi: 'M6 16V10m6 6V6m6 10v-4',
  systems: 'M5 7h6v6H5zm8 0h6v4h-6zM5 15h6v2H5zm8-2h6v4h-6z',
};

export function Mark({ size = 36 }: { size?: number }) {
  return (
    <span className="cx-logo" style={{ width: size, height: size }} aria-hidden="true">
      C<i>X</i>
    </span>
  );
}

export function Glyph({ id, label }: { id: string; label?: string }) {
  const hue = HUES[Math.abs(hash(id)) % HUES.length];
  const d = PATHS[id] ?? PATHS.academic;
  return (
    <svg className="cx-glyph" viewBox="0 0 40 40" role="img" aria-label={label}>
      <rect width="40" height="40" rx="13" fill={hue} />
      <path d={d} fill="none" stroke="#fff" strokeWidth="1.7" strokeLinejoin="round" strokeLinecap="round" transform="translate(8 8)" />
    </svg>
  );
}

function hash(value: string) {
  return [...value].reduce((sum, char) => sum + char.charCodeAt(0), 0);
}
