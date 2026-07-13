import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Input({ className, ref, ...props }: ComponentProps<'input'>) {
  return (
    <input
      ref={ref}
      className={cn(
        'h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground shadow-sm outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-60',
        className
      )}
      {...props}
    />
  );
}
