import React from 'react';
import { LucideIcon, TrendingUp, TrendingDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface StatCardProps {
  title: string;
  value: string | number;
  delta?: {
    value: string | number;
    isPositive?: boolean;
    label?: string;
  };
  icon?: LucideIcon;
  description?: string;
  className?: string;
}

export function StatCard({
  title,
  value,
  delta,
  icon: Icon,
  description,
  className,
}: StatCardProps) {
  return (
    <div
      className={cn(
        'rounded-[12px] border border-[#E3E8EF] bg-white p-5 shadow-sm transition-all hover:border-[#B8D3FC]',
        className
      )}
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-[#475569]">
          {title}
        </span>
        {Icon && (
          <div className="flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#E8F0FE] text-[#1F6FEB]">
            <Icon className="h-5 w-5" />
          </div>
        )}
      </div>

      <div className="mt-3 flex items-baseline gap-2">
        <span className="text-2xl font-bold tracking-tight text-[#0B1F3A] tabular-nums">
          {value}
        </span>

        {delta && (
          <span
            className={cn(
              'inline-flex items-center text-xs font-semibold',
              delta.isPositive ? 'text-[#15803D]' : 'text-[#B91C1C]'
            )}
          >
            {delta.isPositive ? (
              <TrendingUp className="mr-0.5 h-3.5 w-3.5" />
            ) : (
              <TrendingDown className="mr-0.5 h-3.5 w-3.5" />
            )}
            {delta.value}
          </span>
        )}
      </div>

      {(description || delta?.label) && (
        <p className="mt-1 text-xs text-[#475569]">
          {delta?.label || description}
        </p>
      )}
    </div>
  );
}
