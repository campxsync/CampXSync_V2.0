'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { motion, AnimatePresence, useInView, useReducedMotion, useScroll, useSpring } from 'motion/react';
import {
  ShieldCheck,
  CheckCircle2,
  Building,
  GraduationCap,
  CalendarCheck2,
  Clock,
  ArrowRight,
  Layers,
  ChevronRight,
  ChevronLeft,
  BarChart3,
  Sparkles,
  Calendar,
  Check,
  Send,
  X,
  BadgeCheck,
  Activity,
  Menu,
  Zap,
  Radio,
  Lock,
  Play,
  Terminal,
  Database,
  Compass,
  FileCheck2,
  Users,
  Eye,
  Sliders,
  AlertCircle,
  ExternalLink,
} from 'lucide-react';

import { PLACEHOLDERS } from '@/content/placeholders';
import { Logo } from './logo';
import { HeroIllustration, RoleIllustration } from './illustrations';

// ================= ANIMATED COUNTER COMPONENT =================
function AnimatedCounter({
  target,
  decimals = 0,
  prefix = '',
  suffix = '',
}: {
  target: number;
  decimals?: number;
  prefix?: string;
  suffix?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const isInView = useInView(ref, { once: true, margin: '-40px' });
  const shouldReduceMotion = useReducedMotion();
  const [displayValue, setDisplayValue] = useState<number>(shouldReduceMotion ? target : 0);

  useEffect(() => {
    if (!isInView) return;
    if (shouldReduceMotion) {
      setDisplayValue(target);
      return;
    }

    const duration = 1400; // ms
    const startTime = performance.now();
    let frameId: number;

    const tick = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // easeOutExpo curve for elegant settling
      const ease = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      const current = target * ease;
      setDisplayValue(current);

      if (progress < 1) {
        frameId = requestAnimationFrame(tick);
      } else {
        setDisplayValue(target);
      }
    };

    frameId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameId);
  }, [isInView, target, shouldReduceMotion]);

  return (
    <span ref={ref} className="tabular-nums font-extrabold tracking-tight">
      {prefix}
      {displayValue.toFixed(decimals)}
      {suffix}
    </span>
  );
}

