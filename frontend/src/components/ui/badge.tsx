import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

const badgeVariants = cva('inline-flex min-h-6 items-center rounded-chip border px-2 py-0.5 text-xs font-semibold', {
  variants: {
    variant: {
      default: 'border-info bg-info-surface text-info-content',
      secondary: 'border-border bg-surface-hover text-content',
      success: 'border-success bg-success-surface text-success-content',
      warning: 'border-warning bg-warning-surface text-warning-content',
      danger: 'border-danger bg-danger-surface text-danger-content',
      processing: 'border-processing bg-processing-surface text-processing-content',
      unavailable: 'border-unavailable bg-unavailable-surface text-unavailable-content'
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
