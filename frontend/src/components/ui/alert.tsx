import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Alert({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn(
        'rounded-md border border-zinc-700 bg-zinc-900 px-4 py-3 text-sm font-medium text-zinc-200',
        className
      )}
      role="alert"
      {...props}
    />
  );
}