// ================= MASTER LANDING PAGE COMPONENT =================
export function CampXSyncLandingPage() {
  const [activePersonaIdx, setActivePersonaIdx] = useState<number>(0);
  const [isPersonaPaused, setIsPersonaPaused] = useState<boolean>(false);
  const [activeCapabilityTab, setActiveCapabilityTab] = useState<number>(0);
  const [activeWalkthroughStep, setActiveWalkthroughStep] = useState<number>(0);
  const [isDemoModalOpen, setIsDemoModalOpen] = useState<boolean>(false);
  const [demoSubmitted, setDemoSubmitted] = useState<boolean>(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);
  const [isScrolled, setIsScrolled] = useState<boolean>(false);

  const shouldReduceMotion = useReducedMotion();

  // Scroll Progress Tracking
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 100,
    damping: 30,
    restDelta: 0.001,
  });

  // Track window scroll for sticky header elevation
  useEffect(() => {
    const handleScroll = () => {
      if (window.scrollY > 20) {
        setIsScrolled(true);
      } else {
        setIsScrolled(false);
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Automatic looping for persona slider (pauses on hover or touch)
  useEffect(() => {
    if (isPersonaPaused || shouldReduceMotion) return;
    const interval = setInterval(() => {
      setActivePersonaIdx((prev) => (prev + 1) % PLACEHOLDERS.personas.length);
    }, 6000);
    return () => clearInterval(interval);
  }, [isPersonaPaused, shouldReduceMotion]);

  // Demo form states
  const [demoForm, setDemoForm] = useState({
    name: '',
    institution: '',
    role: 'Dean / Department Chair',
    email: '',
    campusSize: '2,000 – 10,000 Students',
  });

  const handleDemoSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setDemoSubmitted(true);
  };

  const closeDemoModal = () => {
    setIsDemoModalOpen(false);
    setDemoSubmitted(false);
  };

  // Keyboard accessibility for Persona Slider Tabs
  const handlePersonaKeyDown = (e: React.KeyboardEvent, currentIdx: number) => {
    let nextIdx = currentIdx;
    if (e.key === 'ArrowRight') {
      nextIdx = (currentIdx + 1) % PLACEHOLDERS.personas.length;
      e.preventDefault();
    } else if (e.key === 'ArrowLeft') {
      nextIdx = (currentIdx - 1 + PLACEHOLDERS.personas.length) % PLACEHOLDERS.personas.length;
      e.preventDefault();
    } else if (e.key === 'Home') {
      nextIdx = 0;
      e.preventDefault();
    } else if (e.key === 'End') {
      nextIdx = PLACEHOLDERS.personas.length - 1;
      e.preventDefault();
    }
    if (nextIdx !== currentIdx) {
      setActivePersonaIdx(nextIdx);
      const el = document.getElementById(`persona-tab-${nextIdx}`);
      el?.focus();
    }
  };

  const activePersona = PLACEHOLDERS.personas[activePersonaIdx];
  const activeCapability = PLACEHOLDERS.capabilities[activeCapabilityTab];

  return (
    <div className="relative min-h-screen bg-[#070B14] text-[#F8FAFC] selection:bg-cyan-500/30 selection:text-cyan-200 overflow-x-hidden">
      {/* 1. SCROLL PROGRESS INDICATOR */}
      <motion.div
        className="fixed top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-blue-500 via-violet-500 to-cyan-400 z-50 origin-left"
        style={{ scaleX }}
      />

      {/* 2. STICKY FROSTED NAVIGATION HEADER */}
      <header
        className={`fixed top-0 left-0 right-0 z-40 transition-all duration-300 ${
          isScrolled
            ? 'bg-[#070B14]/85 backdrop-blur-xl border-b border-white/10 shadow-2xl shadow-black/40 py-3.5'
            : 'bg-transparent py-5'
        }`}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between">
          <Logo size="md" />

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-300" aria-label="Main Navigation">
            <a
              href="#foundation"
              className="transition-colors hover:text-cyan-400 focus-ring rounded-md px-1 py-0.5"
            >
              Foundations
            </a>
            <a
              href="#capabilities"
              className="transition-colors hover:text-cyan-400 focus-ring rounded-md px-1 py-0.5"
            >
              Capabilities
            </a>
            <a
              href="#personas"
              className="transition-colors hover:text-cyan-400 focus-ring rounded-md px-1 py-0.5"
            >
              Role Workspaces
            </a>
            <a
              href="#impact"
              className="transition-colors hover:text-cyan-400 focus-ring rounded-md px-1 py-0.5"
            >
              Impact
            </a>
            <a
              href="#walkthrough"
              className="transition-colors hover:text-cyan-400 focus-ring rounded-md px-1 py-0.5"
            >
              Architecture
            </a>
          </nav>

          {/* Right Action CTAs */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              href="/login"
              className="px-4 py-2 text-sm font-semibold text-slate-300 hover:text-white transition-colors focus-ring rounded-lg border border-transparent hover:border-white/10"
            >
              Workspace Sign In
            </Link>
            <button
              onClick={() => setIsDemoModalOpen(true)}
              className="relative group px-5 py-2.5 rounded-xl text-sm font-bold text-white overflow-hidden shadow-lg shadow-rose-950/50 transition-all duration-300 hover:-translate-y-0.5 active:translate-y-0 focus-ring"
            >
              {/* Vibrant Coral to Amber gradient background */}
              <div className="absolute inset-0 bg-gradient-to-r from-rose-500 via-rose-600 to-amber-500 transition-transform duration-300 group-hover:scale-105" />
              <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity" />
              <span className="relative flex items-center gap-2">
                Request Demo
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
              </span>
            </button>
          </div>

          {/* Mobile Menu Trigger */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden p-2 rounded-lg bg-slate-800/60 border border-white/10 text-slate-300 hover:text-white focus-ring"
            aria-label="Toggle navigation menu"
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>

        {/* Mobile Navigation Drawer */}
        <AnimatePresence>
          {mobileMenuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="md:hidden bg-[#0D1527] border-b border-white/10 px-6 py-6 overflow-hidden"
            >
              <div className="flex flex-col gap-4 text-base font-medium text-slate-200">
                <a
                  href="#foundation"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-cyan-400 transition-colors"
                >
                  Foundations
                </a>
                <a
                  href="#capabilities"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-cyan-400 transition-colors"
                >
                  Capabilities
                </a>
                <a
                  href="#personas"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-cyan-400 transition-colors"
                >
                  Role Workspaces
                </a>
                <a
                  href="#impact"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-cyan-400 transition-colors"
                >
                  Impact
                </a>
                <a
                  href="#walkthrough"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-cyan-400 transition-colors"
                >
                  Architecture
                </a>
                <div className="pt-4 border-t border-white/10 flex flex-col gap-3">
                  <Link
                    href="/login"
                    onClick={() => setMobileMenuOpen(false)}
                    className="w-full text-center py-2.5 rounded-lg border border-white/15 text-slate-200 font-semibold"
                  >
                    Workspace Sign In
                  </Link>
                  <button
                    onClick={() => {
                      setMobileMenuOpen(false);
                      setIsDemoModalOpen(true);
                    }}
                    className="w-full text-center py-2.5 rounded-lg bg-gradient-to-r from-rose-500 to-amber-500 text-white font-bold shadow-lg"
                  >
                    Request Institutional Demo
                  </button>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </header>

      {/* 3. HERO SECTION WITH FLOWING GRADIENT MESH & FOCAL VISUAL */}
      <section className="relative pt-32 pb-20 md:pt-40 md:pb-32 overflow-hidden">
        {/* Slow flowing ambient mesh blobs (24s cycle) */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[550px] bg-gradient-to-tr from-blue-600/30 via-violet-600/30 to-cyan-500/25 blur-[120px] rounded-full -z-10 animate-fluid-mesh pointer-events-none" />
        <div className="absolute top-1/3 -left-40 w-96 h-96 bg-cyan-500/15 blur-[100px] rounded-full -z-10 pointer-events-none" />
        <div className="absolute top-1/4 -right-40 w-96 h-96 bg-violet-600/20 blur-[100px] rounded-full -z-10 pointer-events-none" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
            {/* Left Content Stagger Sequence */}
            <motion.div
              initial="hidden"
              animate="visible"
              variants={{
                hidden: { opacity: 0 },
                visible: {
                  opacity: 1,
                  transition: { staggerChildren: 0.12, delayChildren: 0.1 },
                },
              }}
              className="lg:col-span-7 text-center lg:text-left"
            >
              {/* Badge */}
              <motion.div
                variants={{
                  hidden: { opacity: 0, y: 15 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-slate-900/80 border border-cyan-500/30 text-cyan-300 text-xs font-semibold tracking-wide shadow-lg shadow-cyan-950/40 mb-6"
              >
                <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
                <span>Unified Academic Operating System</span>
                <span className="text-slate-500 font-mono">v2.0</span>
              </motion.div>

              {/* Headline with Confident Line Breaks & Animated Gradient Accent */}
              <motion.h1
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="font-display font-extrabold text-4xl sm:text-5xl lg:text-6xl tracking-tight leading-[1.08] text-white mb-6"
              >
                Higher Education Operations, <br className="hidden sm:inline" />
                <span className="gradient-text font-black">Unified In Real Time.</span>
              </motion.h1>

              {/* Short Supporting Copy */}
              <motion.p
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="text-lg sm:text-xl text-slate-300 max-w-2xl mx-auto lg:mx-0 font-normal leading-relaxed mb-8"
              >
                CampXSync eliminates operational silos across curricula, automated timetables,
                examinations, and institutional governance with an immutable audit ledger.
              </motion.p>

              {/* Coordinated Action CTAs */}
              <motion.div
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4"
              >
                <button
                  onClick={() => setIsDemoModalOpen(true)}
                  className="w-full sm:w-auto relative group px-7 py-4 rounded-xl text-base font-bold text-white overflow-hidden shadow-xl shadow-rose-950/50 transition-all duration-300 hover:-translate-y-1 active:translate-y-0 focus-ring"
                >
                  <div className="absolute inset-0 bg-gradient-to-r from-rose-500 via-rose-600 to-amber-500 transition-transform duration-300 group-hover:scale-105" />
                  <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                  <span className="relative flex items-center justify-center gap-2.5">
                    Request Institutional Demo
                    <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1.5" />
                  </span>
                </button>

                <a
                  href="#foundation"
                  className="w-full sm:w-auto px-6 py-4 rounded-xl text-base font-semibold text-slate-200 bg-slate-900/60 hover:bg-slate-800/80 border border-white/10 hover:border-cyan-400/40 transition-all duration-200 text-center focus-ring"
                >
                  Explore Foundation Architecture
                </a>
              </motion.div>

              {/* Demonstration Telemetry Disclaimer Pill */}
              <motion.div
                variants={{
                  hidden: { opacity: 0 },
                  visible: { opacity: 1 },
                }}
                className="mt-8 flex items-center justify-center lg:justify-start gap-3 text-xs text-slate-400"
              >
                <span className="inline-flex items-center gap-1.5 text-emerald-400 font-medium">
                  <ShieldCheck className="w-4 h-4" />
                  Cryptographic Tenancy
                </span>
                <span className="text-slate-600">•</span>
                <span>Deterministic Clash Solver</span>
                <span className="text-slate-600">•</span>
                <span className="text-cyan-400/90 font-mono">Sample Roster Pacing</span>
              </motion.div>
            </motion.div>

            {/* Right Focal Animated Visual */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 30 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
              className="lg:col-span-5"
            >
              <HeroIllustration />
            </motion.div>
          </div>
        </div>
      </section>

      {/* 4. ECOSYSTEM TRUST MARQUEE (Continuous Smooth Loop) */}
      <section className="relative py-8 border-y border-white/5 bg-[#0A0F1D]/80 overflow-hidden select-none" aria-label="Ecosystem Partners">
        <div className="max-w-7xl mx-auto px-4 mb-3 flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-widest">
          <span>Institutional Architecture Compatibility</span>
          <span className="text-[11px] text-cyan-400/80 font-normal lowercase tracking-normal">
            (pause on hover / demonstration labels)
          </span>
        </div>

        {/* Marquee Track */}
        <div className="relative w-full overflow-hidden mask-gradient-x">
          <div className="animate-marquee flex gap-6 items-center">
            {/* Duplicated list for seamless infinite loop */}
            {[...PLACEHOLDERS.marqueePartners, ...PLACEHOLDERS.marqueePartners].map((partner, idx) => (
              <div
                key={`${partner.id}-${idx}`}
                className="flex items-center gap-3 px-5 py-3 rounded-xl bg-slate-900/60 border border-white/8 text-slate-300 whitespace-nowrap hover:border-cyan-400/30 transition-colors"
              >
                <div className="w-7 h-7 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400 font-bold text-xs">
                  {partner.name.charAt(0)}
                </div>
                <div className="flex flex-col">
                  <span className="text-sm font-semibold text-white tracking-tight">{partner.name}</span>
                  <span className="text-[10px] text-slate-400 font-mono">{partner.category} [Placeholder]</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 5. "ONE SHARED FOUNDATION" (Four Pillars with Sleek Glowing Vectors) */}
      <section id="foundation" className="py-24 relative overflow-hidden">
        {/* Ambient background glow */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[500px] bg-blue-600/10 blur-[130px] rounded-full -z-10 pointer-events-none" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-xs font-bold uppercase tracking-widest text-cyan-400 bg-cyan-400/10 border border-cyan-400/20 px-3.5 py-1 rounded-full">
              Enterprise Governance
            </span>
            <h2 className="font-display font-extrabold text-3xl sm:text-4xl lg:text-5xl tracking-tight text-white mt-4 mb-5">
              One Shared Foundation. <br />
              <span className="text-slate-400 font-normal">Four Interlocking Pillars.</span>
            </h2>
            <p className="text-slate-300 text-base sm:text-lg leading-relaxed">
              Academic operations cannot succeed as isolated software islands. CampXSync connects
              the entire lifecycle on a single, synchronized database core.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {PLACEHOLDERS.pillars.map((pillar, idx) => {
              const icons = [Compass, CalendarCheck2, FileCheck2, Building];
              const IconComp = icons[idx % icons.length];
              const colors = [
                { border: 'hover:border-blue-500/40', glow: 'group-hover:text-blue-400', bg: 'bg-blue-500/10' },
                { border: 'hover:border-cyan-500/40', glow: 'group-hover:text-cyan-400', bg: 'bg-cyan-500/10' },
                { border: 'hover:border-violet-500/40', glow: 'group-hover:text-violet-400', bg: 'bg-violet-500/10' },
                { border: 'hover:border-amber-500/40', glow: 'group-hover:text-amber-400', bg: 'bg-amber-500/10' },
              ][idx % 4];

              return (
                <motion.div
                  key={pillar.id}
                  initial={{ opacity: 0, y: 24 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-40px' }}
                  transition={{ duration: 0.5, delay: idx * 0.1 }}
                  className={`group relative p-6 rounded-2xl bg-gradient-to-b from-slate-900/80 to-slate-950/80 border border-white/10 ${colors.border} transition-all duration-300 hover:-translate-y-1.5 shadow-xl`}
                >
                  <div className="flex items-center justify-between mb-5">
                    <div className={`w-12 h-12 rounded-xl ${colors.bg} border border-white/10 flex items-center justify-center text-slate-200 ${colors.glow} transition-colors`}>
                      <IconComp className="w-6 h-6" />
                    </div>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-slate-400 border border-white/10">
                      {pillar.badge}
                    </span>
                  </div>

                  <h3 className="text-xl font-bold font-display text-white tracking-tight mb-2">
                    {pillar.title}
                  </h3>
                  <p className="text-xs text-cyan-400 font-semibold mb-4 tracking-wide uppercase">
                    {pillar.subtitle}
                  </p>

                  <ul className="space-y-3 text-xs text-slate-300 leading-relaxed border-t border-white/10 pt-4">
                    {pillar.features.map((feat, fIdx) => (
                      <li key={fIdx} className="flex items-start gap-2">
                        <Check className="w-3.5 h-3.5 text-cyan-400 shrink-0 mt-0.5" />
                        <span>{feat}</span>
                      </li>
                    ))}
                  </ul>
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>

      {/* 6. CAPABILITIES (Dynamic Tabbed Showcase with Real Layout Transitions) */}
      <section id="capabilities" className="py-24 relative bg-[#090E1B] border-y border-white/5">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between mb-12">
            <div>
              <span className="text-xs font-bold uppercase tracking-widest text-violet-400 bg-violet-400/10 border border-violet-400/20 px-3.5 py-1 rounded-full">
                Technical Capabilities
              </span>
              <h2 className="font-display font-extrabold text-3xl sm:text-4xl text-white mt-4 tracking-tight">
                Deep Infrastructure, <span className="gradient-text font-black">Zero Compromise.</span>
              </h2>
            </div>
            <p className="text-slate-400 text-sm max-w-md mt-4 md:mt-0">
              Each capability module delivers automated verification, cryptographic data persistence, and instantaneous audit compliance.
            </p>
          </div>

          {/* Interactive Navigation Tabs */}
          <div className="flex flex-wrap gap-2 p-1.5 rounded-2xl bg-slate-900/90 border border-white/10 max-w-3xl mb-8" role="tablist" aria-label="Capabilities Showcase">
            {PLACEHOLDERS.capabilities.map((tab, idx) => {
              const isActive = activeCapabilityTab === idx;
              return (
                <button
                  key={tab.id}
                  role="tab"
                  id={`capability-tab-${idx}`}
                  aria-selected={isActive}
                  aria-controls={`capability-panel-${idx}`}
                  onClick={() => setActiveCapabilityTab(idx)}
                  className={`relative flex-1 min-w-[140px] py-3 px-4 rounded-xl text-xs sm:text-sm font-bold transition-all focus-ring ${
                    isActive ? 'text-white' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {isActive && (
                    <motion.div
                      layoutId="activeCapabilityPill"
                      transition={{ type: 'spring', stiffness: 450, damping: 32 }}
                      className="absolute inset-0 bg-gradient-to-r from-blue-600 via-indigo-600 to-violet-600 rounded-xl shadow-lg shadow-blue-900/40 border border-white/20 -z-0"
                    />
                  )}
                  <span className="relative z-10 tracking-tight">{tab.label}</span>
                </button>
              );
            })}
          </div>

          {/* Tab Content Display with Real AnimatePresence Transition */}
          <div className="relative min-h-[440px]">
            <AnimatePresence mode="wait">
              <motion.div
                key={activeCapability.id}
                role="tabpanel"
                id={`capability-panel-${activeCapabilityTab}`}
                aria-labelledby={`capability-tab-${activeCapabilityTab}`}
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -16 }}
                transition={{ duration: 0.35, ease: 'easeInOut' }}
                className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center bg-slate-900/60 border border-white/10 rounded-3xl p-6 sm:p-10 backdrop-blur-md"
              >
                {/* Left Description & Bullet Points */}
                <div className="lg:col-span-6">
                  <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-cyan-300 text-xs font-semibold mb-4">
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>{activeCapability.badge}</span>
                  </div>

                  <h3 className="font-display font-bold text-2xl sm:text-3xl text-white tracking-tight leading-tight mb-4">
                    {activeCapability.title}
                  </h3>

                  <p className="text-slate-300 text-sm sm:text-base leading-relaxed mb-6">
                    {activeCapability.description}
                  </p>

                  <div className="space-y-4">
                    {activeCapability.bullets.map((bullet, bIdx) => (
                      <div key={bIdx} className="flex items-start gap-3 p-3.5 rounded-xl bg-slate-950/60 border border-white/5">
                        <div className="w-6 h-6 rounded-md bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0 mt-0.5">
                          <Check className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <h4 className="text-xs sm:text-sm font-bold text-white tracking-wide">
                            {bullet.title}
                          </h4>
                          <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                            {bullet.desc}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right Interactive Simulator Preview Panel */}
                <div className="lg:col-span-6 bg-slate-950/85 border border-white/10 rounded-2xl p-5 shadow-2xl overflow-hidden font-mono text-xs">
                  {/* Console Header Bar */}
                  <div className="flex items-center justify-between pb-3 mb-4 border-b border-white/10 text-slate-400">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                      <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                      <span className="ml-2 text-[11px] text-slate-300 font-sans font-semibold">
                        Telemetry Simulator: {activeCapability.label}
                      </span>
                    </div>
                    <span className="text-[10px] text-cyan-400 bg-cyan-950/60 border border-cyan-500/30 px-2 py-0.5 rounded">
                      READ_ONLY DEMO
                    </span>
                  </div>

                  {/* Dynamic Preview View based on Tab */}
                  {activeCapability.previewType === 'curriculum' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-blue-950/30 border border-blue-500/20 text-blue-200">
                        <div className="text-[11px] text-blue-400 uppercase font-bold mb-1">Scheme Validator Active</div>
                        <div className="text-white font-semibold">B.Tech Computer Science (Scheme 2026-R)</div>
                        <div className="text-[11px] text-slate-400 mt-1">Prerequisite Check: Sequential DAG Validated (0 Cycles)</div>
                      </div>
                      <div className="grid grid-cols-2 gap-2 text-[11px]">
                        <div className="p-2.5 rounded bg-slate-900 border border-white/5">
                          <span className="text-slate-400">Course Outcome (CO):</span>
                          <div className="text-emerald-400 font-bold mt-0.5">CO-1 to PO-4 Attainment: 92%</div>
                        </div>
                        <div className="p-2.5 rounded bg-slate-900 border border-white/5">
                          <span className="text-slate-400">BoS Approval Hash:</span>
                          <div className="text-violet-400 font-bold mt-0.5 font-mono text-[10px]">0x7F2...8E9 (Signed)</div>
                        </div>
                      </div>
                      <div className="p-2.5 rounded bg-slate-900/60 text-[11px] text-slate-400 border border-dashed border-white/10">
                        Sample Institutional Scheme: Enforcing 160 Minimum Total Credits
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'timetable' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-cyan-950/30 border border-cyan-500/20 text-cyan-200">
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] text-cyan-400 uppercase font-bold">Constraint Solver: Complete</span>
                          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                        </div>
                        <div className="text-white font-semibold mt-1">Zero Clashes in 1,240 Course Allocations</div>
                        <div className="text-[11px] text-slate-400 mt-1">Solver latency: 42ms • Room occupancy: 91.4%</div>
                      </div>
                      <div className="space-y-1.5 text-[11px]">
                        <div className="flex items-center justify-between p-2 rounded bg-slate-900 border border-white/5">
                          <span className="text-slate-300">Room LH-301: CS302 (Algorithms)</span>
                          <span className="text-emerald-400 font-bold">Allocated (60/60)</span>
                        </div>
                        <div className="flex items-center justify-between p-2 rounded bg-slate-900 border border-white/5">
                          <span className="text-slate-300">Lab NL-04: AI Systems Lab</span>
                          <span className="text-cyan-400 font-bold">Biometric Ready</span>
                        </div>
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'compliance' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-violet-950/30 border border-violet-500/20 text-violet-200">
                        <div className="text-[11px] text-violet-400 uppercase font-bold mb-1">Tamper-Proof Ledger</div>
                        <div className="text-white font-semibold">Semester VI End-Term Examination</div>
                        <div className="text-[11px] text-slate-400 mt-1">Audit status: Cryptographic seals locked on 4,820 papers</div>
                      </div>
                      <div className="p-2.5 rounded bg-slate-900 border border-white/5 text-[11px] space-y-1">
                        <div className="flex justify-between">
                          <span className="text-slate-400">Moderator 1 Score:</span>
                          <span className="text-white">78 / 100</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-400">Moderator 2 Score:</span>
                          <span className="text-white">80 / 100</span>
                        </div>
                        <div className="flex justify-between pt-1 border-t border-white/10 font-bold">
                          <span className="text-emerald-400">Final Resolved:</span>
                          <span className="text-emerald-400">79.0 (Variance: 1.0%)</span>
                        </div>
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'governance' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-amber-950/30 border border-amber-500/20 text-amber-200">
                        <div className="text-[11px] text-amber-400 uppercase font-bold mb-1">Multi-Tenant Isolation</div>
                        <div className="text-white font-semibold">Decentralized College Cluster Sync</div>
                        <div className="text-[11px] text-slate-400 mt-1">Row-Level Security: Active • Foreign Key Isolation: Enforced</div>
                      </div>
                      <div className="p-2.5 rounded bg-slate-900 border border-white/5 text-[11px] flex items-center justify-between">
                        <span className="text-slate-400">Accreditation Telemetry:</span>
                        <span className="text-cyan-400 font-bold">NAAC Criterion 2: 100% Ready</span>
                      </div>
                    </div>
                  )}

                  <div className="mt-4 pt-3 border-t border-white/10 flex items-center justify-between text-[11px] text-slate-500">
                    <span>Target Ref: azulbaxszsqxymqmlwfe</span>
                    <span className="text-emerald-400/90 flex items-center gap-1 font-semibold">
                      <CheckCircle2 className="w-3 h-3" /> Sample Feed Synchronized
                    </span>
                  </div>
                </div>
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      </section>

      {/* 7. ROLE WORKSPACES (Looping Horizontal Persona Slider with Keyboard & Pause on Hover) */}
      <section
        id="personas"
        className="py-24 relative overflow-hidden"
        onMouseEnter={() => setIsPersonaPaused(true)}
        onMouseLeave={() => setIsPersonaPaused(false)}
        onTouchStart={() => setIsPersonaPaused(true)}
        onTouchEnd={() => setIsPersonaPaused(false)}
        aria-label="Role Workspaces Slider"
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between mb-12">
            <div>
              <span className="text-xs font-bold uppercase tracking-widest text-cyan-400 bg-cyan-400/10 border border-cyan-400/20 px-3.5 py-1 rounded-full">
                Persona Isolation
              </span>
              <h2 className="font-display font-extrabold text-3xl sm:text-4xl text-white mt-4 tracking-tight">
                Tailored Cockpits for Every Stakeholder.
              </h2>
            </div>
            <div className="flex items-center gap-3 mt-4 md:mt-0">
              <button
                onClick={() =>
                  setActivePersonaIdx(
                    (prev) => (prev - 1 + PLACEHOLDERS.personas.length) % PLACEHOLDERS.personas.length
                  )
                }
                className="w-10 h-10 rounded-xl bg-slate-900 border border-white/10 flex items-center justify-center text-slate-300 hover:text-white hover:border-cyan-400/40 focus-ring"
                aria-label="Previous persona"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={() =>
                  setActivePersonaIdx((prev) => (prev + 1) % PLACEHOLDERS.personas.length)
                }
                className="w-10 h-10 rounded-xl bg-slate-900 border border-white/10 flex items-center justify-center text-slate-300 hover:text-white hover:border-cyan-400/40 focus-ring"
                aria-label="Next persona"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Persona Role Selection Tabs (Keyboard Accessible) */}
          <div
            className="flex flex-wrap gap-2.5 mb-8"
            role="tablist"
            aria-label="Role-isolated institutional workspaces"
          >
            {PLACEHOLDERS.personas.map((persona, idx) => {
              const isSelected = activePersonaIdx === idx;
              return (
                <button
                  key={persona.id}
                  id={`persona-tab-${idx}`}
                  role="tab"
                  aria-selected={isSelected}
                  aria-controls={`persona-panel-${idx}`}
                  tabIndex={isSelected ? 0 : -1}
                  onKeyDown={(e) => handlePersonaKeyDown(e, idx)}
                  onClick={() => setActivePersonaIdx(idx)}
                  className={`px-5 py-2.5 rounded-xl text-xs sm:text-sm font-bold transition-all focus-ring ${
                    isSelected
                      ? 'bg-gradient-to-r from-blue-600 to-violet-600 text-white shadow-lg shadow-blue-900/50 border border-white/20'
                      : 'bg-slate-900/80 text-slate-400 hover:text-slate-200 border border-white/8'
                  }`}
                >
                  {persona.title}
                </button>
              );
            })}
          </div>

          {/* Persona Display Area with Dedicated Anime-Inspired Illustration */}
          <AnimatePresence mode="wait">
            <motion.div
              key={activePersona.id}
              role="tabpanel"
              id={`persona-panel-${activePersonaIdx}`}
              aria-labelledby={`persona-tab-${activePersonaIdx}`}
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.4, ease: 'easeOut' }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center bg-slate-900/70 border border-white/10 rounded-3xl p-6 sm:p-10 backdrop-blur-xl"
            >
              {/* Left Column: Dedicated Character Illustration */}
              <div className="lg:col-span-5 flex justify-center">
                <RoleIllustration role={activePersona.role} />
              </div>

              {/* Right Column: Persona Description and Responsibilities */}
              <div className="lg:col-span-7">
                <div className="flex items-center gap-2 text-xs font-semibold text-cyan-400 uppercase tracking-widest mb-2">
                  <Activity className="w-4 h-4" />
                  <span>{activePersona.subtitle}</span>
                </div>

                <h3 className="font-display font-bold text-2xl sm:text-3xl text-white tracking-tight mb-3">
                  {activePersona.tagline}
                </h3>

                <p className="text-slate-300 text-sm sm:text-base leading-relaxed mb-6">
                  {activePersona.description}
                </p>

                <div className="p-4 rounded-xl bg-slate-950/70 border border-white/10 mb-6">
                  <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
                    Core Operational Cockpit Features
                  </h4>
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs text-slate-300">
                    {activePersona.responsibilities.map((resp, rIdx) => (
                      <li key={rIdx} className="flex items-start gap-2">
                        <CheckCircle2 className="w-3.5 h-3.5 text-cyan-400 shrink-0 mt-0.5" />
                        <span>{resp}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="flex items-center justify-between pt-4 border-t border-white/10 text-xs">
                  <span className="text-slate-400 font-mono italic">
                    {activePersona.metricsPlaceholder}
                  </span>
                  <span className="text-[11px] text-cyan-400 font-semibold">
                    Isolated Security Perimeter ✓
                  </span>
                </div>
              </div>
            </motion.div>
          </AnimatePresence>
        </div>
      </section>

      {/* 8. IMPACT (Count-Up Statistics Strip with Icons & Sample Markers) */}
      <section id="impact" className="py-20 relative bg-[#0B1020] border-y border-white/10" aria-label="Institutional Impact">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-14">
            <span className="text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-400/10 border border-amber-400/20 px-3.5 py-1 rounded-full">
              Measurable Performance
            </span>
            <h2 className="font-display font-extrabold text-3xl sm:text-4xl text-white mt-4 tracking-tight">
              Institutional Reliability by the Numbers.
            </h2>
            <p className="text-slate-400 text-sm mt-3">
              Configured targets demonstrate computational throughput and governance audit capabilities.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {PLACEHOLDERS.impactMetrics.map((metric, idx) => {
              const icons = [ShieldCheck, Lock, Building, Activity];
              const IconComp = icons[idx % icons.length];
              const colors = [
                'text-blue-400 bg-blue-500/15 border-blue-500/30',
                'text-violet-400 bg-violet-500/15 border-violet-500/30',
                'text-cyan-400 bg-cyan-500/15 border-cyan-500/30',
                'text-emerald-400 bg-emerald-500/15 border-emerald-500/30',
              ][idx % 4];

              return (
                <motion.div
                  key={metric.id}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-40px' }}
                  transition={{ duration: 0.5, delay: idx * 0.1 }}
                  className="p-6 rounded-2xl bg-slate-900/80 border border-white/10 relative overflow-hidden group hover:border-cyan-400/40 transition-colors"
                >
                  {/* Glowing icon */}
                  <div className={`w-12 h-12 rounded-xl border flex items-center justify-center mb-5 ${colors}`}>
                    <IconComp className="w-6 h-6" />
                  </div>

                  {/* Animated Counter with Sample Marker */}
                  <div className="flex items-baseline gap-2 mb-2">
                    <span className="text-4xl sm:text-5xl font-display font-extrabold text-white tracking-tight">
                      <AnimatedCounter
                        target={metric.target}
                        decimals={metric.decimals}
                        prefix={metric.prefix}
                        suffix={metric.suffix}
                      />
                    </span>
                    {metric.isSample && (
                      <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-white/10" title="Demonstration placeholder metric">
                        Sample
                      </span>
                    )}
                  </div>

                  <h3 className="text-sm font-bold text-white tracking-tight mb-1">
                    {metric.label}
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed border-t border-white/5 pt-2.5 mt-2">
                    {metric.description}
                  </p>
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>

      {/* 9. "SEE HOW IT WORKS" (Walkthrough Area with Video/Terminal Slot) */}
      <section id="walkthrough" className="py-24 relative overflow-hidden">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-xs font-bold uppercase tracking-widest text-rose-400 bg-rose-400/10 border border-rose-400/20 px-3.5 py-1 rounded-full">
              System Execution Walkthrough
            </span>
            <h2 className="font-display font-extrabold text-3xl sm:text-4xl lg:text-5xl text-white mt-4 tracking-tight">
              See How CampXSync Executes.
            </h2>
            <p className="text-slate-300 text-base mt-4">
              From scheme onboarding to live telemetry, experience how our transactional engine operates in three straightforward phases.
            </p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            {/* Left Steps List */}
            <div className="lg:col-span-5 space-y-4">
              {PLACEHOLDERS.walkthroughSteps.map((step, idx) => {
                const isCurrent = activeWalkthroughStep === idx;
                return (
                  <button
                    key={step.step}
                    onClick={() => setActiveWalkthroughStep(idx)}
                    className={`w-full text-left p-5 rounded-2xl border transition-all duration-300 focus-ring ${
                      isCurrent
                        ? 'bg-slate-900/90 border-cyan-500/40 shadow-xl shadow-cyan-950/30'
                        : 'bg-slate-950/40 border-white/8 hover:border-white/20'
                    }`}
                  >
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded ${
                        isCurrent ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30' : 'bg-slate-800 text-slate-400'
                      }`}>
                        STEP {step.step}
                      </span>
                      <h3 className="text-base font-bold text-white tracking-tight">
                        {step.title}
                      </h3>
                    </div>
                    <p className="text-xs text-slate-400 leading-relaxed pl-1">
                      {step.description}
                    </p>
                  </button>
                );
              })}
            </div>

            {/* Right Product Walkthrough & Video Placeholder Slot */}
            <div className="lg:col-span-7 bg-slate-900/80 border border-white/10 rounded-3xl p-6 sm:p-8 backdrop-blur-xl relative overflow-hidden">
              <div className="relative aspect-video rounded-2xl bg-[#070B14] border border-white/10 flex flex-col items-center justify-center p-6 text-center overflow-hidden group">
                {/* Decorative mesh */}
                <div className="absolute inset-0 bg-gradient-to-tr from-blue-900/30 via-violet-900/20 to-cyan-900/30 -z-0" />

                {/* Video Play Placeholder Button */}
                <div className="relative z-10 w-16 h-16 rounded-2xl bg-rose-500/20 border border-rose-500/40 flex items-center justify-center text-rose-400 mb-4 shadow-xl shadow-rose-950/60 transition-transform group-hover:scale-110">
                  <Play className="w-7 h-7 fill-rose-400 ml-1" />
                </div>

                <div className="relative z-10">
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/10 text-slate-300 uppercase tracking-widest">
                    Interactive Walkthrough Slot
                  </span>
                  <h4 className="text-lg font-bold text-white mt-2">
                    CampXSync Technical Overview Video
                  </h4>
                  <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
                    Dedicated slot for university leadership video briefing and live architecture walkthrough demonstration.
                  </p>
                </div>
              </div>

              <div className="mt-4 flex items-center justify-between text-xs text-slate-400">
                <span className="flex items-center gap-1.5 font-mono">
                  <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                  Active Phase: {PLACEHOLDERS.walkthroughSteps[activeWalkthroughStep].title}
                </span>
                <span className="text-cyan-400 font-semibold cursor-pointer hover:underline" onClick={() => setIsDemoModalOpen(true)}>
                  Schedule Live Interactive Walkthrough →
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 10. CLOSING HIGH-ENERGY CTA BAND & FOOTER */}
      <section className="relative py-24 bg-gradient-to-b from-[#070B14] via-[#0D1527] to-[#070B14] border-t border-white/10 overflow-hidden">
        {/* Ambient energy orbs */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[400px] bg-rose-600/15 blur-[120px] rounded-full -z-10 pointer-events-none" />

        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <span className="text-xs font-bold uppercase tracking-widest text-cyan-400 bg-cyan-400/10 border border-cyan-400/20 px-3.5 py-1 rounded-full">
            Modernize Your Academic Enterprise
          </span>
          <h2 className="font-display font-extrabold text-4xl sm:text-5xl lg:text-6xl text-white tracking-tight mt-6 mb-6 leading-tight">
            Ready to Unify Your Campus <br />
            <span className="gradient-text font-black">Under One Sovereign Core?</span>
          </h2>
          <p className="text-lg sm:text-xl text-slate-300 max-w-2xl mx-auto mb-10 leading-relaxed">
            Eliminate scheduling deadlocks, automate attendance verification, and ensure audit-ready compliance across all your academic units.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <button
              onClick={() => setIsDemoModalOpen(true)}
              className="w-full sm:w-auto relative group px-8 py-4 rounded-xl text-base font-bold text-white overflow-hidden shadow-2xl shadow-rose-950/60 transition-all duration-300 hover:-translate-y-1 active:translate-y-0 focus-ring"
            >
              <div className="absolute inset-0 bg-gradient-to-r from-rose-500 via-rose-600 to-amber-500 transition-transform duration-300 group-hover:scale-105" />
              <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity" />
              <span className="relative flex items-center justify-center gap-2">
                Request Executive Briefing
                <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1.5" />
              </span>
            </button>

            <Link
              href="/login"
              className="w-full sm:w-auto px-7 py-4 rounded-xl text-base font-semibold text-slate-200 bg-slate-900/80 hover:bg-slate-800 border border-white/10 hover:border-cyan-400/40 transition-colors text-center focus-ring"
            >
              Access Institutional Workspace
            </Link>
          </div>
        </div>
      </section>

      {/* 11. INSTITUTIONAL FOOTER */}
      <footer className="bg-[#05080F] border-t border-white/5 py-12 text-xs text-slate-400">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-10">
            <div className="space-y-3">
              <Logo size="md" />
              <p className="text-slate-400 text-xs leading-relaxed max-w-xs">
                {PLACEHOLDERS.brand.tagline}
              </p>
              <div className="flex items-center gap-2 text-[11px] text-emerald-400">
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
                <span>All Institutional Services Operational</span>
              </div>
            </div>

            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-3">
                Core Architecture
              </h4>
              <ul className="space-y-2 text-slate-400">
                <li><a href="#foundation" className="hover:text-cyan-400 transition-colors">Curriculum Schemes</a></li>
                <li><a href="#foundation" className="hover:text-cyan-400 transition-colors">Deterministic Timetable</a></li>
                <li><a href="#foundation" className="hover:text-cyan-400 transition-colors">Examination Ledger</a></li>
                <li><a href="#foundation" className="hover:text-cyan-400 transition-colors">Executive Governance</a></li>
              </ul>
            </div>

            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-3">
                Workspaces
              </h4>
              <ul className="space-y-2 text-slate-400">
                <li><a href="#personas" className="hover:text-cyan-400 transition-colors">Scholars & Students</a></li>
                <li><a href="#personas" className="hover:text-cyan-400 transition-colors">Faculty & Mentors</a></li>
                <li><a href="#personas" className="hover:text-cyan-400 transition-colors">Deans & Academic Chairs</a></li>
                <li><a href="#personas" className="hover:text-cyan-400 transition-colors">University Core Chancellors</a></li>
              </ul>
            </div>

            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-3">
                Governance & Security
              </h4>
              <ul className="space-y-2 text-slate-400">
                <li><span className="hover:text-white cursor-pointer">Multi-Tenant Isolation</span></li>
                <li><span className="hover:text-white cursor-pointer">Cryptographic Audit Trails</span></li>
                <li><span className="hover:text-white cursor-pointer">NAAC / NBA / ABET Readiness</span></li>
                <li><span className="hover:text-white cursor-pointer">Sovereign Cloud Deployment</span></li>
              </ul>
            </div>
          </div>

          <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-slate-500">
            <div>
              © 2026 {PLACEHOLDERS.brand.name}. Higher Education Operating System. All rights reserved.
            </div>
            <div className="flex items-center gap-6">
              <span>Security Controls</span>
              <span>•</span>
              <span>Privacy Policy</span>
              <span>•</span>
              <span>Institutional SLA</span>
            </div>
          </div>
        </div>
      </footer>

      {/* 12. DEMO REQUEST MODAL (Accessible Dialog) */}
      <AnimatePresence>
        {isDemoModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={closeDemoModal}
              className="absolute inset-0 bg-black/80 backdrop-blur-md"
              aria-hidden="true"
            />

            <motion.div
              initial={{ scale: 0.95, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.95, opacity: 0, y: 20 }}
              transition={{ duration: 0.25 }}
              className="relative w-full max-w-lg bg-[#0D1527] border border-white/15 rounded-3xl p-6 sm:p-8 shadow-2xl shadow-blue-950/80 z-10 text-white"
              role="dialog"
              aria-modal="true"
              aria-labelledby="demo-modal-title"
            >
              <button
                onClick={closeDemoModal}
                className="absolute top-5 right-5 p-2 rounded-xl bg-slate-800/60 border border-white/10 text-slate-400 hover:text-white focus-ring"
                aria-label="Close dialog"
              >
                <X className="w-5 h-5" />
              </button>

              {demoSubmitted ? (
                <div className="text-center py-8">
                  <div className="w-16 h-16 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center mx-auto mb-4">
                    <CheckCircle2 className="w-8 h-8" />
                  </div>
                  <h3 id="demo-modal-title" className="text-2xl font-bold font-display text-white mb-2">
                    Demonstration Request Confirmed
                  </h3>
                  <p className="text-sm text-slate-300 leading-relaxed max-w-sm mx-auto mb-6">
                    Our academic architecture team will reach out with a sandbox tailored to{' '}
                    <span className="text-cyan-400 font-semibold">{demoForm.institution || 'your institution'}</span>.
                  </p>
                  <button
                    onClick={closeDemoModal}
                    className="px-6 py-2.5 rounded-xl bg-slate-800 text-white text-sm font-semibold hover:bg-slate-700 transition-colors focus-ring"
                  >
                    Close Window
                  </button>
                </div>
              ) : (
                <>
                  <div className="mb-6">
                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-cyan-300 text-xs font-semibold mb-2">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Institutional Briefing</span>
                    </div>
                    <h3 id="demo-modal-title" className="text-2xl font-bold font-display text-white">
                      Request CampXSync Architecture Briefing
                    </h3>
                    <p className="text-xs text-slate-400 mt-1">
                      Experience conflict-free scheduling and real-time governance on your curriculum data.
                    </p>
                  </div>

                  <form onSubmit={handleDemoSubmit} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                        Your Full Name
                      </label>
                      <input
                        type="text"
                        required
                        value={demoForm.name}
                        onChange={(e) => setDemoForm({ ...demoForm, name: e.target.value })}
                        placeholder="Dr. Katherine Vance"
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-white/10 text-white text-sm placeholder:text-slate-500 focus-ring"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                        University or Institution Name
                      </label>
                      <input
                        type="text"
                        required
                        value={demoForm.institution}
                        onChange={(e) => setDemoForm({ ...demoForm, institution: e.target.value })}
                        placeholder="State University of Technology"
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-white/10 text-white text-sm placeholder:text-slate-500 focus-ring"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                        Institutional Role
                      </label>
                      <select
                        value={demoForm.role}
                        onChange={(e) => setDemoForm({ ...demoForm, role: e.target.value })}
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-white/10 text-white text-sm focus-ring"
                      >
                        <option>Dean / Department Chair</option>
                        <option>Registrar / Controller of Examinations</option>
                        <option>Chancellor / Provost</option>
                        <option>Chief Information Officer (CIO)</option>
                        <option>Senior Faculty Member</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                        Institutional Email Address
                      </label>
                      <input
                        type="email"
                        required
                        value={demoForm.email}
                        onChange={(e) => setDemoForm({ ...demoForm, email: e.target.value })}
                        placeholder="dean.office@university.edu"
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-white/10 text-white text-sm placeholder:text-slate-500 focus-ring"
                      />
                    </div>

                    <button
                      type="submit"
                      className="w-full mt-2 py-3 rounded-xl bg-gradient-to-r from-rose-500 via-rose-600 to-amber-500 text-white font-bold text-sm shadow-xl shadow-rose-950/50 hover:brightness-110 transition-all flex items-center justify-center gap-2 focus-ring"
                    >
                      <span>Submit Request</span>
                      <Send className="w-4 h-4" />
                    </button>
                  </form>
                </>
              )}
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
