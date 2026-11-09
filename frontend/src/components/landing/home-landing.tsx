import Link from 'next/link';

const roles = [
  {
    code: '01',
    name: 'Platform',
    color: '#0B1F3A',
    line: 'Opens an institute, the first college, and the college administrator. Reads the governance ledger.',
  },
  {
    code: '02',
    name: 'College',
    color: '#1F6FEB',
    line: 'Keeps students, faculty, departments, batches, the timetable, and attendance corrections for one college.',
  },
  {
    code: '03',
    name: 'Faculty',
    color: '#0D9488',
    line: 'Opens the assigned timetable and marks the students enrolled on that section. Other sections stay closed.',
  },
  {
    code: '04',
    name: 'Student',
    color: '#64748B',
    line: 'Sees enrolled subjects and the sessions already conducted. No other student is listed.',
  },
];

const record = [
  { label: 'Subject', value: 'CS201 · Data Structures' },
  { label: 'Section', value: 'A · 09:00–09:50' },
  { label: 'Roster', value: '42 enrolled · 42 marked' },
  { label: 'Session', value: 'Locked' },
  { label: 'Correction', value: 'Reason required' },
];

const questions = [
  {
    q: 'What is CampXSync?',
    a: 'CampXSync is the operating record for a college. One institute is one tenant. A college administrator assigned to one college does not see another college.',
  },
  {
    q: 'How does attendance work?',
    a: 'A session belongs to a scheduled section. Faculty mark the students enrolled on that roster. When the session locks, a later change is a correction with a reason, and the previous value stays on the ledger.',
  },
  {
    q: 'Who can sign in?',
    a: 'Platform administrators, college administrators, faculty, and students each sign in to their own workspace. The home page does not show another role’s records.',
  },
  {
    q: 'Where do I sign in?',
    a: 'Use Sign in. After a successful sign-in, CampXSync sends you to the workspace for your role.',
  },
];

