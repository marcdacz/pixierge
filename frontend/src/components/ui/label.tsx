import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Label({ className, ...props }: ComponentProps<'label'>) {
  return (
    <label
      className={cn('grid gap-2 text-sm font-semibold leading-none text-foreground', className)}
      {...props}
    />
  );
}
