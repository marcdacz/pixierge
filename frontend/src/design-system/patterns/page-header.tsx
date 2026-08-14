import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export type PageHeaderProps = {
  title: ReactNode;
  meta?: ReactNode;
  eyebrow?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
};

export function PageHeader({ actions, className, description, eyebrow, meta, title }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-end justify-between gap-3', className)}>
      <div className="grid min-w-0 gap-1">
        {eyebrow}
        <div className="flex min-w-0 flex-wrap items-baseline gap-3">
          {typeof title === 'string' ? (
            <h1 className="truncate text-2xl font-semibold leading-tight tracking-display text-content">{title}</h1>
          ) : (
            <div className="min-w-0 text-content">{title}</div>
          )}
          {meta ? <span className="shrink-0 text-sm text-content-muted">{meta}</span> : null}
        </div>
        {description}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
}
