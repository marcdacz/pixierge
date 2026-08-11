import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Input({ className, ref, ...props }: ComponentProps<'input'>) {
  return (
    <input
      ref={ref}
      className={cn(
        'h-10 w-full rounded-md border border-border-strong bg-surface-sunken px-3 text-sm text-content outline-none transition-colors placeholder:text-content-subtle focus-visible:border-focus focus-visible:ring-2 focus-visible:ring-focus-shadow disabled:cursor-not-allowed disabled:opacity-60',
        className
      )}
      {...props}
    />
  );
}
