'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  ArrowDown,
  Building2,
  Check,
  GraduationCap,
  Landmark,
  Menu,
  Presentation,
  X,
} from 'lucide-react';

type ChapterId = 'student' | 'faculty' | 'college' | 'university';
type Attendance = 'present' | 'absent' | 'late';

const CHAPTERS: {
  id: ChapterId;
  label: string;
  hint: string;
  band: string;
  ink: string;
}[] = [
  { id: 'student', label: 'Student', hint: 'Morning timetable', band: '#06b6d4', ink: '#042f35' },
  { id: 'faculty', label: 'Faculty', hint: 'Live roster', band: '#7c3aed', ink: '#2e1065' },
  { id: 'college', label: 'College', hint: 'The whole day', band: '#2563eb', ink: '#172554' },
  { id: 'university', label: 'University', hint: 'The ledger', band: '#f43f5e', ink: '#4c0519' },
];

const CLASSES = [
  { id: 'ds', time: '09:00 AM', title: 'Distributed Systems', place: 'Hall C', status: 'Completed' },
  { id: 'aa', time: '11:30 AM', title: 'Advanced Algorithms', place: 'Lecture Hall B · Room 204', status: 'Live now' },
  { id: 'id', time: '02:15 PM', title: 'Interaction Design Lab', place: 'Studio 4', status: 'Upcoming' },
];

const PILLARS = [
  { id: 'curriculum', title: 'Curriculum', copy: 'Degree maps, prerequisite paths, and elective catalog controls.' },
  { id: 'timetable', title: 'Timetable', copy: 'Resource assignment, room locks, and collision checks.' },
  { id: 'exams', title: 'Examinations', copy: 'Seating plans, invigilation windows, and grade seals.' },
  { id: 'governance', title: 'Governance', copy: 'Workload, resolutions, and compliance on one desk.' },
];

const COLLEGES = [
  { id: 'eng', node: 'Node 01', title: 'School of Engineering' },
  { id: 'arts', node: 'Node 02', title: 'College of Arts & Sciences' },
  { id: 'med', node: 'Node 03', title: 'School of Medicine' },
  { id: 'arch', node: 'Node 04', title: 'College of Architecture' },
];

const TICKER =
  'Student timetable  ·  Faculty roster  ·  College schedule  ·  University ledger  ·  Same day, four desks  ·  ';

