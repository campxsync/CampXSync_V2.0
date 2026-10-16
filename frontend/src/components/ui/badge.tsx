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

  // Lifecycle state colors matching Section 5
  const statusStyles: Record<string, string> = {
    DRAFT: 'bg-[#F0F2F5] text-[#6B7688] border-[#E3E8EF]',
    OPEN: 'bg-[#E8F0FE] text-[#1F6FEB] border-[#B8D3FC]',
    SUBMITTED: 'bg-[#EAF8F1] text-[#1E9E6A] border-[#B2E6CE]',
    CLOSED: 'bg-[#EAF8F1] text-[#1E9E6A] border-[#B2E6CE]',
    CORRECTED: 'bg-[#FEF7EA] text-[#E39B16] border-[#FCE2B3]',
    LOCKED: 'bg-[#E7ECF3] text-[#0B1F3A] border-[#BDCDE0]',
    CANCELLED: 'bg-[#FDECEC] text-[#D64545] border-[#F8BDBD]',
    ONBOARDING: 'bg-[#FEF7EA] text-[#E39B16] border-[#FCE2B3]',
    ACTIVE: 'bg-[#EAF8F1] text-[#1E9E6A] border-[#B2E6CE]',
    SUSPENDED: 'bg-[#FDECEC] text-[#D64545] border-[#F8BDBD]',
    OFFBOARDED: 'bg-[#F0F2F5] text-[#6B7688] border-[#E3E8EF]',
    INACTIVE: 'bg-[#F0F2F5] text-[#6B7688] border-[#E3E8EF]',
    ARCHIVED: 'bg-[#F0F2F5] text-[#6B7688] border-[#E3E8EF]',
    PENDING: 'bg-[#FEF7EA] text-[#E39B16] border-[#FCE2B3]',
    APPROVED: 'bg-[#EAF8F1] text-[#1E9E6A] border-[#B2E6CE]',
    REJECTED: 'bg-[#FDECEC] text-[#D64545] border-[#F8BDBD]',
  };

  const roleStyles = {
    platform: 'bg-[#0B1F3A] text-white border-transparent',
    college: 'bg-[#1F6FEB] text-white border-transparent',
    faculty: 'bg-[#0D9488] text-white border-transparent',
    student: 'bg-[#64748B] text-white border-transparent',
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
