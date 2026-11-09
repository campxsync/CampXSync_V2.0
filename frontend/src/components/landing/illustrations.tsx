'use client';

import React from 'react';
import { motion } from 'motion/react';
import { ShieldCheck, Calendar, Activity, CheckCircle2, Sparkles, Layers, Cpu } from 'lucide-react';

/**
 * Modern Editorial Illustrations with Light Anime Influence
 *
 * Strict specifications:
 * - Clean line work, flat colour with soft cel shading
 * - Natural proportions, gentle expressions and relaxed professional poses
 * - Electric Fluid palette (Cobalt, Vivid Violet, Luminous Cyan, Deep Indigo)
 * - Layered SVG architecture with floating interactive UI panels
 * - Responsive viewBox, optimized performance, accessibility attributes
 */

export function HeroIllustration({ className = '' }: { className?: string }) {
  return (
    <div className={`relative w-full aspect-[4/3] max-w-xl mx-auto flex items-center justify-center select-none ${className}`}>
      {/* Ambient background aura glow */}
      <div className="absolute inset-4 bg-gradient-to-tr from-blue-600/25 via-violet-600/30 to-cyan-500/25 rounded-3xl blur-3xl -z-10 animate-pulse-ambient" />

      {/* Main SVG Composition */}
      <svg
        viewBox="0 0 640 480"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="w-full h-full drop-shadow-2xl"
        role="img"
        aria-label="Illustration: University researcher collaborating on digital campus governance and timetable coordination"
      >
        <defs>
          <linearGradient id="heroGradientBase" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#1E293B" stopOpacity="0.8" />
            <stop offset="100%" stopColor="#0F172A" stopOpacity="0.95" />
          </linearGradient>
          <linearGradient id="glowCyan" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#06B6D4" />
            <stop offset="100%" stopColor="#3B82F6" />
          </linearGradient>
          <linearGradient id="glowViolet" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#8B5CF6" />
            <stop offset="100%" stopColor="#EC4899" />
          </linearGradient>
          <linearGradient id="clothingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#2563EB" />
            <stop offset="100%" stopColor="#1E1B4B" />
          </linearGradient>
          <linearGradient id="skinGrad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#FED7AA" />
            <stop offset="100%" stopColor="#FDBA74" />
          </linearGradient>
          <linearGradient id="hairGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#1E1B4B" />
            <stop offset="100%" stopColor="#0F172A" />
          </linearGradient>
          <filter id="softShadow" x="-10%" y="-10%" width="120%" height="120%">
            <feDropShadow dx="0" dy="8" stdDeviation="12" floodColor="#000000" floodOpacity="0.4" />
          </filter>
        </defs>

        {/* Geometric Architectural Backdrop Quadrangles */}
        <g opacity="0.4">
          <circle cx="320" cy="240" r="180" stroke="#334155" strokeWidth="1" strokeDasharray="4 6" />
          <circle cx="320" cy="240" r="220" stroke="#1E293B" strokeWidth="1" />
          <path d="M120 400 L520 400" stroke="#334155" strokeWidth="1" />
          <path d="M160 420 L480 420" stroke="#1E293B" strokeWidth="1" strokeDasharray="2 4" />

          {/* Constellation Nodes */}
          <circle cx="160" cy="180" r="4" fill="#06B6D4" />
          <line x1="160" y1="180" x2="220" y2="140" stroke="#06B6D4" strokeWidth="1" strokeDasharray="2 2" opacity="0.6" />
          <circle cx="220" cy="140" r="3" fill="#8B5CF6" />
          <circle cx="480" cy="190" r="4" fill="#3B82F6" />
          <line x1="480" y1="190" x2="430" y2="130" stroke="#3B82F6" strokeWidth="1" strokeDasharray="2 2" opacity="0.6" />
          <circle cx="430" cy="130" r="3" fill="#06B6D4" />
        </g>

        {/* Central Terminal / Desk Surface */}
        <path
          d="M140 380 C 220 375, 420 375, 500 380 L 460 430 L 180 430 Z"
          fill="url(#heroGradientBase)"
          stroke="#334155"
          strokeWidth="1.5"
          filter="url(#softShadow)"
        />
        <line x1="200" y1="382" x2="440" y2="382" stroke="#38BDF8" strokeWidth="1" opacity="0.7" />

        {/* Character Illustration (Academic / Scholar Architect) */}
        <g transform="translate(230, 160)">
          {/* Torso & Jacket - Relaxed Professional Attire */}
          <path
            d="M 50 170 C 40 130, 20 110, -10 105 L 10 210 L 170 210 L 190 105 C 160 110, 140 130, 130 170 Z"
            fill="url(#clothingGrad)"
            stroke="#1E3A8A"
            strokeWidth="1.5"
          />
          {/* Inner Shirt Collar */}
          <polygon points="65,110 90,150 115,110 100,105 80,105" fill="#F8FAFC" stroke="#CBD5E1" strokeWidth="1" />
          <line x1="90" y1="150" x2="90" y2="210" stroke="#CBD5E1" strokeWidth="1" />

          {/* Neck with soft cel shadow */}
          <rect x="76" y="80" width="28" height="32" rx="4" fill="url(#skinGrad)" />
          <path d="M 76 96 Q 90 106 104 96 L 104 110 L 76 110 Z" fill="#F97316" opacity="0.25" />

          {/* Head & Face - Calm, Intelligent, Refined Expression */}
          <path
            d="M 64 52 C 64 26, 116 26, 116 52 C 116 75, 102 92, 90 94 C 78 92, 64 75, 64 52 Z"
            fill="url(#skinGrad)"
            stroke="#EA580C"
            strokeWidth="0.8"
          />

          {/* Facial Features (Subtle Anime-inspired clean line art) */}
          {/* Modern Academic Glasses */}
          <rect x="71" y="48" width="16" height="11" rx="2.5" fill="#0284C7" fillOpacity="0.15" stroke="#0284C7" strokeWidth="1.2" />
          <rect x="93" y="48" width="16" height="11" rx="2.5" fill="#0284C7" fillOpacity="0.15" stroke="#0284C7" strokeWidth="1.2" />
          <line x1="87" y1="52" x2="93" y2="52" stroke="#0284C7" strokeWidth="1.2" />

          {/* Calm eyes behind glasses */}
          <path d="M 74 53 Q 79 51 84 53" stroke="#0F172A" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="79" cy="54" r="1.5" fill="#0F172A" />
          <path d="M 96 53 Q 101 51 106 53" stroke="#0F172A" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="101" cy="54" r="1.5" fill="#0F172A" />

          {/* Refined Eyebrows */}
          <path d="M 72 45 Q 79 43 85 45" stroke="#1E293B" strokeWidth="1.5" strokeLinecap="round" />
          <path d="M 95 45 Q 101 43 108 45" stroke="#1E293B" strokeWidth="1.5" strokeLinecap="round" />

          {/* Nose & Gentle Smile */}
          <path d="M 90 57 L 88 64 L 92 64" stroke="#D97706" strokeWidth="1" strokeLinecap="round" />
          <path d="M 85 73 Q 90 76 95 73" stroke="#9A3412" strokeWidth="1.2" strokeLinecap="round" />

          {/* Stylized Hair - Neat, Modern Cut */}
          <path
            d="M 62 48 C 58 32, 70 14, 90 12 C 112 14, 122 30, 118 48 C 114 40, 110 32, 98 28 C 86 24, 74 34, 62 48 Z"
            fill="url(#hairGrad)"
          />
          {/* Bangs / Strands with soft cel highlight */}
          <path d="M 68 28 Q 78 38 82 48 Q 80 34 88 28" fill="#312E81" />
          <path d="M 92 28 Q 98 38 106 46 Q 102 32 110 30" fill="#312E81" />
          <path d="M 80 18 Q 92 22 102 18" stroke="#818CF8" strokeWidth="1.2" strokeLinecap="round" opacity="0.6" />

          {/* Arms / Hand operating digital glass interface */}
          <path
            d="M 12 130 C 35 150, 75 180, 85 195 L 105 190 C 95 170, 55 140, 35 120 Z"
            fill="url(#clothingGrad)"
            stroke="#1E3A8A"
            strokeWidth="1"
          />
          <circle cx="95" cy="192" r="7" fill="url(#skinGrad)" />
          {/* Light projection ring on finger */}
          <circle cx="95" cy="192" r="10" stroke="#06B6D4" strokeWidth="1.2" strokeDasharray="2 3" opacity="0.8" />
        </g>

        {/* Central Holographic Orbiting Node */}
        <g transform="translate(320, 290)">
          <ellipse cx="0" cy="0" rx="90" ry="24" stroke="url(#glowCyan)" strokeWidth="1.5" strokeDasharray="6 4" opacity="0.8" />
          <ellipse cx="0" cy="0" rx="70" ry="18" stroke="url(#glowViolet)" strokeWidth="1" opacity="0.6" />
          <circle cx="0" cy="0" r="14" fill="#0F172A" stroke="#06B6D4" strokeWidth="2" />
          <circle cx="0" cy="0" r="6" fill="#38BDF8" className="animate-pulse" />
          {/* Vertical Sync Beam */}
          <line x1="0" y1="-80" x2="0" y2="0" stroke="url(#glowCyan)" strokeWidth="1.5" opacity="0.7" strokeDasharray="3 3" />
        </g>
      </svg>

      {/* Floating UI Telemetry Card 1 - Deterministic Clash Resolver */}
      <motion.div
        initial={{ y: 0 }}
        animate={{ y: [-4, 4, -4] }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute top-4 left-0 md:-left-6 bg-slate-900/80 backdrop-blur-xl border border-white/10 rounded-xl p-3 shadow-2xl shadow-blue-950/60 flex items-center gap-3 select-none"
      >
        <div className="w-8 h-8 rounded-lg bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
          <ShieldCheck className="w-4 h-4" />
        </div>
        <div>
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-bold text-white tracking-wide">Clash Resolution</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-medium border border-emerald-500/30">
              Active
            </span>
          </div>
          <p className="text-[11px] text-slate-400">0 Schedule Conflicts across rooms</p>
        </div>
      </motion.div>

      {/* Floating UI Telemetry Card 2 - Real-Time Attendance Relay */}
      <motion.div
        initial={{ y: 0 }}
        animate={{ y: [4, -4, 4] }}
        transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="absolute bottom-6 right-0 md:-right-6 bg-slate-900/85 backdrop-blur-xl border border-cyan-500/20 rounded-xl p-3 shadow-2xl shadow-cyan-950/50 flex items-center gap-3 select-none"
      >
        <div className="w-8 h-8 rounded-lg bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
          <Activity className="w-4 h-4" />
        </div>
        <div>
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-bold text-white tracking-wide">Telemetry Relay</span>
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
          </div>
          <p className="text-[11px] text-cyan-300/80 font-mono">Biometric Sync: 99.8%</p>
        </div>
      </motion.div>

      {/* Floating Mini Badge - Accreditation Ledger */}
      <motion.div
        initial={{ scale: 1 }}
        animate={{ scale: [1, 1.04, 1] }}
        transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut', delay: 2 }}
        className="absolute -top-3 right-6 bg-gradient-to-r from-violet-600/90 to-blue-600/90 backdrop-blur-md px-3 py-1 rounded-full border border-violet-400/30 text-white text-[11px] font-semibold shadow-lg flex items-center gap-1.5"
      >
        <Sparkles className="w-3 h-3 text-amber-300" />
        <span>Unified Master Schema</span>
      </motion.div>
    </div>
  );
}

/**
 * Persona Role Illustrations
 * Styled individually for each of the 4 key roles:
 * - Scholars: student with tablet studying pathway
 * - Faculty: professor leading lecture / grading rubric
 * - Deans: chair approving Board of Studies curriculum
 * - Core: chancellor observing multi-campus governance node
 */
export function RoleIllustration({
  role,
  className = '',
}: {
  role: 'scholars' | 'faculty' | 'deans' | 'core';
  className?: string;
}) {
  const configs = {
    scholars: {
      title: 'Scholar Academic Navigator',
      accent: '#38BDF8',
      bgGrad: 'from-blue-600/20 via-cyan-500/15 to-transparent',
      badge: 'Degree Pacing',
      badgeVal: 'On Track',
      renderChar: () => (
        <g transform="translate(60, 40)">
          {/* Seated student with digital tablet */}
          <ellipse cx="140" cy="230" rx="90" ry="12" fill="#1E293B" opacity="0.6" />
          {/* Body */}
          <path d="M 110 130 Q 140 120 170 130 L 180 220 L 100 220 Z" fill="#2563EB" />
          {/* Head & Expression */}
          <circle cx="140" cy="85" r="28" fill="#FED7AA" stroke="#EA580C" strokeWidth="0.8" />
          {/* Modern hair */}
          <path d="M 116 75 C 114 55, 140 50, 164 62 C 160 52, 145 48, 130 50 C 118 52, 114 65, 116 75 Z" fill="#0F172A" />
          <path d="M 124 70 Q 134 80 140 90 Q 142 75 152 70" fill="#1E1B4B" />
          {/* Calm eyes & smile */}
          <circle cx="132" cy="84" r="2" fill="#0F172A" />
          <circle cx="148" cy="84" r="2" fill="#0F172A" />
          <path d="M 136 96 Q 140 100 144 96" stroke="#9A3412" strokeWidth="1.2" strokeLinecap="round" />
          {/* Glowing Tablet */}
          <rect x="110" y="150" width="60" height="40" rx="4" fill="#0F172A" stroke="#38BDF8" strokeWidth="1.5" />
          <line x1="118" y1="162" x2="152" y2="162" stroke="#38BDF8" strokeWidth="2" strokeLinecap="round" />
          <line x1="118" y1="172" x2="142" y2="172" stroke="#818CF8" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="158" cy="172" r="3" fill="#34D399" />
        </g>
      ),
    },
    faculty: {
      title: 'Faculty Course Director',
      accent: '#818CF8',
      bgGrad: 'from-violet-600/20 via-purple-500/15 to-transparent',
      badge: 'Evaluation Rubric',
      badgeVal: 'Verified',
      renderChar: () => (
        <g transform="translate(60, 40)">
          <ellipse cx="140" cy="230" rx="90" ry="12" fill="#1E293B" opacity="0.6" />
          {/* Smart academic blazer */}
          <path d="M 105 120 Q 140 115 175 120 L 190 220 L 90 220 Z" fill="#4338CA" />
          <polygon points="125,120 140,160 155,120" fill="#F8FAFC" />
          <polygon points="135,120 140,165 145,120" fill="#06B6D4" />
          {/* Head & Glasses */}
          <circle cx="140" cy="80" r="28" fill="#FDBA74" stroke="#EA580C" strokeWidth="0.8" />
          {/* Neatly styled hair */}
          <path d="M 116 75 C 114 50, 150 48, 164 68 C 158 55, 142 50, 126 55 Z" fill="#1E1B4B" />
          {/* Glasses */}
          <rect x="124" y="74" width="13" height="9" rx="2" fill="#818CF8" fillOpacity="0.2" stroke="#818CF8" strokeWidth="1" />
          <rect x="143" y="74" width="13" height="9" rx="2" fill="#818CF8" fillOpacity="0.2" stroke="#818CF8" strokeWidth="1" />
          <line x1="137" y1="78" x2="143" y2="78" stroke="#818CF8" strokeWidth="1" />
          <path d="M 136 94 Q 140 97 144 94" stroke="#9A3412" strokeWidth="1.2" strokeLinecap="round" />
          {/* Floating Course Syllabus Holo-Chart */}
          <rect x="180" y="100" width="70" height="50" rx="6" fill="#0F172A" stroke="#818CF8" strokeWidth="1.5" />
          <path d="M 190 135 L 205 120 L 220 130 L 240 110" stroke="#06B6D4" strokeWidth="2" fill="none" />
          <circle cx="240" cy="110" r="3" fill="#06B6D4" />
        </g>
      ),
    },
    deans: {
      title: 'Dean & Academic Chair',
      accent: '#F59E0B',
      bgGrad: 'from-amber-500/20 via-orange-500/15 to-transparent',
      badge: 'Board of Studies',
      badgeVal: 'Approved',
      renderChar: () => (
        <g transform="translate(60, 40)">
          <ellipse cx="140" cy="230" rx="90" ry="12" fill="#1E293B" opacity="0.6" />
          {/* Executive blazer */}
          <path d="M 100 120 Q 140 110 180 120 L 195 220 L 85 220 Z" fill="#0F172A" stroke="#334155" strokeWidth="1" />
          <polygon points="120,120 140,170 160,120" fill="#F8FAFC" />
          {/* Head */}
          <circle cx="140" cy="78" r="28" fill="#FED7AA" stroke="#EA580C" strokeWidth="0.8" />
          {/* Distinguished modern haircut */}
          <path d="M 114 70 C 114 45, 160 45, 166 70 C 158 52, 138 48, 122 56 Z" fill="#334155" />
          <circle cx="132" cy="78" r="2" fill="#0F172A" />
          <circle cx="148" cy="78" r="2" fill="#0F172A" />
          <path d="M 135 94 Q 140 96 145 94" stroke="#9A3412" strokeWidth="1.2" strokeLinecap="round" />
          {/* Floating Accreditation Seal & Ledger */}
          <g transform="translate(180, 110)">
            <circle cx="30" cy="30" r="24" fill="#0F172A" stroke="#F59E0B" strokeWidth="1.5" />
            <path d="M 22 30 L 28 36 L 40 24" stroke="#F59E0B" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
          </g>
        </g>
      ),
    },
    core: {
      title: 'University Core Chancellor',
      accent: '#34D399',
      bgGrad: 'from-emerald-500/20 via-teal-500/15 to-transparent',
      badge: 'Multi-Campus Sync',
      badgeVal: 'Synchronized',
      renderChar: () => (
        <g transform="translate(60, 40)">
          <ellipse cx="140" cy="230" rx="90" ry="12" fill="#1E293B" opacity="0.6" />
          {/* Grounded executive robe/suit */}
          <path d="M 95 115 Q 140 108 185 115 L 200 220 L 80 220 Z" fill="#1E293B" stroke="#047857" strokeWidth="1" />
          <polygon points="120,115 140,160 160,115" fill="#10B981" fillOpacity="0.2" stroke="#10B981" strokeWidth="1" />
          {/* Head */}
          <circle cx="140" cy="75" r="28" fill="#FDBA74" stroke="#EA580C" strokeWidth="0.8" />
          <path d="M 114 68 C 114 42, 160 42, 166 68 C 158 50, 138 46, 122 52 Z" fill="#0F172A" />
          <circle cx="132" cy="76" r="2" fill="#0F172A" />
          <circle cx="148" cy="76" r="2" fill="#0F172A" />
          <path d="M 135 92 Q 140 94 145 92" stroke="#9A3412" strokeWidth="1.2" strokeLinecap="round" />
          {/* Multi-campus constellation network */}
          <g transform="translate(30, 90)">
            <circle cx="20" cy="20" r="5" fill="#34D399" />
            <circle cx="70" cy="10" r="4" fill="#38BDF8" />
            <circle cx="50" cy="50" r="4" fill="#818CF8" />
            <line x1="20" y1="20" x2="70" y2="10" stroke="#34D399" strokeWidth="1.5" strokeDasharray="2 3" />
            <line x1="20" y1="20" x2="50" y2="50" stroke="#34D399" strokeWidth="1.5" strokeDasharray="2 3" />
            <line x1="70" y1="10" x2="50" y2="50" stroke="#818CF8" strokeWidth="1.5" strokeDasharray="2 3" />
          </g>
        </g>
      ),
    },
  }[role];

  return (
    <div className={`relative w-full aspect-square max-w-sm mx-auto flex items-center justify-center select-none ${className}`}>
      {/* Dynamic ambient backdrop */}
      <div className={`absolute inset-4 bg-gradient-to-tr ${configs.bgGrad} rounded-3xl blur-2xl -z-10`} />

      <svg
        viewBox="0 0 360 320"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="w-full h-full drop-shadow-xl"
        role="img"
        aria-label={`Illustration: ${configs.title}`}
      >
        <defs>
          <filter id={`roleShadow-${role}`} x="-10%" y="-10%" width="120%" height="120%">
            <feDropShadow dx="0" dy="6" stdDeviation="10" floodColor="#000000" floodOpacity="0.5" />
          </filter>
        </defs>

        {/* Outer orbital rings */}
        <ellipse cx="180" cy="160" rx="130" ry="130" stroke="#1E293B" strokeWidth="1" strokeDasharray="4 6" opacity="0.6" />
        <ellipse cx="180" cy="160" rx="100" ry="100" stroke="#334155" strokeWidth="0.8" opacity="0.4" />

        {/* Character element */}
        {configs.renderChar()}
      </svg>

      {/* Floating Status Pill */}
      <div className="absolute top-2 right-2 bg-slate-900/90 backdrop-blur-md px-3 py-1 rounded-full border border-white/10 shadow-lg flex items-center gap-2">
        <span className="w-2 h-2 rounded-full" style={{ backgroundColor: configs.accent }} />
        <span className="text-[11px] font-medium text-slate-300">{configs.badge}:</span>
        <span className="text-[11px] font-bold text-white">{configs.badgeVal}</span>
      </div>
    </div>
  );
}
