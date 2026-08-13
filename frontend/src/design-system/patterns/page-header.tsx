import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export type PageHeaderProps = {
  title: string;
  meta?: string;
  actions?: ReactNode;
  className?: string;
};

export function PageHeader({ actions, className, meta, title }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-center justify-between gap-3', className)}>
      <div className="flex min-w-0 items-baseline gap-3">
        <h1 className="truncate text-2xl font-semibold leading-tight tracking-display text-content">{title}</h1>
        {meta ? <span className="shrink-0 text-sm text-content-muted">{meta}</span> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
}
