import type { ReactNode } from 'react';
import Link from 'next/link';
import {
  ShieldCheck,
  CalendarCheck2,
  Building2,
  GraduationCap,
  Users,
  ScrollText,
  LayoutGrid,
  ArrowRight,
  Lock,
} from 'lucide-react';

const nav = [
  { href: '#product', label: 'Product' },
  { href: '#workspaces', label: 'Workspaces' },
  { href: '#register', label: 'Register' },
  { href: '#plans', label: 'Plans' },
];

const pillars = [
  {
    title: 'One institute, many colleges',
    body: 'Each institute is its own tenant. A college administrator assigned to one college does not see another college.',
    icon: Building2,
  },
  {
    title: 'Attendance follows the timetable',
    body: 'A session belongs to a scheduled section. Faculty mark the students enrolled on that roster, then the session locks.',
    icon: CalendarCheck2,
  },
  {
    title: 'A correction stays on the ledger',
    body: 'An administrator can change a mark only with a reason. The previous value and the change are both kept.',
    icon: ScrollText,
  },
];

const workspaces = [
  { title: 'Platform', body: 'Onboards the institute, the first college, and the college administrator, then reads the governance ledger.', icon: ShieldCheck },
  { title: 'College', body: 'Keeps students, faculty, departments, batches, the timetable, attendance oversight, and corrections.', icon: Building2 },
  { title: 'Faculty', body: 'Opens the assigned timetable and marks attendance for that section only.', icon: Users },
  { title: 'Student', body: 'Sees enrolled subjects and the sessions already conducted. No other student is listed.', icon: GraduationCap },
];

const plans = [
  { name: 'Trial', detail: '30-day evaluation for one institute.', points: ['The academic workspace', 'One institute'] },
  { name: 'Standard', detail: 'A college academic office.', points: ['Up to 4 departments', 'Up to 1,000 students'] },
  { name: 'Growth', detail: 'A larger college on the same workspace.', points: ['Up to 10 departments', 'Up to 5,000 students'], featured: true },
  { name: 'Enterprise', detail: 'The plan selected by default at onboarding.', points: ['Multi-campus', 'Departments and students are not capped'] },
];

function GoToSignIn({ children, light = false }: { children: ReactNode; light?: boolean }) {
  return (
    <Link
      href="/login"
      className={`inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-full px-6 text-base font-semibold transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-[#0B1F3A] ${
        light ? 'bg-white text-[#0B1F3A] hover:bg-[#E8F0FE]' : 'bg-[#1F6FEB] text-white hover:bg-[#12315C] focus-visible:ring-[#1F6FEB] focus-visible:ring-offset-white'
      }`}
    >
      {children}
      <ArrowRight className="h-4 w-4" aria-hidden="true" />
    </Link>
  );
}

