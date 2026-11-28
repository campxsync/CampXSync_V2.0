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

const SIZE_STYLES = {
  sm: { height: 'h-8', width: 'w-[7.5rem]' },
  md: { height: 'h-10', width: 'w-[9.5rem]' },
  lg: { height: 'h-12', width: 'w-[11.5rem]' },
} as const;

export function Logo({
  size = 'md',
  showTile: _showTile = true,
  className = '',
  href = '/',
  tone = 'ink',
}: LogoProps) {
  const sizeClasses = SIZE_STYLES[size];

  // Official Orbit lockup image only — no other landing design changes.
  // White pad on light tone keeps the lockup readable on the dark footer.
  const content = (
    <span
      className={`inline-flex items-center select-none group ${tone === 'light' ? 'rounded-md bg-white px-2 py-1' : ''} ${className}`}
    >
      <img
        src="/brand/campxsync-logo.png"
        alt="CampXSync — Institutional OS"
        width={905}
        height={260}
        className={`${sizeClasses.height} ${sizeClasses.width} object-contain object-left`}
        decoding="async"
      />
    </span>
  );

  if (href) {
    return (
      <Link
        href={href}
        className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 rounded-lg p-0.5 transition-opacity hover:opacity-95"
      >
        {content}
      </Link>
    );
  }

  return content;
}
