'use client';

import React from 'react';
import Link from 'next/link';

interface LogoProps {
  size?: 'sm' | 'md' | 'lg';
  showTile?: boolean;
  className?: string;
  href?: string;
  tone?: 'ink' | 'light';
}

export function Logo({
  size = 'md',
  showTile = true,
  className = '',
  href = '/',
  tone = 'ink',
}: LogoProps) {
  const sizeClasses = {
    sm: {
      tile: 'w-7 h-7 text-xs rounded-md',
      text: 'text-lg',
      sub: 'text-[9px]',
    },
    md: {
      tile: 'w-8 h-8 text-sm rounded-lg',
      text: 'text-xl',
      sub: 'text-[10px]',
    },
    lg: {
      tile: 'w-10 h-10 text-base rounded-xl',
      text: 'text-2xl',
      sub: 'text-xs',
    },
  }[size];

  const content = (
    <div className={`inline-flex items-center gap-2.5 font-display tracking-tight select-none group ${className}`}>
      {showTile && (
        <div
          className={`${sizeClasses.tile} font-bold font-sora flex items-center justify-center ${tone === 'light' ? 'bg-[#1F6FEB] text-white' : 'bg-[#0B1F3A] text-white'}`}
          aria-hidden="true"
        >
          CX
        </div>
      )}
      <div className="flex flex-col leading-none">
        <span className={`${sizeClasses.text} font-extrabold tracking-tight ${tone === 'light' ? 'text-white' : 'text-[#0B1F3A]'}`}>
          CampXSync
        </span>
        <span className={`${sizeClasses.sub} ${tone === 'light' ? 'text-[#E3E8EF]' : 'text-[#6B7688]'} font-sans uppercase tracking-widest font-semibold mt-0.5`}>
          Institutional OS
        </span>
      </div>
    </div>
  );

  if (href) {
    return (
      <Link href={href} className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 rounded-lg p-0.5 transition-opacity hover:opacity-95">
        {content}
      </Link>
    );
  }

  return content;
}