export function CampXSyncStoryLanding() {
  const [active, setActive] = useState<ChapterId>('student');
  const [beat, setBeat] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedClass, setSelectedClass] = useState('aa');
  const [attendance, setAttendance] = useState<Attendance>('present');
  const [pillar, setPillar] = useState('timetable');
  const [college, setCollege] = useState('eng');
  const [demoOpen, setDemoOpen] = useState(false);
  const [demoSent, setDemoSent] = useState(false);
  const [spot, setSpot] = useState({ x: 50, y: 30 });

  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) return;
    const timer = window.setInterval(() => setBeat((value) => (value + 1) % CHAPTERS.length), 2400);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const sections = CHAPTERS.map((chapter) => document.getElementById(chapter.id)).filter(
      (node): node is HTMLElement => Boolean(node),
    );
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        const id = visible?.target.id as ChapterId | undefined;
        if (id) setActive(id);
      },
      { rootMargin: '-35% 0px -45% 0px', threshold: [0.2, 0.45, 0.7] },
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const nodes = document.querySelectorAll('[data-reveal]');
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) entry.target.classList.add('is-in');
        });
      },
      { threshold: 0.16 },
    );
    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, []);

  const spoken = CHAPTERS[beat];
  const openDemo = () => {
    setDemoSent(false);
    setDemoOpen(true);
  };

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-[#f6f7fb] text-[#111827]">
      <StoryStyles />
      <div className="overflow-hidden bg-[#0b1020] text-white">
        <div className="story-marquee flex w-max gap-0 py-2 text-[12px] font-semibold uppercase tracking-[0.18em]">
          <span className="px-4">{TICKER.repeat(2)}</span>
          <span className="px-4" aria-hidden="true">
            {TICKER.repeat(2)}
          </span>
        </div>
      </div>

      <header className="sticky top-0 z-50 border-b border-black/5 bg-white/90 backdrop-blur-md">
        <div className="mx-auto flex h-[4.5rem] max-w-[1440px] items-center justify-between px-4 sm:px-6 lg:px-12">
          <a href="#top" className="flex items-center gap-3 rounded-lg">
            <BrandMark />
            <span className="story-display text-xl font-bold tracking-tight">CampXSync</span>
          </a>
          <nav className="hidden items-center gap-7 md:flex" aria-label="Story">
            {CHAPTERS.map((chapter) => (
              <a
                key={chapter.id}
                href={`#${chapter.id}`}
                className="text-sm font-semibold transition-colors"
                style={{ color: active === chapter.id ? chapter.band : '#64748b' }}
              >
                {chapter.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2 sm:gap-3">
            <Link
              href="/login"
              className="hidden min-h-11 items-center rounded-full border border-slate-200 px-4 text-sm font-semibold text-slate-700 hover:border-slate-900 hover:text-slate-950 sm:inline-flex"
            >
              Workspace Sign In
            </Link>
            <button type="button" onClick={openDemo} className="story-cta min-h-11 rounded-full px-4 text-sm font-semibold text-white sm:px-5">
              Request Demo
            </button>
            <button
              type="button"
              className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-slate-200 md:hidden"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav className="border-t border-slate-100 bg-white px-4 py-3 md:hidden" aria-label="Story mobile">
            {CHAPTERS.map((chapter) => (
              <a
                key={chapter.id}
                href={`#${chapter.id}`}
                onClick={() => setMenuOpen(false)}
                className="flex min-h-11 items-center rounded-lg px-2 text-base font-semibold"
                style={{ color: chapter.band }}
              >
                {chapter.label}
              </a>
            ))}
            <Link href="/login" className="flex min-h-11 items-center rounded-lg px-2 text-base font-semibold text-slate-800">
              Workspace Sign In
            </Link>
          </nav>
        )}
      </header>

      <main id="top">
        <section
          className="relative overflow-hidden px-4 pb-16 pt-12 sm:px-6 sm:pt-20"
          onMouseMove={(event) => {
            const rect = event.currentTarget.getBoundingClientRect();
            setSpot({
              x: ((event.clientX - rect.left) / rect.width) * 100,
              y: ((event.clientY - rect.top) / rect.height) * 100,
            });
          }}
        >
          <div
            className="pointer-events-none absolute inset-0"
            style={{
              background: `radial-gradient(420px circle at ${spot.x}% ${spot.y}%, ${spoken.band}55, transparent 42%)`,
            }}
          />
          <div className="relative mx-auto max-w-5xl text-center">
            <p className="text-xs font-bold uppercase tracking-[0.22em] text-slate-500">One campus. Four perspectives.</p>
            <h1 className="story-display mt-5 text-5xl font-bold leading-[0.95] tracking-tight text-slate-950 sm:text-7xl lg:text-8xl">
              The same morning,
              <span className="mt-2 block" style={{ color: spoken.band }} key={spoken.id}>
                <span className="story-swap inline-block">{spoken.label}</span>
              </span>
            </h1>
            <p className="mx-auto mt-6 max-w-2xl text-lg text-slate-600 sm:text-xl">
              CampXSync carries timetable, attendance, examinations, and governance on one shared core.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <button type="button" onClick={openDemo} className="story-cta min-h-12 w-full rounded-full px-8 text-base font-semibold text-white sm:w-auto">
                Request Institutional Demo
              </button>
              <a
                href="#student"
                className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-slate-300 bg-white px-6 font-semibold hover:border-slate-950 sm:w-auto"
              >
                Explore the story
                <ArrowDown className="h-4 w-4" />
              </a>
            </div>
            <div className="mx-auto mt-12 grid max-w-4xl grid-cols-2 gap-3 md:grid-cols-4">
              {CHAPTERS.map((chapter, index) => {
                const on = beat === index || active === chapter.id;
                return (
                  <a
                    key={chapter.id}
                    href={`#${chapter.id}`}
                    className="story-chip rounded-2xl px-3 py-4 text-left"
                    style={{
                      background: on ? chapter.band : '#ffffff',
                      color: on ? '#ffffff' : '#0f172a',
                      boxShadow: on ? `0 16px 40px -18px ${chapter.band}` : '0 1px 0 rgba(15,23,42,0.06)',
                    }}
                  >
                    <span className="block text-lg font-bold">{chapter.label}</span>
                    <span className="mt-1 block text-xs uppercase tracking-[0.12em] opacity-80">{chapter.hint}</span>
                  </a>
                );
              })}
            </div>
          </div>
        </section>

        <section id="student" data-reveal className="scroll-mt-24 px-4 py-6 sm:px-6 lg:px-10">
          <Band tone="#06b6d4" ink="#042f35">
            <ChapterHead icon={<GraduationCap className="h-4 w-4" />} label="Student" title="The day starts in one timetable." />
            <div className="grid items-start gap-6 lg:grid-cols-12">
              <div className="grid gap-3 sm:grid-cols-2 lg:col-span-7">
                {[
                  ["Personal timetable", "The day's rooms and times, before the first bell."],
                  ['Attendance margin', 'An early signal before a session minimum is missed.'],
                  ['Elective choice', 'Prerequisite and clash checks at the moment of selection.'],
                  ['Degree progress', 'Credits still owed, visible across the term.'],
                ].map(([title, copy]) => (
                  <article key={title} className="story-card">
                    <h3 className="font-semibold">{title}</h3>
                    <p className="mt-1 text-sm opacity-80">{copy}</p>
                  </article>
                ))}
              </div>
              <div className="rounded-[1.6rem] bg-white p-5 text-slate-900 shadow-xl lg:col-span-5">
                <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-cyan-700">Today&apos;s schedule</p>
                <h3 className="story-display text-2xl font-semibold">A sample Tuesday</h3>
                <div className="mt-4 space-y-3">
                  {CLASSES.map((item) => {
                    const selected = selectedClass === item.id;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setSelectedClass(item.id)}
                        className={`flex w-full items-center justify-between rounded-2xl p-3.5 text-left transition ${
                          selected
                            ? 'bg-[#06b6d4] text-white shadow-[0_12px_30px_-16px_#06b6d4]'
                            : 'bg-slate-50 text-slate-900 hover:-translate-y-0.5 hover:bg-cyan-50'
                        }`}
                      >
                        <span>
                          <span className={`text-[11px] font-bold uppercase tracking-[0.12em] ${selected ? 'text-white' : 'text-cyan-700'}`}>
                            {item.time}
                          </span>
                          <span className="mt-1 block font-semibold">{item.title}</span>
                          <span className={`text-sm ${selected ? 'text-cyan-50' : 'text-slate-500'}`}>{item.place}</span>
                        </span>
                        {selected ? (
                          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-cyan-700">
                            <Check className="h-4 w-4" />
                          </span>
                        ) : (
                          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{item.status}</span>
                        )}
                      </button>
                    );
                  })}
                </div>
                <div className="mt-4">
                  <div className="mb-2 flex justify-between text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500">
                    <span>Attendance meter · Sample</span>
                    <span className="text-cyan-700">88%</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                    <div className="story-meter h-full w-[88%] rounded-full bg-[#06b6d4]" />
                  </div>
                  <p className="mt-2 text-center text-[11px] uppercase tracking-[0.12em] text-slate-400">Sample, not a live statistic.</p>
                </div>
              </div>
            </div>
          </Band>
        </section>

        <section id="faculty" data-reveal className="scroll-mt-24 px-4 py-6 sm:px-6 lg:px-10">
          <Band tone="#7c3aed" ink="#2e1065">
            <ChapterHead icon={<Presentation className="h-4 w-4" />} label="Faculty" title="The hour the roster becomes a record." />
            <p className="max-w-xl text-lg text-white/90">
              The instructor marks the same session the student already opened. Present, absent, or late is one tap.
            </p>
            <div className="mt-6 rounded-[1.6rem] bg-white p-5 text-slate-900 shadow-xl sm:p-6">
              <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-violet-700">Active session roster</p>
              <h3 className="story-display text-2xl font-semibold">CS-402 Advanced Algorithms</h3>
              <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Attendance status">
                {(['present', 'absent', 'late'] as Attendance[]).map((status) => (
                  <button
                    key={status}
                    type="button"
                    onClick={() => setAttendance(status)}
                    className={`min-h-11 rounded-full px-4 text-sm font-semibold capitalize transition ${
                      attendance === status
                        ? 'bg-[#7c3aed] text-white shadow-[0_10px_24px_-12px_#7c3aed]'
                        : 'bg-violet-50 text-violet-900 hover:bg-violet-100'
                    }`}
                  >
                    {status}
                    {attendance === status ? ' · selected' : ''}
                  </button>
                ))}
              </div>
              <div className="mt-4 space-y-3">
                {[
                  ['AK', 'Alex Kim', 'STU-8921'],
                  ['MR', 'Maya Rodriguez', 'STU-7419'],
                ].map(([initials, name, id], index) => (
                  <div
                    key={id}
                    className={`flex flex-col gap-3 rounded-2xl p-3.5 sm:flex-row sm:items-center sm:justify-between ${
                      index === 0 ? 'bg-[#7c3aed] text-white' : 'bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <span className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold ${index === 0 ? 'bg-white text-violet-700' : 'bg-violet-600 text-white'}`}>
                        {initials}
                      </span>
                      <span>
                        <span className="block font-semibold">{name}</span>
                        <span className={`text-xs ${index === 0 ? 'text-violet-100' : 'text-slate-500'}`}>{id} · sample roster</span>
                      </span>
                    </div>
                    <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${index === 0 ? 'bg-white/20' : 'bg-white text-slate-600'}`}>
                      {index === 0 ? `Marked: ${attendance}` : 'Pending confirmation'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </Band>
        </section>

        <section id="college" data-reveal className="scroll-mt-24 px-4 py-6 sm:px-6 lg:px-10">
          <Band tone="#2563eb" ink="#172554">
            <ChapterHead icon={<Building2 className="h-4 w-4" />} label="College" title="The college sees the whole day." />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {PILLARS.map((item) => {
                const selected = pillar === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setPillar(item.id)}
                    className={`rounded-2xl p-5 text-left transition hover:-translate-y-1 ${
                      selected ? 'bg-white text-[#172554] shadow-xl' : 'bg-white/15 text-white hover:bg-white/25'
                    }`}
                  >
                    <h3 className="font-semibold">{item.title}</h3>
                    <p className={`mt-1 text-sm ${selected ? 'text-slate-600' : 'text-blue-50'}`}>{item.copy}</p>
                  </button>
                );
              })}
            </div>
            <div className="mt-5 rounded-[1.4rem] bg-white p-5 text-slate-900">
              <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-blue-700">Sample schedule · conflict free</p>
              <div className="mt-4 grid gap-3 md:grid-cols-3">
                {[
                  ['Hall B', 'CS-402 Algorithms', '11:30–13:00'],
                  ['Studio 4', 'Interaction Lab', '14:15–15:45'],
                  ['Auditorium 1', 'Physics Forum', '16:00–17:30'],
                ].map(([room, title, time]) => (
                  <div key={room} className="rounded-xl bg-blue-50 p-3 transition hover:-translate-y-0.5 hover:bg-blue-100">
                    <p className="text-[11px] uppercase tracking-[0.12em] text-blue-700">{room}</p>
                    <p className="font-semibold">{title}</p>
                    <p className="text-xs text-slate-500">{time} · locked</p>
                  </div>
                ))}
              </div>
            </div>
          </Band>
        </section>

        <section id="university" data-reveal className="scroll-mt-24 px-4 py-6 sm:px-6 lg:px-10">
          <Band tone="#f43f5e" ink="#4c0519">
            <ChapterHead icon={<Landmark className="h-4 w-4" />} label="University" title="The university holds the ledger." />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {COLLEGES.map((item) => {
                const selected = college === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setCollege(item.id)}
                    className={`rounded-2xl p-4 text-left transition hover:-translate-y-1 ${
                      selected ? 'bg-white text-[#4c0519] shadow-xl' : 'bg-white/15 text-white hover:bg-white/25'
                    }`}
                  >
                    <p className={`text-[11px] font-bold uppercase tracking-[0.12em] ${selected ? 'text-rose-600' : 'text-rose-100'}`}>{item.node}</p>
                    <p className="mt-1 font-semibold">{item.title}</p>
                    <p className={`mt-3 text-[11px] uppercase tracking-[0.12em] ${selected ? 'text-slate-500' : 'text-rose-100'}`}>
                      {selected ? 'Ledger stream active' : 'Sync normal'}
                    </p>
                  </button>
                );
              })}
            </div>
            <div className="mt-5 rounded-[1.4rem] bg-[#0b1020] p-5 text-white">
              <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-rose-300">CampXSync core ledger</p>
              <h3 className="story-display mt-1 text-2xl font-semibold">Central academic registry</h3>
              <p className="mt-1 text-sm text-slate-300">
                {COLLEGES.find((item) => item.id === college)?.title} is the selected stream into the same register.
              </p>
              <div className="mt-4 space-y-2">
                {[
                  ['11:34', 'Curriculum amendment locked'],
                  ['10:15', 'Exam session sealed'],
                  ['09:00', 'Cross-college credit noted'],
                ].map(([time, action]) => (
                  <div key={time} className="flex flex-col gap-1 rounded-xl bg-white/5 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm">
                      <span className="mr-3 font-bold text-rose-300">{time}</span>
                      {action}
                    </p>
                    <span className="text-[11px] uppercase tracking-[0.12em] text-emerald-300">Sample · verified</span>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-center text-xs text-slate-400">Sample ledger entries, not a live institution.</p>
            </div>
          </Band>
        </section>

        <section data-reveal className="mx-auto max-w-4xl px-4 py-16 text-center sm:px-6">
          <h2 className="story-display text-4xl font-bold tracking-tight sm:text-6xl">Same day. Four desks.</h2>
          <ol className="mt-10 space-y-4 text-left">
            {[
              ['08:00', 'Student desk', 'The timetable updates before the walk to class.', '#06b6d4'],
              ['11:30', 'Faculty desk', 'The roster mark is the same session the student sees.', '#7c3aed'],
              ['14:00', 'College desk', 'The room stays free of a second booking.', '#2563eb'],
              ['17:00', 'University desk', 'The day closes as one ledger entry.', '#f43f5e'],
            ].map(([time, desk, copy, color]) => (
              <li key={time} className="story-chip flex flex-col gap-2 rounded-2xl bg-white p-4 text-slate-900 shadow-sm sm:flex-row sm:items-center" style={{ borderLeft: `8px solid ${color}` }}>
                <span className="story-display text-2xl font-bold" style={{ color }}>
                  {time}
                </span>
                <span>
                  <span className="block font-semibold">{desk}</span>
                  <span className="text-sm text-slate-600">{copy}</span>
                </span>
              </li>
            ))}
          </ol>
        </section>

        <section className="mx-4 mb-16 overflow-hidden rounded-[2rem] bg-[#0b1020] px-6 py-14 text-center text-white sm:mx-6 sm:px-12 lg:mx-10">
          <h2 className="story-display text-4xl font-bold tracking-tight sm:text-5xl">Ready to see the same day from every desk?</h2>
          <p className="mx-auto mt-3 max-w-2xl text-lg text-slate-300">
            One core for the student, the faculty member, the college, and the university.
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <button type="button" onClick={openDemo} className="story-cta min-h-12 w-full rounded-full px-8 font-semibold text-white sm:w-auto">
              Request Executive Briefing
            </button>
            <Link href="/login" className="inline-flex min-h-12 w-full items-center justify-center rounded-full border border-white/20 px-8 font-semibold hover:bg-white hover:text-slate-950 sm:w-auto">
              Access Institutional Workspace
            </Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto grid max-w-[1440px] gap-8 px-6 py-12 md:grid-cols-2 lg:px-12">
          <div>
            <div className="flex items-center gap-3">
              <BrandMark />
              <span className="story-display text-xl font-bold">CampXSync</span>
            </div>
            <p className="mt-3 max-w-sm text-sm text-slate-500">Higher Education Operating System. The same day, seen from four desks.</p>
          </div>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <p className="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400">Perspectives</p>
              {CHAPTERS.map((chapter) => (
                <a key={chapter.id} href={`#${chapter.id}`} className="block py-1 font-semibold" style={{ color: chapter.band }}>
                  {chapter.label}
                </a>
              ))}
            </div>
            <div>
              <p className="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400">Note</p>
              <p className="text-slate-500">Every number, roster row, and schedule on this page is a demonstration placeholder.</p>
            </div>
          </div>
        </div>
      </footer>

      {demoOpen && (
        <div className="fixed inset-0 z-[60] flex items-end justify-center bg-slate-950/50 p-4 sm:items-center" role="dialog" aria-modal="true" aria-labelledby="demo-title">
          <div className="w-full max-w-md rounded-3xl bg-white p-6 text-slate-900 shadow-2xl">
            <div className="flex items-start justify-between gap-4">
              <h3 id="demo-title" className="story-display text-2xl font-bold">
                {demoSent ? 'Request noted on this screen' : 'Request a briefing'}
              </h3>
              <button type="button" aria-label="Close dialog" onClick={() => setDemoOpen(false)} className="rounded-lg p-2 hover:bg-slate-100">
                <X className="h-5 w-5" />
              </button>
            </div>
            {demoSent ? (
              <p className="mt-4 text-slate-600">This confirmation stays in the browser. It is not saved to a server yet.</p>
            ) : (
              <form
                className="mt-4 space-y-3"
                onSubmit={(event) => {
                  event.preventDefault();
                  setDemoSent(true);
                }}
              >
                <label className="block text-sm font-medium">
                  Name
                  <input required className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-3" name="name" />
                </label>
                <label className="block text-sm font-medium">
                  Institution
                  <input required className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-3" name="institution" />
                </label>
                <label className="block text-sm font-medium">
                  Email
                  <input required type="email" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-3" name="email" />
                </label>
                <button type="submit" className="story-cta min-h-11 w-full rounded-full font-semibold text-white">
                  Submit request
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function BrandMark() {
  return (
    <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400 via-violet-500 to-rose-500 text-sm font-bold text-white shadow-lg shadow-rose-500/30">
      CX
    </span>
  );
}

function ChapterHead({ icon, label, title }: { icon: React.ReactNode; label: string; title: string }) {
  return (
    <div className="mb-6">
      <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-white/20 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.16em] text-white">
        {icon}
        {label}
      </div>
      <h2 className="story-display text-4xl font-bold leading-none tracking-tight text-white sm:text-5xl">{title}</h2>
    </div>
  );
}

function Band({ tone, ink, children }: { tone: string; ink: string; children: React.ReactNode }) {
  return (
    <div className="story-band mx-auto max-w-[1280px] rounded-[2rem] px-5 py-8 sm:px-8 sm:py-10" style={{ background: tone, color: ink }}>
      {children}
    </div>
  );
}

function StoryStyles() {
  return (
    <style>{`
      .story-display { font-family: "Space Grotesk", "Sora", sans-serif; }
      .story-marquee { animation: story-marquee 28s linear infinite; }
      .story-swap { animation: story-swap .55s ease; }
      .story-card { border-radius: 1rem; background: rgba(255,255,255,0.16); color: white; padding: 1.1rem; transition: transform .25s ease, background .25s ease; }
      .story-card:hover { transform: translateY(-6px); background: rgba(255,255,255,0.28); }
      .story-chip { transition: transform .25s ease, background .25s ease; }
      .story-chip:hover { transform: translateY(-4px) scale(1.02); }
      .story-cta { background-image: linear-gradient(90deg, #f43f5e, #f59e0b); box-shadow: 0 14px 30px -14px rgba(244,63,94,.8); transition: transform .2s ease; }
      .story-cta:hover { transform: translateY(-2px) scale(1.03); }
      .story-band { animation: story-rise .7s ease both; }
      [data-reveal] { opacity: 0; transform: translateY(36px); animation: story-rise .8s ease forwards; animation-play-state: paused; }
      [data-reveal].is-in { animation-play-state: running; }
      .story-meter { transform-origin: left; animation: story-fill 1.1s ease forwards; }
      @keyframes story-marquee { from { transform: translateX(0); } to { transform: translateX(-50%); } }
      @keyframes story-swap { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }
      @keyframes story-rise { to { opacity: 1; transform: none; } }
      @keyframes story-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
      @media (prefers-reduced-motion: reduce) {
        .story-marquee, .story-swap, .story-band, [data-reveal], .story-meter, .story-cta, .story-card, .story-chip {
          animation: none !important; transition: none !important; opacity: 1 !important; transform: none !important;
        }
      }
    `}</style>
  );
}
