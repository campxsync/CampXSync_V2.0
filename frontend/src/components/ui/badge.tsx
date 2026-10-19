import React from 'react';
import { cn } from '@/lib/utils';

export type LifecycleState =
  | 'DRAFT'
  | 'OPEN'
  | 'SUBMITTED'
  | 'CLOSED'
  | 'CORRECTED'
  | 'LOCKED'
  | 'CANCELLED'
  | 'ONBOARDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'OFFBOARDED'
  | 'INACTIVE'
  | 'ARCHIVED'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  status?: LifecycleState | string;
  label?: string;
  variant?: 'default' | 'role' | 'outline';
  roleType?: 'platform' | 'college' | 'faculty' | 'student';
}

export function Badge({
  className,
  status,
  label,
  variant = 'default',
  roleType,
  children,
  ...props
}: BadgeProps) {
  const normStatus = (status || '').toUpperCase() as LifecycleState;

  // Lifecycle state colors matching Section 5 with WCAG AA compliant contrast (>= 4.5:1)
  const statusStyles: Record<string, string> = {
    DRAFT: 'bg-[#F0F2F5] text-[#374151] border-[#E3E8EF]',
    OPEN: 'bg-[#E8F0FE] text-[#1D4ED8] border-[#B8D3FC]',
    SUBMITTED: 'bg-[#EAF8F1] text-[#15803D] border-[#B2E6CE]',
    CLOSED: 'bg-[#EAF8F1] text-[#15803D] border-[#B2E6CE]',
    CORRECTED: 'bg-[#FEF7EA] text-[#B45309] border-[#FCE2B3]',
    LOCKED: 'bg-[#E7ECF3] text-[#0B1F3A] border-[#BDCDE0]',
    CANCELLED: 'bg-[#FDECEC] text-[#B91C1C] border-[#F8BDBD]',
    ONBOARDING: 'bg-[#FEF7EA] text-[#B45309] border-[#FCE2B3]',
    ACTIVE: 'bg-[#EAF8F1] text-[#15803D] border-[#B2E6CE]',
    SUSPENDED: 'bg-[#FDECEC] text-[#B91C1C] border-[#F8BDBD]',
    OFFBOARDED: 'bg-[#F0F2F5] text-[#374151] border-[#E3E8EF]',
    INACTIVE: 'bg-[#F0F2F5] text-[#374151] border-[#E3E8EF]',
    ARCHIVED: 'bg-[#F0F2F5] text-[#374151] border-[#E3E8EF]',
    PENDING: 'bg-[#FEF7EA] text-[#B45309] border-[#FCE2B3]',
    APPROVED: 'bg-[#EAF8F1] text-[#15803D] border-[#B2E6CE]',
    REJECTED: 'bg-[#FDECEC] text-[#B91C1C] border-[#F8BDBD]',
  };

  const roleStyles = {
    platform: 'bg-[#0B1F3A] text-white border-transparent',
    college: 'bg-[#1D4ED8] text-white border-transparent',
    faculty: 'bg-[#0F766E] text-white border-transparent',
    student: 'bg-[#475569] text-white border-transparent',
  };

  const computedStyle =
    variant === 'role' && roleType
      ? roleStyles[roleType]
      : normStatus && statusStyles[normStatus]
      ? statusStyles[normStatus]
      : 'bg-[#F6F8FB] text-[#1B2433] border-[#E3E8EF]';

  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded-[6px] text-xs font-semibold border tracking-wide uppercase',
        computedStyle,
        className
      )}
      {...props}
    >
      {children || label || status}
    </span>
  );
}
