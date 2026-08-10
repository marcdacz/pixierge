import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

const badgeVariants = cva('inline-flex min-h-6 items-center rounded-md border px-2 py-0.5 text-xs font-semibold', {
  variants: {
    variant: {
      default: 'border-transparent bg-primary text-primary-foreground',
      secondary: 'border-border bg-muted text-foreground',
      success: 'border-success bg-success-surface text-success-content',
      warning: 'border-warning bg-warning-surface text-warning-content'
    }
  },
  defaultVariants: {
    variant: 'default'
  }
});

export type BadgeProps = ComponentProps<'span'> & VariantProps<typeof badgeVariants>;

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
