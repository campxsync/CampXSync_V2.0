'use client';

import React, { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import './platform.css';
import { Glyph, Mark } from './icons';
import {
  beats,
  channels,
  collegeAdmin,
  engines,
  externalSystems,
  flows,
  modules,
  pillars,
  platformAdmin,
  scenario,
  stakeholders,
  type ModuleId,
} from './content';

const CHANNEL_ICON: Record<string, string> = {
  'Web Portal': 'web',
  'Mobile App': 'mobile',
  'Conversational AI': 'chat',
  Email: 'email',
  SMS: 'sms',
  WhatsApp: 'whatsapp',
  'Push Notifications': 'push',
  'API and Integrations': 'api',
};

const EXTERNAL_ICON: Record<string, string> = {
  'Payment Gateway': 'payment',
  'Government Portals': 'government',
  'Email and SMS providers': 'providers',
  'BI and analytics tools': 'bi',
  'Other institution systems': 'systems',
};

const PILLAR_ICON = ['unified', 'automation', 'insights', 'secure', 'scale'];

export function CampXSyncPlatformLanding() {
  const pageRef = useRef<HTMLDivElement>(null);
  const [menu, setMenu] = useState(false);
  const [word, setWord] = useState(0);
  const [person, setPerson] = useState(0);
  const [openModule, setOpenModule] = useState<ModuleId | null>(null);
  const [engine, setEngine] = useState(0);
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [sent, setSent] = useState(false);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const apply = () => setReduced(media.matches);
    apply();
    media.addEventListener('change', apply);
    const nodes = document.querySelectorAll('.cx-from-left, .cx-from-right');
    const seen = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) entry.target.classList.add('is-in');
      });
    }, { threshold: 0.2 });
    nodes.forEach((node) => seen.observe(node));
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const max = document.documentElement.scrollHeight - window.innerHeight;
        const p = max > 0 ? window.scrollY / max : 0;
        pageRef.current?.style.setProperty('--p', p.toFixed(4));
      });
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      media.removeEventListener('change', apply);
      seen.disconnect();
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    if (reduced) return;
    const id = window.setInterval(() => setWord((value) => (value + 1) % beats.length), 2400);
    return () => window.clearInterval(id);
  }, [reduced]);

  useEffect(() => {
    if (!playing || reduced) return;
    const id = window.setInterval(() => setStep((value) => (value + 1) % scenario.length), 2400);
    return () => window.clearInterval(id);
  }, [playing, reduced]);

  const doubledPeople = [...stakeholders, ...stakeholders];
  const doubledChannels = [...channels, ...channels];

  return (
    <div className="cx-page" ref={pageRef}>
      <a className="cx-skip" href="#main">Skip to content</a>
      <div className="cx-progress" />
      <div className="cx-mesh" aria-hidden="true"><i /><i /><i /><i /></div>
      <header className="cx-header">
        <Link href="/" className="cx-brand" aria-label="CampXSync home">
          <Mark />
          <span>Camp<span className="cx-x">X</span>Sync</span>
        </Link>
        <nav className="cx-nav" aria-label="Page">
          <a href="#channels">Channels</a>
          <a href="#modules">Modules</a>
          <a href="#intelligence">Intelligence</a>
          <a href="#story">A day on campus</a>
          <a href="#demo">Demo</a>
        </nav>
        <div className="cx-actions">
          <Link className="cx-btn ghost cx-signin" href="/login">Sign in</Link>
          <a className="cx-btn" href="#demo">Request a demo</a>
          <button className="cx-menu" type="button" aria-expanded={menu} aria-label="Open menu" onClick={() => setMenu((value) => !value)}>☰</button>
        </div>
      </header>
      {menu ? (
        <nav className="cx-drawer" aria-label="Mobile">
          <a href="#channels" onClick={() => setMenu(false)}>Channels</a>
          <a href="#modules" onClick={() => setMenu(false)}>Modules</a>
          <a href="#intelligence" onClick={() => setMenu(false)}>Intelligence</a>
          <a href="#story" onClick={() => setMenu(false)}>A day on campus</a>
          <Link href="/login">Sign in</Link>
        </nav>
      ) : null}

      <main id="main" className="cx-main">
        <section className="cx-hero">
          <p className="cx-kicker">One campus platform</p>
          <h1 className="cx-display">
            Remember. <span className="cx-g1">Understand.</span> <span className="cx-g2">Act.</span> <span className="cx-g3">Evolve.</span>
          </h1>
          <p className="cx-lead">{beats[word].copy}</p>
          <div className="cx-cta">
            <a className="cx-btn" href="#demo">Request a demo</a>
            <a className="cx-btn ghost" href="#modules">See the modules</a>
          </div>
          <div className="cx-connect" aria-hidden="true">
            <svg viewBox="0 0 720 150">
              <defs>
                <linearGradient id="cxg" x1="0" x2="1">
                  <stop offset="0" stopColor="#2563eb" />
                  <stop offset="0.5" stopColor="#7c3aed" />
                  <stop offset="1" stopColor="#f97316" />
                </linearGradient>
              </defs>
              <path d="M40 75 H280 M440 75 H680 M360 28 V122" stroke="url(#cxg)" strokeWidth="2" fill="none" />
              <circle cx="360" cy="75" r="28" fill="#2563eb" />
              <text x="360" y="80" textAnchor="middle" fill="white" fontSize="14" fontWeight="700">CX</text>
              {['Student', 'Faculty', 'College', 'Family'].map((label, index) => (
                <g key={label} transform={`translate(${index < 2 ? 40 + index * 90 : 500 + (index - 2) * 90}, ${index % 2 ? 28 : 108})`}>
                  <circle r="16" fill={index % 2 ? '#7c3aed' : '#0891b2'} />
                  <text y="28" textAnchor="middle" fill="#3d4663" fontSize="12">{label}</text>
                </g>
              ))}
            </svg>
          </div>
        </section>

        <section className="cx-section" id="people">
          <div className="cx-wrap">
            <p className="cx-kicker cx-from-left">Who it is for</p>
            <h2 className="cx-display cx-from-left">Every person on campus, on the same platform.</h2>
          </div>
          <div className="cx-marquee" aria-label="Stakeholders">
            <div className="cx-track">
              {doubledPeople.map((item, index) => (
                <button className="cx-chip" type="button" key={`${item.id}-${index}`} onClick={() => setPerson(index % stakeholders.length)}>
                  <Glyph id={item.id} label={item.name} />
                  <span>{item.name}</span>
                </button>
              ))}
            </div>
          </div>
          <p className="cx-detail cx-wrap">{stakeholders[person].name}: {stakeholders[person].promise}</p>
        </section>

        <section className="cx-section" id="channels">
          <div className="cx-wrap">
            <p className="cx-kicker cx-from-right">Access channels</p>
            <h2 className="cx-display cx-from-right">One platform, every channel.</h2>
            <p className="cx-intro">People reach the same campus record from the web, a phone, a message, or a partner system.</p>
          </div>
          <div className="cx-marquee rev">
            <div className="cx-track">
              {doubledChannels.map((name, index) => (
                <span className="cx-chip" key={`${name}-${index}`}>
                  <Glyph id={CHANNEL_ICON[name]} label={name} />
                  <span>{name}</span>
                </span>
              ))}
            </div>
          </div>
          <div className="cx-channel-board">
            <Mark size={52} />
            <div className="cx-line" />
            <div className="cx-icons">
              {channels.map((name) => (
                <button key={name} type="button">
                  <Glyph id={CHANNEL_ICON[name]} label={name} />
                  {name}
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="cx-section" id="modules">
          <div className="cx-wrap">
            <p className="cx-kicker cx-from-left">Campus modules</p>
            <h2 className="cx-display cx-from-left">Twelve modules. One campus record.</h2>
            <div className="cx-modules">
              {modules.map((item) => (
                <button
                  className={`cx-tile${openModule === item.id ? ' is-open' : ''}`}
                  key={item.id}
                  type="button"
                  onClick={() => setOpenModule((current) => (current === item.id ? null : item.id))}
                >
                  <Glyph id={item.id} label={item.name} />
                  <strong>{item.name}</strong>
                  <ul className="cx-caps">
                    {item.capabilities.map((capability) => <li key={capability}>{capability}</li>)}
                  </ul>
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="cx-section" id="administration">
          <div className="cx-wrap">
            <p className="cx-kicker">Administration</p>
            <h2 className="cx-display">One institute. Many colleges, each with its own space.</h2>
            <div className="cx-admin">
              <article className="cx-soft institute">
                <h3>Institute</h3>
                <ol>{platformAdmin.map((item) => <li key={item}>{item}</li>)}</ol>
              </article>
              <article className="cx-soft">
                <h3>Each college</h3>
                <ol>{collegeAdmin.map((item) => <li key={item}>{item}</li>)}</ol>
              </article>
            </div>
          </div>
        </section>

        <section className="cx-intel" id="intelligence">
          <div className="cx-wrap">
            <p className="cx-kicker">Intelligence layer</p>
            <h2 className="cx-display">AI-driven at the core.</h2>
            <p className="cx-intro">Eight engines share one picture of the campus, then remember, understand, act, and evolve.</p>
            <div className="cx-engines">
              {engines.map((item, index) => (
                <button className={`cx-engine${engine === index ? ' is-on' : ''}`} key={item.id} type="button" onClick={() => setEngine(index)}>
                  <Glyph id={item.id} label={item.name} />
                  <strong>{item.name}</strong>
                  <small>{item.facets}</small>
                  <p>{item.plain}</p>
                </button>
              ))}
            </div>
            <div className="cx-rail" aria-hidden="true"><i /></div>
          </div>
        </section>

        <section className="cx-section" id="flows">
          <div className="cx-wrap">
            <p className="cx-kicker cx-from-right">How information moves</p>
            <h2 className="cx-display cx-from-right">A request becomes a result someone can see.</h2>
            {flows.map((flow) => (
              <div className="cx-flow" key={flow.join('-')}>
                {flow.map((part, index) => (
                  <React.Fragment key={part}>
                    {index > 0 ? <i /> : null}
                    <b>{part}</b>
                  </React.Fragment>
                ))}
              </div>
            ))}
          </div>
        </section>

        <section className="cx-section" id="story">
          <div className="cx-wrap">
            <p className="cx-kicker">A student enrolls in a course</p>
            <h2 className="cx-display">Six steps, from sign-in to the dashboard.</h2>
            <button className="cx-btn ghost" type="button" onClick={() => setPlaying((value) => !value)}>
              {playing && !reduced ? 'Pause' : 'Play'}
            </button>
            <div className="cx-scene">
              <div>
                {scenario.map((item, index) => (
                  <button
                    className={`cx-step${step === index ? ' is-on' : ''}`}
                    key={item.title}
                    type="button"
                    onClick={() => { setStep(index); setPlaying(false); }}
                  >
                    <strong>{item.title}</strong>
                    <div>{item.detail}</div>
                  </button>
                ))}
              </div>
              <Moment moment={scenario[step].moment} title={scenario[step].title} />
            </div>
            <p className="cx-note">All interactions are logged in Audit & Compliance.</p>
          </div>
        </section>

        <section className="cx-section" id="ecosystem">
          <div className="cx-wrap">
            <p className="cx-kicker">Works with the campus already has</p>
            <h2 className="cx-display">Connected to the systems around it.</h2>
            <div className="cx-ext">
              <Mark size={56} />
              <div className="cx-line" />
              <div className="cx-extrow">
                {externalSystems.map((name) => (
                  <span className="cx-extchip" key={name}>
                    <Glyph id={EXTERNAL_ICON[name]} label={name} />
                    {name}
                    {name === 'Government Portals' ? ' · DigiLocker' : ''}
                    {name === 'Other institution systems' ? ' · ERP / LMS' : ''}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section className="cx-section" id="why">
          <div className="cx-wrap">
            <p className="cx-kicker">Why CampXSync</p>
            <h2 className="cx-display">Built for the whole institution.</h2>
            <div className="cx-pillars">
              {pillars.map((item, index) => (
                <article className="cx-pillar" key={item.title}>
                  <Glyph id={PILLAR_ICON[index]} label={item.title} />
                  <h3>{item.title}</h3>
                  <p>{item.copy}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="cx-close" id="demo">
          <h2 className="cx-display">Intelligently connecting education. Today. Tomorrow. Together.</h2>
          {sent ? (
            <p>This request stays in the browser. It is not saved to a server yet.</p>
          ) : (
            <form className="cx-form" onSubmit={(event) => { event.preventDefault(); setSent(true); }}>
              <label>Name<input name="name" required autoComplete="name" /></label>
              <label>Institution<input name="institution" required /></label>
              <label>Email<input name="email" type="email" required autoComplete="email" /></label>
              <button className="cx-btn" type="submit">Request a demo</button>
            </form>
          )}
        </section>
      </main>
      <footer className="cx-footer">
        <span className="cx-brand"><Mark size={28} /> Camp<span className="cx-x">X</span>Sync</span>
        <nav>
          <a href="#modules">Modules</a>
          <a href="#intelligence">Intelligence</a>
          <Link href="/login">Sign in</Link>
        </nav>
      </footer>
    </div>
  );
}

function Moment({ moment, title }: { moment: string; title: string }) {
  return (
    <div className="cx-moment" aria-live="polite">
      <strong>{title}</strong>
      {moment === 'login' ? <div className="cx-mini">Portal · Mobile · Signed in</div> : null}
      {moment === 'enroll' ? <div className="cx-mini">Academic · Enrollment recorded</div> : null}
      {moment === 'fee' ? <div className="cx-mini">Finance · Fee confirmed<div className="cx-bar"><b /></div></div> : null}
      {moment === 'note' ? <div className="cx-mini">Notification · Confirmation sent</div> : null}
      {moment === 'course' ? <div className="cx-mini">Learning · Course unlocked</div> : null}
      {moment === 'board' ? <div className="cx-mini">Insight · Dashboards updated<div className="cx-bar"><b /></div></div> : null}
    </div>
  );
}