function RegisterFrame() {
  const rows = [
    ['CS201', 'Data Structures', 'A', 'Locked'],
    ['MA110', 'Calculus', 'B', 'Open'],
    ['EE204', 'Circuits', 'A', 'Open'],
  ];
  return (
    <div className="overflow-hidden rounded-2xl border border-white/15 bg-white text-[#1B2433] shadow-[0_40px_80px_-36px_rgba(0,0,0,0.45)]">
      <div className="flex h-11 items-center gap-2 border-b border-[#E3E8EF] bg-[#F6F8FB] px-4">
        <span className="h-2.5 w-2.5 rounded-full bg-[#E3E8EF]" />
        <span className="h-2.5 w-2.5 rounded-full bg-[#E3E8EF]" />
        <span className="h-2.5 w-2.5 rounded-full bg-[#E3E8EF]" />
        <span className="ml-3 text-sm text-[#3E4C63]">college / attendance</span>
      </div>
      <div className="grid min-h-[380px] sm:grid-cols-[156px_1fr]">
        <aside className="hidden bg-[#0B1F3A] p-4 text-white sm:block">
          <p className="text-sm font-bold">CampXSync</p>
          <ul className="mt-5 space-y-1 text-sm">
            {['Overview', 'Students', 'Timetable', 'Attendance', 'Audit'].map((item) => (
              <li key={item} className={`rounded-md px-2 py-2 ${item === 'Attendance' ? 'bg-white/15' : ''}`}>{item}</li>
            ))}
          </ul>
        </aside>
        <div className="p-4 sm:p-5">
          <p className="text-sm font-semibold text-[#1F6FEB]">College workspace</p>
          <h2 className="mt-1 text-xl font-bold text-[#0B1F3A]">Today&apos;s register</h2>
          <table className="mt-4 w-full text-left text-sm">
            <thead>
              <tr className="border-b border-[#E3E8EF] text-[#3E4C63]">
                {['Code', 'Subject', 'Sec', 'State'].map((heading) => (
                  <th key={heading} className="py-2 font-semibold">{heading}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row[0]} className="border-b border-[#E3E8EF]">
                  {row.map((cell) => (
                    <td key={cell} className="py-2">{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-3 text-sm text-[#3E4C63]">Sample of the college attendance screen. Live rows appear after sign-in.</p>
        </div>
      </div>
    </div>
  );
}

export function HomeLanding() {
  return (
    <div className="min-h-screen bg-[#F6F8FB] text-[#1B2433]">
      <header className="sticky top-0 z-20 border-b border-white/10 bg-[#0B1F3A] text-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-2 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#1F6FEB]">
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className="text-base font-bold">CampXSync</span>
          </Link>
          <nav className="hidden items-center gap-6 md:flex" aria-label="Page">
            {nav.map((item) => (
              <a key={item.href} href={item.href} className="cursor-pointer text-base text-[#D6DEE8] hover:text-white">{item.label}</a>
            ))}
          </nav>
          <GoToSignIn light>Sign in</GoToSignIn>
        </div>
      </header>

      <main>
        <section className="bg-[#0B1F3A] text-white">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:py-20">
            <div>
              <p className="text-base font-semibold text-[#9CC0FF]">Higher-education operations</p>
              <h1 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">Academic governance for the institute, kept inside each college.</h1>
              <p className="mt-5 text-lg leading-8 text-[#D6DEE8]">CampXSync is the multi-tenant workspace for scheduling, roster-checked attendance, and audited corrections. Platform, college, faculty, and student each open their own screen.</p>
              <div className="mt-8 flex flex-col gap-3 sm:flex-row">
                <GoToSignIn light>Sign in to your workspace</GoToSignIn>
                <a href="#workspaces" className="inline-flex h-12 cursor-pointer items-center justify-center rounded-full border border-white/30 px-6 text-base font-semibold hover:bg-white/10">See the four workspaces</a>
              </div>
            </div>
            <RegisterFrame />
          </div>
        </section>

        <section id="product" className="scroll-mt-20 mx-auto max-w-6xl px-4 py-16 sm:px-6">
          <h2 className="max-w-3xl text-3xl font-bold tracking-tight text-[#0B1F3A] sm:text-4xl">What the product actually does</h2>
          <div className="mt-10 grid gap-4 lg:grid-cols-3">
            {pillars.map((item) => (
              <article key={item.title} className="rounded-2xl border border-[#E3E8EF] bg-white p-6">
                <item.icon className="h-6 w-6 text-[#1F6FEB]" aria-hidden="true" />
                <h3 className="mt-4 text-xl font-semibold text-[#0B1F3A]">{item.title}</h3>
                <p className="mt-2 text-base leading-7">{item.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="workspaces" className="scroll-mt-20 bg-white">
          <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
            <h2 className="text-3xl font-bold tracking-tight text-[#0B1F3A] sm:text-4xl">Sign-in opens one of four workspaces</h2>
            <div className="mt-10 grid gap-4 md:grid-cols-2">
              {workspaces.map((item) => (
                <article key={item.title} className="flex gap-4 rounded-2xl border border-[#E3E8EF] p-6">
                  <item.icon className="mt-1 h-6 w-6 shrink-0 text-[#1F6FEB]" aria-hidden="true" />
                  <div>
                    <h3 className="text-xl font-semibold text-[#0B1F3A]">{item.title}</h3>
                    <p className="mt-2 text-base leading-7">{item.body}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="register" className="scroll-mt-20 bg-[#0B1F3A] text-white">
          <div className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-16 sm:px-6 lg:grid-cols-2">
            <div>
              <LayoutGrid className="h-6 w-6 text-[#9CC0FF]" aria-hidden="true" />
              <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">The register is the product.</h2>
              <ol className="mt-6 space-y-4 text-lg leading-8 text-[#D6DEE8]">
                <li>The college places the subject, section, and faculty on the timetable.</li>
                <li>Faculty mark the enrolled students for that slot.</li>
                <li>Submit locks the session. A later change needs a reason.</li>
              </ol>
            </div>
            <div className="rounded-2xl bg-white p-6 text-[#1B2433]">
              <div className="flex items-center justify-between">
                <p className="text-base font-semibold text-[#0B1F3A]">CS201 · Section A</p>
                <span className="inline-flex items-center gap-1 rounded-full bg-[#EAF8F1] px-3 py-1 text-sm font-semibold text-[#1E9E6A]">
                  <Lock className="h-3.5 w-3.5" aria-hidden="true" /> Locked
                </span>
              </div>
              <ul className="mt-4 divide-y divide-[#E3E8EF] text-base">
                {['Present', 'Absent', 'On duty'].map((status) => (
                  <li key={status} className="flex items-center justify-between py-3">
                    <span>Enrolled student</span>
                    <span className="font-semibold text-[#0B1F3A]">{status}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section id="plans" className="scroll-mt-20 mx-auto max-w-6xl px-4 py-16 sm:px-6">
          <h2 className="text-3xl font-bold tracking-tight text-[#0B1F3A] sm:text-4xl">Plans used when an institute is onboarded</h2>
          <p className="mt-3 max-w-2xl text-lg leading-8">These set capacity. CampXSync does not publish a price. The platform operator chooses the plan.</p>
          <div className="mt-10 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {plans.map((plan) => (
              <article key={plan.name} className={`flex flex-col rounded-2xl border p-6 ${plan.featured ? 'border-[#1F6FEB] bg-[#0B1F3A] text-white' : 'border-[#E3E8EF] bg-white'}`}>
                <h3 className="text-2xl font-bold">{plan.name}</h3>
                <p className={`mt-2 text-base leading-7 ${plan.featured ? 'text-[#D6DEE8]' : ''}`}>{plan.detail}</p>
                <ul className="mt-5 flex-1 space-y-2 text-base">
                  {plan.points.map((point) => <li key={point}>{point}</li>)}
                </ul>
                <div className="mt-6"><GoToSignIn light={Boolean(plan.featured)}>Sign in</GoToSignIn></div>
              </article>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t border-[#E3E8EF] bg-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p className="text-base font-semibold text-[#0B1F3A]">CampXSync</p>
          <Link href="/login" className="text-base font-semibold text-[#1F6FEB]">Sign in</Link>
        </div>
      </footer>
    </div>
  );
}
