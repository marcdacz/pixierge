import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Alert({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn(
        'rounded-md border border-warning bg-warning-surface px-4 py-3 text-sm font-medium text-warning-content',
        className
      )}
      role="alert"
      {...props}
    />
  );
}