export function HomeLanding() {
  return (
    <div className="min-h-screen bg-[#F6F8FB] text-[#1B2433]">
      <header className="border-b border-[#E3E8EF] bg-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5 sm:px-8">
          <Link href="/" className="flex items-center gap-3 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-[#0B1F3A] text-sm font-semibold tracking-tight text-white">
              CX
            </span>
            <span className="text-lg font-semibold tracking-tight text-[#0B1F3A]">CampXSync</span>
          </Link>
          <Link
            href="/login"
            className="inline-flex h-10 items-center rounded-md bg-[#0B1F3A] px-4 text-sm font-semibold text-white hover:bg-[#12315C] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] focus-visible:ring-offset-2"
          >
            Sign in
          </Link>
        </div>
      </header>

      <main>
        <section className="mx-auto grid max-w-6xl gap-10 px-5 py-12 sm:px-8 lg:grid-cols-[minmax(0,1.15fr)_minmax(280px,0.85fr)] lg:items-end lg:py-20">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[#1F6FEB]">College operating record</p>
            <h1 className="mt-4 max-w-xl text-4xl font-semibold leading-[1.12] tracking-tight text-[#0B1F3A] sm:text-5xl">
              One college day. One timetable. One ledger.
            </h1>
            <p className="mt-5 max-w-lg text-lg leading-relaxed text-[#3E4C63]">
              CampXSync is the record a college office works from. Attendance follows the timetable. A changed mark keeps its reason. Each college stays inside its own institute.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-4">
              <Link
                href="/login"
                className="inline-flex h-12 items-center rounded-md bg-[#1F6FEB] px-5 text-base font-semibold text-white hover:bg-[#12315C] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#0B1F3A] focus-visible:ring-offset-2"
              >
                Sign in to your workspace
              </Link>
              <a href="#workspaces" className="text-sm font-semibold text-[#0B1F3A] underline decoration-[#E3E8EF] underline-offset-4 hover:decoration-[#0B1F3A]">
                Four workspaces
              </a>
            </div>
          </div>

          <aside className="bg-[#0B1F3A] p-6 text-white sm:p-8" aria-label="Session slip">
            <div className="flex items-baseline justify-between border-b border-white/15 pb-3">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#E3E8EF]">Session slip</p>
              <p className="font-mono text-xs text-[#E3E8EF]">Mon · Period 1</p>
            </div>
            <dl className="mt-1">
              {record.map((row) => (
                <div key={row.label} className="grid grid-cols-[7.5rem_1fr] gap-3 border-b border-white/10 py-3 text-sm">
                  <dt className="text-[#E3E8EF]">{row.label}</dt>
                  <dd className="font-medium">{row.value}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-4 text-xs leading-relaxed text-[#E3E8EF]">
              The slip is an example of the record faculty close. It is not a live class.
            </p>
          </aside>
        </section>

        <section id="workspaces" className="border-t border-[#E3E8EF] bg-white">
          <div className="mx-auto max-w-6xl px-5 py-14 sm:px-8">
            <h2 className="text-2xl font-semibold tracking-tight text-[#0B1F3A]">Who works in CampXSync</h2>
            <p className="mt-2 max-w-2xl text-[#3E4C63]">
              Sign-in sends each person to one workspace. A college administrator does not open another college. A student does not see the roster.
            </p>
            <ol className="mt-8 divide-y divide-[#E3E8EF] border-y border-[#E3E8EF]">
              {roles.map((role) => (
                <li key={role.code} className="grid gap-2 py-5 sm:grid-cols-[5rem_9rem_1fr] sm:items-baseline sm:gap-6">
                  <span className="font-mono text-sm text-[#6B7688]">{role.code}</span>
                  <span className="font-semibold" style={{ color: role.color }}>{role.name}</span>
                  <span className="text-[#1B2433]">{role.line}</span>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="bg-[#F6F8FB]" aria-labelledby="record-heading">
          <div className="mx-auto grid max-w-6xl gap-10 px-5 py-14 sm:px-8 lg:grid-cols-2">
            <div>
              <h2 id="record-heading" className="text-2xl font-semibold tracking-tight text-[#0B1F3A]">
                How a mark is kept
              </h2>
              <p className="mt-3 text-[#3E4C63]">
                The timetable names the section. The roster names the students. The session is the mark. A correction is a new line, not a silent edit.
              </p>
            </div>
            <ol className="space-y-0 border-l-2 border-[#0B1F3A]">
              {[
                ['Timetable', 'The section already has a day, a period, and a room.'],
                ['Roster', 'Only students enrolled on that section can be marked.'],
                ['Lock', 'Faculty close the session. The count matches the roster.'],
                ['Ledger', 'An administrator can change a mark only with a reason. Both values remain.'],
              ].map(([title, body], index) => (
                <li key={title} className="py-4 pl-5">
                  <p className="text-sm font-semibold text-[#0B1F3A]">
                    <span className="mr-2 font-mono text-[#1F6FEB]">0{index + 1}</span>
                    {title}
                  </p>
                  <p className="mt-1 text-sm text-[#3E4C63]">{body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="border-t border-[#E3E8EF] bg-white" aria-labelledby="answers-heading">
          <div className="mx-auto max-w-6xl px-5 py-14 sm:px-8">
            <h2 id="answers-heading" className="text-2xl font-semibold tracking-tight text-[#0B1F3A]">Answers</h2>
            <div className="mt-6 divide-y divide-[#E3E8EF] border-y border-[#E3E8EF]">
              {questions.map((item) => (
                <details key={item.q} className="group py-4">
                  <summary className="cursor-pointer list-none font-semibold text-[#0B1F3A] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB]">
                    {item.q}
                  </summary>
                  <p className="mt-2 max-w-3xl text-[#3E4C63]">{item.a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>
      </main>

      <footer className="bg-[#081629] text-[#E3E8EF]">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-8 text-sm sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <p>CampXSync · college operating record</p>
          <Link href="/login" className="font-semibold text-white underline-offset-4 hover:underline">
            Sign in
          </Link>
        </div>
      </footer>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            '@context': 'https://schema.org',
            '@graph': [
              {
                '@type': 'WebSite',
                name: 'CampXSync',
                description: 'College operating record for timetable attendance, role workspaces, and a correction ledger.',
              },
              {
                '@type': 'FAQPage',
                mainEntity: questions.map((item) => ({
                  '@type': 'Question',
                  name: item.q,
                  acceptedAnswer: { '@type': 'Answer', text: item.a },
                })),
              },
            ],
          }),
        }}
      />
    </div>
  );
}
