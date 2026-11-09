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
    <span ref={ref} className="tabular-nums font-semibold tracking-tight">
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
    <div className="relative min-h-screen bg-[#F6F8FB] text-[#1B2433] selection:bg-[#E8F0FE] selection:text-[#0B1F3A] overflow-x-hidden">
      {/* 1. SCROLL PROGRESS INDICATOR */}
      <motion.div
        className="fixed top-0 left-0 right-0 h-[3px] bg-[#1F6FEB] z-50 origin-left"
        style={{ scaleX }}
      />

      {/* 2. STICKY FROSTED NAVIGATION HEADER */}
      <header className="fixed top-0 left-0 right-0 z-40 h-16 border-b border-[#E3E8EF] bg-white">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <Logo size="md" />

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-[#3E4C63]" aria-label="Main Navigation">
            <a
              href="#foundation"
              className="transition-colors hover:text-[#1F6FEB] focus-ring rounded-md px-1 py-0.5"
            >
              Foundations
            </a>
            <a
              href="#capabilities"
              className="transition-colors hover:text-[#1F6FEB] focus-ring rounded-md px-1 py-0.5"
            >
              Capabilities
            </a>
            <a
              href="#personas"
              className="transition-colors hover:text-[#1F6FEB] focus-ring rounded-md px-1 py-0.5"
            >
              Role Workspaces
            </a>
            <a
              href="#impact"
              className="transition-colors hover:text-[#1F6FEB] focus-ring rounded-md px-1 py-0.5"
            >
              Impact
            </a>
            <a
              href="#walkthrough"
              className="transition-colors hover:text-[#1F6FEB] focus-ring rounded-md px-1 py-0.5"
            >
              Architecture
            </a>
          </nav>

          {/* Right Action CTAs */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              href="/login"
              className="px-4 py-2 text-sm font-semibold text-[#3E4C63] hover:text-[#0B1F3A] transition-colors focus-ring rounded-lg border border-transparent hover:border-[#E3E8EF]"
            >
              Workspace Sign In
            </Link>
            <button
              onClick={() => setIsDemoModalOpen(true)}
              className="relative group px-5 py-2.5 rounded-[8px] text-sm font-bold text-white overflow-hidden bg-[#1F6FEB]  transition-all duration-300 hover:-translate-y-0.5 active:translate-y-0 focus-ring"
            >
              {/* Vibrant Coral to Amber gradient background */}
              <div className="absolute inset-0 bg-[#1F6FEB] transition-transform duration-300 group-hover:scale-105" />
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
            className="md:hidden p-2 rounded-lg bg-[#F6F8FB] border border-[#E3E8EF] text-[#3E4C63] hover:text-[#0B1F3A] focus-ring"
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
              className="md:hidden bg-white border-b border-[#E3E8EF] px-6 py-6 overflow-hidden"
            >
              <div className="flex flex-col gap-4 text-base font-medium text-[#1B2433]">
                <a
                  href="#foundation"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-[#1F6FEB] transition-colors"
                >
                  Foundations
                </a>
                <a
                  href="#capabilities"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-[#1F6FEB] transition-colors"
                >
                  Capabilities
                </a>
                <a
                  href="#personas"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-[#1F6FEB] transition-colors"
                >
                  Role Workspaces
                </a>
                <a
                  href="#impact"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-[#1F6FEB] transition-colors"
                >
                  Impact
                </a>
                <a
                  href="#walkthrough"
                  onClick={() => setMobileMenuOpen(false)}
                  className="py-1 hover:text-[#1F6FEB] transition-colors"
                >
                  Architecture
                </a>
                <div className="pt-4 border-t border-[#E3E8EF] flex flex-col gap-3">
                  <Link
                    href="/login"
                    onClick={() => setMobileMenuOpen(false)}
                    className="w-full text-center py-2.5 rounded-lg border border-[#E3E8EF] text-[#1B2433] font-semibold"
                  >
                    Workspace Sign In
                  </Link>
                  <button
                    onClick={() => {
                      setMobileMenuOpen(false);
                      setIsDemoModalOpen(true);
                    }}
                    className="w-full text-center py-2.5 rounded-lg bg-[#1F6FEB] text-white font-bold shadow-lg"
                  >
                    See the record
                  </button>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </header>

      {/* 3. HERO SECTION WITH FLOWING GRADIENT MESH & FOCAL VISUAL */}
      <section className="relative pt-24 pb-16 md:pt-28 md:pb-20 overflow-hidden">
        {/* Slow flowing ambient mesh blobs (24s cycle) */}

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
                className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-white border border-[#1F6FEB]/25 text-[#1F6FEB] text-xs font-semibold tracking-wide  mb-6"
              >
                <span className="w-2 h-2 rounded-full bg-[#1F6FEB] animate-pulse" />
                <span>College operating record</span>
                <span className="text-[#6B7688] font-mono">v2.0</span>
              </motion.div>

              {/* Headline with Confident Line Breaks & Animated Gradient Accent */}
              <motion.h1
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="font-display font-semibold text-4xl sm:text-5xl lg:text-6xl tracking-tight leading-[1.08] text-[#0B1F3A] mb-6"
              >
                One college record, <br className="hidden sm:inline" />
                <span className="gradient-text font-semibold">from roster to result.</span>
              </motion.h1>

              {/* Short Supporting Copy */}
              <motion.p
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  visible: { opacity: 1, y: 0 },
                }}
                className="text-lg sm:text-xl text-[#3E4C63] max-w-2xl mx-auto lg:mx-0 font-normal leading-relaxed mb-8"
              >
                The college admin keeps students, faculty, the timetable, attendance, office fees, exams, staff pay, one campus, and in-app notices.
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
                  className="w-full sm:w-auto relative group px-7 py-4 rounded-[8px] text-base font-bold text-white overflow-hidden bg-[#1F6FEB]  transition-all duration-300 hover:-translate-y-1 active:translate-y-0 focus-ring"
                >
                  <div className="absolute inset-0 bg-[#1F6FEB] transition-transform duration-300 group-hover:scale-105" />
                  <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                  <span className="relative flex items-center justify-center gap-2.5">
                    See the record
                    <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1.5" />
                  </span>
                </button>

                <a
                  href="#foundation"
                  className="w-full sm:w-auto px-6 py-4 rounded-[8px] text-base font-semibold text-[#1B2433] bg-white hover:bg-[#F6F8FB] border border-[#E3E8EF] hover:border-[#1F6FEB] transition-all duration-200 text-center focus-ring"
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
                className="mt-8 flex items-center justify-center lg:justify-start gap-3 text-xs text-[#6B7688]"
              >
                <span className="inline-flex items-center gap-1.5 text-emerald-400 font-medium">
                  <ShieldCheck className="w-4 h-4" />
                  College admin creates the rows
                </span>
                <span className="text-[#6B7688]">•</span>
                <span>Faculty mark attendance</span>
                <span className="text-[#6B7688]">•</span>
                <span className="text-[#1F6FEB] font-mono">No SGPA, no gateway</span>
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
      <section className="relative py-8 border-y border-[#E3E8EF] bg-white overflow-hidden select-none" aria-label="Ecosystem Partners">
        <div className="max-w-7xl mx-auto px-4 mb-3 flex items-center justify-between text-xs font-semibold text-[#6B7688] uppercase tracking-widest">
          <span>Institutional Architecture Compatibility</span>
          <span className="text-[11px] text-[#1F6FEB] font-normal lowercase tracking-normal">
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
                className="flex items-center gap-3 px-5 py-3 rounded-[8px] bg-white border border-[#E3E8EF] text-[#3E4C63] whitespace-nowrap hover:border-[#1F6FEB] transition-colors"
              >
                <div className="w-7 h-7 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400 font-bold text-xs">
                  {partner.name.charAt(0)}
                </div>
                <div className="flex flex-col">
                  <span className="text-sm font-semibold text-[#0B1F3A] tracking-tight">{partner.name}</span>
                  <span className="text-[10px] text-[#6B7688] font-mono">{partner.category} [Placeholder]</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 5. "ONE SHARED FOUNDATION" (Four Pillars with Sleek Glowing Vectors) */}
      <section id="foundation" className="py-24 relative overflow-hidden">
        {/* Ambient background glow */}

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-xs font-bold uppercase tracking-widest text-[#1F6FEB] bg-[#E8F0FE] border border-[#1F6FEB]/20 px-3.5 py-1 rounded-full">
              Enterprise Governance
            </span>
            <h2 className="font-display font-semibold text-3xl sm:text-4xl lg:text-5xl tracking-tight text-[#0B1F3A] mt-4 mb-5">
              One college record. <br />
              <span className="text-[#6B7688] font-normal">Four Interlocking Pillars.</span>
            </h2>
            <p className="text-[#3E4C63] text-base sm:text-lg leading-relaxed">
              Academic, learning, examinations, and college operations are four views of the same college record.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {PLACEHOLDERS.pillars.map((pillar, idx) => {
              const icons = [Compass, CalendarCheck2, FileCheck2, Building];
              const IconComp = icons[idx % icons.length];
              const colors = [
                { border: 'hover:border-blue-500/40', glow: 'group-hover:text-blue-400', bg: 'bg-blue-500/10' },
                { border: 'hover:border-cyan-500/40', glow: 'group-hover:text-[#1F6FEB]', bg: 'bg-cyan-500/10' },
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
                  className={`group relative p-6 rounded-[12px] bg-gradient-to-b from-slate-900/80 to-slate-950/80 border border-[#E3E8EF] ${colors.border} transition-all duration-300 hover:-translate-y-1.5 shadow-xl`}
                >
                  <div className="flex items-center justify-between mb-5">
                    <div className={`w-12 h-12 rounded-[8px] ${colors.bg} border border-[#E3E8EF] flex items-center justify-center text-[#1B2433] ${colors.glow} transition-colors`}>
                      <IconComp className="w-6 h-6" />
                    </div>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-[#6B7688] border border-[#E3E8EF]">
                      {pillar.badge}
                    </span>
                  </div>

                  <h3 className="text-xl font-bold font-display text-[#0B1F3A] tracking-tight mb-2">
                    {pillar.title}
                  </h3>
                  <p className="text-xs text-[#1F6FEB] font-semibold mb-4 tracking-wide uppercase">
                    {pillar.subtitle}
                  </p>

                  <ul className="space-y-3 text-xs text-[#3E4C63] leading-relaxed border-t border-[#E3E8EF] pt-4">
                    {pillar.features.map((feat, fIdx) => (
                      <li key={fIdx} className="flex items-start gap-2">
                        <Check className="w-3.5 h-3.5 text-[#1F6FEB] shrink-0 mt-0.5" />
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
      <section id="capabilities" className="py-24 relative bg-white border-y border-[#E3E8EF]">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between mb-12">
            <div>
              <span className="text-xs font-bold uppercase tracking-widest text-violet-400 bg-violet-400/10 border border-violet-400/20 px-3.5 py-1 rounded-full">
                Technical Capabilities
              </span>
              <h2 className="font-display font-semibold text-3xl sm:text-4xl text-[#0B1F3A] mt-4 tracking-tight">
                What the workspace keeps, <span className="gradient-text font-semibold">section by section.</span>
              </h2>
            </div>
            <p className="text-[#6B7688] text-sm max-w-md mt-4 md:mt-0">
              Each tab is a part of the college record that is already built.
            </p>
          </div>

          {/* Interactive Navigation Tabs */}
          <div className="flex flex-wrap gap-2 p-1.5 rounded-[12px] bg-[#F6F8FB] border border-[#E3E8EF] max-w-3xl mb-8" role="tablist" aria-label="Capabilities Showcase">
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
                  className={`relative flex-1 min-w-[140px] py-3 px-4 rounded-[8px] text-xs sm:text-sm font-bold transition-all focus-ring ${
                    isActive ? 'text-white' : 'text-[#6B7688] hover:text-[#1B2433]'
                  }`}
                >
                  {isActive && (
                    <motion.div
                      layoutId="activeCapabilityPill"
                      transition={{ type: 'spring', stiffness: 450, damping: 32 }}
                      className="absolute inset-0 bg-[#1F6FEB] rounded-[8px] shadow-lg shadow-blue-900/40 border border-white/20 -z-0"
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
                className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center bg-white border border-[#E3E8EF] rounded-[12px] p-6 sm:p-10 backdrop-blur-md"
              >
                {/* Left Description & Bullet Points */}
                <div className="lg:col-span-6">
                  <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-[#1F6FEB]/25 text-[#1F6FEB] text-xs font-semibold mb-4">
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>{activeCapability.badge}</span>
                  </div>

                  <h3 className="font-display font-bold text-2xl sm:text-3xl text-[#0B1F3A] tracking-tight leading-tight mb-4">
                    {activeCapability.title}
                  </h3>

                  <p className="text-[#3E4C63] text-sm sm:text-base leading-relaxed mb-6">
                    {activeCapability.description}
                  </p>

                  <div className="space-y-4">
                    {activeCapability.bullets.map((bullet, bIdx) => (
                      <div key={bIdx} className="flex items-start gap-3 p-3.5 rounded-[8px] bg-slate-950/60 border border-[#E3E8EF]">
                        <div className="w-6 h-6 rounded-md bg-cyan-500/15 border border-[#1F6FEB]/25 flex items-center justify-center text-[#1F6FEB] shrink-0 mt-0.5">
                          <Check className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <h4 className="text-xs sm:text-sm font-bold text-[#0B1F3A] tracking-wide">
                            {bullet.title}
                          </h4>
                          <p className="text-xs text-[#6B7688] mt-0.5 leading-relaxed">
                            {bullet.desc}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right Interactive Simulator Preview Panel */}
                <div className="lg:col-span-6 bg-slate-950/85 border border-[#E3E8EF] rounded-[12px] p-5 shadow-2xl overflow-hidden font-mono text-xs">
                  {/* Console Header Bar */}
                  <div className="flex items-center justify-between pb-3 mb-4 border-b border-[#E3E8EF] text-[#6B7688]">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                      <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                      <span className="ml-2 text-[11px] text-[#3E4C63] font-sans font-semibold">
                        Telemetry Simulator: {activeCapability.label}
                      </span>
                    </div>
                    <span className="text-[10px] text-[#1F6FEB] bg-cyan-950/60 border border-[#1F6FEB]/25 px-2 py-0.5 rounded">
                      READ_ONLY DEMO
                    </span>
                  </div>

                  {/* Dynamic Preview View based on Tab */}
                  {activeCapability.previewType === 'curriculum' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-blue-950/30 border border-blue-500/20 text-blue-200">
                        <div className="text-[11px] text-blue-400 uppercase font-bold mb-1">Academic record</div>
                        <div className="text-[#0B1F3A] font-semibold">Programme, batch, and subject</div>
                        <div className="text-[11px] text-[#6B7688] mt-1">Created by the college admin. No outcome score is calculated.</div>
                      </div>
                      <div className="grid grid-cols-2 gap-2 text-[11px]">
                        <div className="p-2.5 rounded bg-[#F6F8FB] border border-[#E3E8EF]">
                          <span className="text-[#6B7688]">Timetable slot:</span>
                          <div className="text-emerald-400 font-bold mt-0.5">Subject, faculty, room, day</div>
                        </div>
                        <div className="p-2.5 rounded bg-[#F6F8FB] border border-[#E3E8EF]">
                          <span className="text-[#6B7688]">Who creates it:</span>
                          <div className="text-violet-400 font-bold mt-0.5">College admin only</div>
                        </div>
                      </div>
                      <div className="p-2.5 rounded bg-white text-[11px] text-[#6B7688] border border-dashed border-[#E3E8EF]">
                        Faculty cannot enrol students.
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'timetable' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-cyan-950/30 border border-cyan-500/20 text-cyan-200">
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] text-[#1F6FEB] uppercase font-bold">Attendance</span>
                          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                        </div>
                        <div className="text-[#0B1F3A] font-semibold mt-1">Marked on the timetable session</div>
                        <div className="text-[11px] text-[#6B7688] mt-1">A correction keeps a reason. No biometric device.</div>
                      </div>
                      <div className="space-y-1.5 text-[11px]">
                        <div className="flex items-center justify-between p-2 rounded bg-[#F6F8FB] border border-[#E3E8EF]">
                          <span className="text-[#3E4C63]">Session on the timetable</span>
                          <span className="text-emerald-400 font-bold">Faculty marks it</span>
                        </div>
                        <div className="flex items-center justify-between p-2 rounded bg-[#F6F8FB] border border-[#E3E8EF]">
                          <span className="text-[#3E4C63]">Later change</span>
                          <span className="text-[#1F6FEB] font-bold">Reason required</span>
                        </div>
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'compliance' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-violet-950/30 border border-violet-500/20 text-violet-200">
                        <div className="text-[11px] text-violet-400 uppercase font-bold mb-1">Published result</div>
                        <div className="text-[#0B1F3A] font-semibold">Subject marks and pass or fail</div>
                        <div className="text-[11px] text-[#6B7688] mt-1">No hall-ticket PDF. No SGPA.</div>
                      </div>
                      <div className="p-2.5 rounded bg-[#F6F8FB] border border-[#E3E8EF] text-[11px] space-y-1">
                        <div className="flex justify-between">
                          <span className="text-[#6B7688]">Script:</span>
                          <span className="text-[#0B1F3A]">Recorded</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-[#6B7688]">Marks:</span>
                          <span className="text-[#0B1F3A]">Entered, then published</span>
                        </div>
                        <div className="flex justify-between pt-1 border-t border-[#E3E8EF] font-bold">
                          <span className="text-emerald-400">Result:</span>
                          <span className="text-emerald-400">Pass or fail</span>
                        </div>
                      </div>
                    </div>
                  )}

                  {activeCapability.previewType === 'governance' && (
                    <div className="space-y-3 font-mono text-xs">
                      <div className="p-3 rounded-lg bg-amber-950/30 border border-amber-500/20 text-amber-200">
                        <div className="text-[11px] text-amber-400 uppercase font-bold mb-1">College operations</div>
                        <div className="text-[#0B1F3A] font-semibold">Office receipt, payslip, campus, notice</div>
                        <div className="text-[11px] text-[#6B7688] mt-1">Receipt matched at the office. Payslip calculated, not paid out.</div>
                      </div>
                      <div className="p-2.5 rounded bg-[#F6F8FB] border border-[#E3E8EF] text-[11px] flex items-center justify-between">
                        <span className="text-[#6B7688]">Notice:</span>
                        <span className="text-[#1F6FEB] font-bold">In-app, delivered</span>
                      </div>
                    </div>
                  )}

                  <div className="mt-4 pt-3 border-t border-[#E3E8EF] flex items-center justify-between text-[11px] text-[#6B7688]">
                    <span>College workspace</span>
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
              <span className="text-xs font-bold uppercase tracking-widest text-[#1F6FEB] bg-[#E8F0FE] border border-[#1F6FEB]/20 px-3.5 py-1 rounded-full">
                Persona Isolation
              </span>
              <h2 className="font-display font-semibold text-3xl sm:text-4xl text-[#0B1F3A] mt-4 tracking-tight">
                Four workspaces.
              </h2>
            </div>
            <div className="flex items-center gap-3 mt-4 md:mt-0">
              <button
                onClick={() =>
                  setActivePersonaIdx(
                    (prev) => (prev - 1 + PLACEHOLDERS.personas.length) % PLACEHOLDERS.personas.length
                  )
                }
                className="w-10 h-10 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] flex items-center justify-center text-[#3E4C63] hover:text-[#0B1F3A] hover:border-[#1F6FEB] focus-ring"
                aria-label="Previous persona"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={() =>
                  setActivePersonaIdx((prev) => (prev + 1) % PLACEHOLDERS.personas.length)
                }
                className="w-10 h-10 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] flex items-center justify-center text-[#3E4C63] hover:text-[#0B1F3A] hover:border-[#1F6FEB] focus-ring"
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
                  className={`px-5 py-2.5 rounded-[8px] text-xs sm:text-sm font-bold transition-all focus-ring ${
                    isSelected
                      ? 'bg-[#1F6FEB] text-white shadow-lg shadow-blue-900/50 border border-white/20'
                      : 'bg-white text-[#6B7688] hover:text-[#1B2433] border border-[#E3E8EF]'
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
              className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center bg-[#F6F8FB]/70 border border-[#E3E8EF] rounded-[12px] p-6 sm:p-10 backdrop-blur-xl"
            >
              {/* Left Column: Dedicated Character Illustration */}
              <div className="lg:col-span-5 flex justify-center">
                <RoleIllustration role={activePersona.role} />
              </div>

              {/* Right Column: Persona Description and Responsibilities */}
              <div className="lg:col-span-7">
                <div className="flex items-center gap-2 text-xs font-semibold text-[#1F6FEB] uppercase tracking-widest mb-2">
                  <Activity className="w-4 h-4" />
                  <span>{activePersona.subtitle}</span>
                </div>

                <h3 className="font-display font-bold text-2xl sm:text-3xl text-[#0B1F3A] tracking-tight mb-3">
                  {activePersona.tagline}
                </h3>

                <p className="text-[#3E4C63] text-sm sm:text-base leading-relaxed mb-6">
                  {activePersona.description}
                </p>

                <div className="p-4 rounded-[8px] bg-slate-950/70 border border-[#E3E8EF] mb-6">
                  <h4 className="text-xs font-bold text-[#3E4C63] uppercase tracking-wider mb-3">
                    Core Operational Cockpit Features
                  </h4>
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs text-[#3E4C63]">
                    {activePersona.responsibilities.map((resp, rIdx) => (
                      <li key={rIdx} className="flex items-start gap-2">
                        <CheckCircle2 className="w-3.5 h-3.5 text-[#1F6FEB] shrink-0 mt-0.5" />
                        <span>{resp}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="flex items-center justify-between pt-4 border-t border-[#E3E8EF] text-xs">
                  <span className="text-[#6B7688] font-mono italic">
                    {activePersona.metricsPlaceholder}
                  </span>
                  <span className="text-[11px] text-[#1F6FEB] font-semibold">
                    Isolated Security Perimeter ✓
                  </span>
                </div>
              </div>
            </motion.div>
          </AnimatePresence>
        </div>
      </section>

      {/* 8. IMPACT (Count-Up Statistics Strip with Icons & Sample Markers) */}
      <section id="impact" className="py-20 relative bg-[#F6F8FB] border-y border-[#E3E8EF]" aria-label="Institutional Impact">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-14">
            <span className="text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-400/10 border border-amber-400/20 px-3.5 py-1 rounded-full">
              Measurable Performance
            </span>
            <h2 className="font-display font-semibold text-3xl sm:text-4xl text-[#0B1F3A] mt-4 tracking-tight">
              What is on the sample college.
            </h2>
            <p className="text-[#6B7688] text-sm mt-3">
              These counts describe the sample college. They are not a performance score.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {PLACEHOLDERS.impactMetrics.map((metric, idx) => {
              const icons = [ShieldCheck, Lock, Building, Activity];
              const IconComp = icons[idx % icons.length];
              const colors = [
                'text-blue-400 bg-blue-500/15 border-blue-500/30',
                'text-violet-400 bg-violet-500/15 border-violet-500/30',
                'text-[#1F6FEB] bg-cyan-500/15 border-[#1F6FEB]/25',
                'text-emerald-400 bg-emerald-500/15 border-emerald-500/30',
              ][idx % 4];

              return (
                <motion.div
                  key={metric.id}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-40px' }}
                  transition={{ duration: 0.5, delay: idx * 0.1 }}
                  className="p-6 rounded-[12px] bg-white border border-[#E3E8EF] relative overflow-hidden group hover:border-[#1F6FEB] transition-colors"
                >
                  {/* Glowing icon */}
                  <div className={`w-12 h-12 rounded-[8px] border flex items-center justify-center mb-5 ${colors}`}>
                    <IconComp className="w-6 h-6" />
                  </div>

                  {/* Animated Counter with Sample Marker */}
                  <div className="flex items-baseline gap-2 mb-2">
                    <span className="text-4xl sm:text-5xl font-display font-semibold text-[#0B1F3A] tracking-tight">
                      <AnimatedCounter
                        target={metric.target}
                        decimals={metric.decimals}
                        prefix={metric.prefix}
                        suffix={metric.suffix}
                      />
                    </span>
                    {metric.isSample && (
                      <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-[#0B1F3A] text-[#6B7688] border border-[#E3E8EF]" title="Demonstration placeholder metric">
                        Sample
                      </span>
                    )}
                  </div>

                  <h3 className="text-sm font-bold text-[#0B1F3A] tracking-tight mb-1">
                    {metric.label}
                  </h3>
                  <p className="text-xs text-[#6B7688] leading-relaxed border-t border-[#E3E8EF] pt-2.5 mt-2">
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
            <h2 className="font-display font-semibold text-3xl sm:text-4xl lg:text-5xl text-[#0B1F3A] mt-4 tracking-tight">
              How a college record is filled.
            </h2>
            <p className="text-[#3E4C63] text-base mt-4">
              Three steps: the admin creates the rows, learning and exams follow them, then staff, campus, and a notice.
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
                    className={`w-full text-left p-5 rounded-[12px] border transition-all duration-300 focus-ring ${
                      isCurrent
                        ? 'bg-[#F6F8FB] border-cyan-500/40 shadow-xl shadow-cyan-950/30'
                        : 'bg-slate-950/40 border-[#E3E8EF] hover:border-white/20'
                    }`}
                  >
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded ${
                        isCurrent ? 'bg-cyan-500/20 text-[#1F6FEB] border border-[#1F6FEB]/25' : 'bg-[#0B1F3A] text-[#6B7688]'
                      }`}>
                        STEP {step.step}
                      </span>
                      <h3 className="text-base font-bold text-[#0B1F3A] tracking-tight">
                        {step.title}
                      </h3>
                    </div>
                    <p className="text-xs text-[#6B7688] leading-relaxed pl-1">
                      {step.description}
                    </p>
                  </button>
                );
              })}
            </div>

            {/* Right Product Walkthrough & Video Placeholder Slot */}
            <div className="lg:col-span-7 bg-white border border-[#E3E8EF] rounded-[12px] p-6 sm:p-8 backdrop-blur-xl relative overflow-hidden">
              <div className="relative aspect-video rounded-[12px] bg-[#F6F8FB] border border-[#E3E8EF] flex flex-col items-center justify-center p-6 text-center overflow-hidden group">
                {/* Video Play Placeholder Button */}
                <div className="relative z-10 w-16 h-16 rounded-[12px] bg-rose-500/20 border border-rose-500/40 flex items-center justify-center text-rose-400 mb-4 shadow-xl shadow-rose-950/60 transition-transform group-hover:scale-110">
                  <Play className="w-7 h-7 fill-rose-400 ml-1" />
                </div>

                <div className="relative z-10">
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/10 text-[#3E4C63] uppercase tracking-widest">
                    Interactive Walkthrough Slot
                  </span>
                  <h4 className="text-lg font-bold text-[#0B1F3A] mt-2">
                    Walkthrough notes
                  </h4>
                  <p className="text-xs text-[#6B7688] max-w-sm mx-auto mt-1">
                    Dedicated slot for university leadership video briefing and live architecture walkthrough demonstration.
                  </p>
                </div>
              </div>

              <div className="mt-4 flex items-center justify-between text-xs text-[#6B7688]">
                <span className="flex items-center gap-1.5 font-mono">
                  <Terminal className="w-3.5 h-3.5 text-[#1F6FEB]" />
                  Active Phase: {PLACEHOLDERS.walkthroughSteps[activeWalkthroughStep].title}
                </span>
                <span className="text-[#1F6FEB] font-semibold cursor-pointer hover:underline" onClick={() => setIsDemoModalOpen(true)}>
                  Schedule Live Interactive Walkthrough →
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 10. CLOSING HIGH-ENERGY CTA BAND & FOOTER */}
      <section data-band="navy" className="relative py-24 bg-[#0B1F3A] text-white border-t border-[#081629] overflow-hidden">
        {/* Ambient energy orbs */}

        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <span className="text-xs font-bold uppercase tracking-widest text-white bg-white/10 border border-[#1F6FEB]/20 px-3.5 py-1 rounded-full">
            Open the college workspace
          </span>
          <h2 className="font-display font-semibold text-4xl sm:text-5xl lg:text-6xl text-white tracking-tight mt-6 mb-6 leading-tight">
            The public page shows the record. <br />
            <span className="gradient-text font-semibold">Sign-in is on the app.</span>
          </h2>
          <p className="text-lg sm:text-xl text-[#E3E8EF] max-w-2xl mx-auto mb-10 leading-relaxed">
            This site is the feature showcase. The college admin, faculty, and student workspaces open on the application.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <button
              onClick={() => setIsDemoModalOpen(true)}
              className="w-full sm:w-auto relative group px-8 py-4 rounded-[8px] text-base font-bold text-white overflow-hidden bg-[#1F6FEB]  transition-all duration-300 hover:-translate-y-1 active:translate-y-0 focus-ring"
            >
              <div className="absolute inset-0 bg-[#1F6FEB] transition-transform duration-300 group-hover:scale-105" />
              <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity" />
              <span className="relative flex items-center justify-center gap-2">
                Ask about the record
                <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1.5" />
              </span>
            </button>

            <Link
              href="/login"
              className="w-full sm:w-auto px-7 py-4 rounded-[8px] text-base font-semibold text-[#1B2433] bg-white hover:bg-[#F6F8FB] border border-[#E3E8EF] hover:border-[#1F6FEB] transition-colors text-center focus-ring"
            >
              Access Institutional Workspace
            </Link>
          </div>
        </div>
      </section>

      {/* 11. INSTITUTIONAL FOOTER */}
      <footer className="bg-[#081629] border-t border-[#081629] py-12 text-xs text-[#E3E8EF]">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-10">
            <div className="space-y-3">
              <Logo size="md" tone="light" />
              <p className="text-[#6B7688] text-xs leading-relaxed max-w-xs">
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
              <ul className="space-y-2 text-[#6B7688]">
                <li><a href="#foundation" className="hover:text-[#1F6FEB] transition-colors">Academic record</a></li>
                <li><a href="#foundation" className="hover:text-[#1F6FEB] transition-colors">Learning</a></li>
                <li><a href="#foundation" className="hover:text-[#1F6FEB] transition-colors">Examinations</a></li>
                <li><a href="#foundation" className="hover:text-[#1F6FEB] transition-colors">Fees, staff, campus</a></li>
              </ul>
            </div>

            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-3">
                Workspaces
              </h4>
              <ul className="space-y-2 text-[#6B7688]">
                <li><a href="#personas" className="hover:text-[#1F6FEB] transition-colors">Students</a></li>
                <li><a href="#personas" className="hover:text-[#1F6FEB] transition-colors">Faculty</a></li>
                <li><a href="#personas" className="hover:text-[#1F6FEB] transition-colors">College admin</a></li>
                <li><a href="#personas" className="hover:text-[#1F6FEB] transition-colors">Platform admin</a></li>
              </ul>
            </div>

            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-3">
                Governance & Security
              </h4>
              <ul className="space-y-2 text-[#6B7688]">
                <li><span>College admin creates people</span></li>
                <li><span>Faculty cannot enrol students</span></li>
                <li><span>No payment gateway</span></li>
                <li><span>No SGPA on the published result</span></li>
              </ul>
            </div>
          </div>

          <div className="pt-8 border-t border-[#E3E8EF] flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-[#6B7688]">
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
              className="relative w-full max-w-lg bg-white border border-[#E3E8EF] rounded-[12px] p-6 sm:p-8 shadow-2xl shadow-blue-950/80 z-10 text-[#0B1F3A]"
              role="dialog"
              aria-modal="true"
              aria-labelledby="demo-modal-title"
            >
              <button
                onClick={closeDemoModal}
                className="absolute top-5 right-5 p-2 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A] focus-ring"
                aria-label="Close dialog"
              >
                <X className="w-5 h-5" />
              </button>

              {demoSubmitted ? (
                <div className="text-center py-8">
                  <div className="w-16 h-16 rounded-[12px] bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center mx-auto mb-4">
                    <CheckCircle2 className="w-8 h-8" />
                  </div>
                  <h3 id="demo-modal-title" className="text-2xl font-bold font-display text-[#0B1F3A] mb-2">
                    Demonstration Request Confirmed
                  </h3>
                  <p className="text-sm text-[#3E4C63] leading-relaxed max-w-sm mx-auto mb-6">
                    Our academic architecture team will reach out with a sandbox tailored to{' '}
                    <span className="text-[#1F6FEB] font-semibold">{demoForm.institution || 'your institution'}</span>.
                  </p>
                  <button
                    onClick={closeDemoModal}
                    className="px-6 py-2.5 rounded-[8px] bg-[#0B1F3A] text-[#0B1F3A] text-sm font-semibold hover:bg-[#12315C] transition-colors focus-ring"
                  >
                    Close Window
                  </button>
                </div>
              ) : (
                <>
                  <div className="mb-6">
                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-cyan-500/10 border border-[#1F6FEB]/25 text-[#1F6FEB] text-xs font-semibold mb-2">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Institutional Briefing</span>
                    </div>
                    <h3 id="demo-modal-title" className="text-2xl font-bold font-display text-[#0B1F3A]">
                      Request CampXSync Architecture Briefing
                    </h3>
                    <p className="text-xs text-[#6B7688] mt-1">
                      Experience conflict-free scheduling and real-time governance on your curriculum data.
                    </p>
                  </div>

                  <form onSubmit={handleDemoSubmit} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-[#3E4C63] mb-1.5">
                        Your Full Name
                      </label>
                      <input
                        type="text"
                        required
                        value={demoForm.name}
                        onChange={(e) => setDemoForm({ ...demoForm, name: e.target.value })}
                        placeholder="Dr. Katherine Vance"
                        className="w-full px-4 py-2.5 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-[#1B2433] text-sm placeholder:text-[#6B7688] focus-ring"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-[#3E4C63] mb-1.5">
                        University or Institution Name
                      </label>
                      <input
                        type="text"
                        required
                        value={demoForm.institution}
                        onChange={(e) => setDemoForm({ ...demoForm, institution: e.target.value })}
                        placeholder="State University of Technology"
                        className="w-full px-4 py-2.5 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-[#1B2433] text-sm placeholder:text-[#6B7688] focus-ring"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-[#3E4C63] mb-1.5">
                        Institutional Role
                      </label>
                      <select
                        value={demoForm.role}
                        onChange={(e) => setDemoForm({ ...demoForm, role: e.target.value })}
                        className="w-full px-4 py-2.5 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-[#1B2433] text-sm focus-ring"
                      >
                        <option>College admin</option>
                        <option>Faculty</option>
                        <option>Platform admin</option>
                        <option>Student</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-[#3E4C63] mb-1.5">
                        Institutional Email Address
                      </label>
                      <input
                        type="email"
                        required
                        value={demoForm.email}
                        onChange={(e) => setDemoForm({ ...demoForm, email: e.target.value })}
                        placeholder="dean.office@university.edu"
                        className="w-full px-4 py-2.5 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-[#1B2433] text-sm placeholder:text-[#6B7688] focus-ring"
                      />
                    </div>

                    <button
                      type="submit"
                      className="w-full mt-2 py-3 rounded-[8px] bg-[#1F6FEB] text-white font-bold text-sm  hover:brightness-110 transition-all flex items-center justify-center gap-2 focus-ring"
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
