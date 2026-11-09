'use client';

import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', isLoading = false, disabled, children, ...props }, ref) => {
    const baseStyles = 'inline-flex items-center justify-center font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none select-none';

    const variants = {
      primary: 'bg-[#1F6FEB] text-white hover:bg-[#12315C] focus-visible:ring-[#1F6FEB] shadow-sm',
      secondary: 'bg-[#E8F0FE] text-[#1F6FEB] hover:bg-[#D0E2FD] focus-visible:ring-[#1F6FEB]',
      outline: 'border border-[#E3E8EF] bg-white text-[#1B2433] hover:bg-[#F6F8FB] focus-visible:ring-[#1F6FEB]',
      ghost: 'text-[#6B7688] hover:text-[#1B2433] hover:bg-[#F6F8FB] focus-visible:ring-[#1F6FEB]',
      danger: 'bg-[#D64545] text-white hover:bg-[#B33535] focus-visible:ring-[#D64545] shadow-sm',
    };

    const sizes = {
      sm: 'h-8 px-3 text-xs rounded-[8px]',
      md: 'h-10 px-4 text-sm rounded-[8px]',
      lg: 'h-12 px-6 text-base rounded-[8px]',
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        {...props}
      >
        {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
        {children}
      </button>
    );
  }
);

Button.displayName = 'Button';
