'use client';

import React from 'react';
import { cn } from '@/lib/utils';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = 'text', label, hint, error, id, ...props }, ref) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="w-full flex flex-col gap-1.5 text-left">
        {label && (
          <label htmlFor={inputId} className="text-xs font-semibold text-[#1B2433] tracking-wide">
            {label}
          </label>
        )}
        <input
          id={inputId}
          type={type}
          ref={ref}
          className={cn(
            'flex h-10 w-full rounded-[8px] border bg-white px-3 py-2 text-sm text-[#1B2433] placeholder:text-[#6B7688]',
            'transition-colors duration-150',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1',
            error
              ? 'border-[#D64545] focus-visible:ring-[#D64545]'
              : 'border-[#E3E8EF] focus-visible:border-[#1F6FEB] focus-visible:ring-[#1F6FEB]',
            'disabled:cursor-not-allowed disabled:opacity-50',
            className
          )}
          {...props}
        />
        {hint && !error && (
          <span className="text-xs text-[#6B7688]">{hint}</span>
        )}
        {error && (
          <span className="text-xs font-medium text-[#D64545] flex items-center gap-1">
            {error}
          </span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';
