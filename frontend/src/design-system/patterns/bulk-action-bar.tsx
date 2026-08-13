import { MoreHorizontal, X, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export type BulkAction = {
  icon: LucideIcon;
  label: string;
  destructive?: boolean;
  onSelect?: () => void;
};

export type BulkActionBarProps = {
  selectedCount: number;
  summary: string;
  actions: BulkAction[];
  onMore?: () => void;
  onClear?: () => void;
  className?: string;
};

export function BulkActionBar({ actions, className, onClear, onMore, selectedCount, summary }: BulkActionBarProps) {
  return (
    <div
      className={cn(
        'pointer-events-none fixed right-2 bottom-4 left-[calc(var(--rail-width)+1rem)] z-10 flex justify-center md:right-8 lg:left-[calc(var(--rail-width)+var(--context-sidebar-width)+1.25rem)]',
        className
      )}
    >
      <div className="pointer-events-auto flex w-full min-w-0 items-center rounded-lg border border-border-strong bg-surface-raised/95 px-3 py-3 shadow-[var(--shadow-floating)] sm:px-4">
        <div className="flex min-w-0 flex-1 items-center gap-3 sm:w-64 sm:shrink-0 sm:flex-none">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-md border border-border bg-surface-sunken text-xl font-semibold text-content sm:h-12 sm:w-12">
            {selectedCount}
          </span>
          <div className="grid min-w-0 gap-0.5">
            <span className="truncate text-base font-semibold text-content">
              {selectedCount === 1 ? 'item selected' : 'items selected'}
            </span>
            <span className="truncate text-sm text-content-muted">{summary}</span>
          </div>
        </div>
        <div className="hidden items-center gap-10 text-sm text-content-muted sm:flex">
          {actions.map(({ destructive, icon: Icon, label, onSelect }) => (
            <button
              className={cn(
                'grid min-w-16 place-items-center gap-1 transition-colors hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                destructive && 'text-danger hover:text-danger-content'
              )}
              key={label}
              onClick={onSelect}
              type="button"
            >
              <Icon aria-hidden="true" className="h-6 w-6" />
              <span>{label}</span>
            </button>
          ))}
          <button
            aria-label="More actions"
            className="grid min-w-16 place-items-center gap-1 transition-colors hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            onClick={onMore}
            type="button"
          >
            <MoreHorizontal aria-hidden="true" className="h-6 w-6" />
            <span className="sr-only">More</span>
          </button>
        </div>
        <button
          aria-label="Close selection bar"
          className="ml-3 grid h-10 w-10 shrink-0 place-items-center rounded-md border border-border bg-surface-sunken text-content-muted transition-colors hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:ml-auto sm:h-12 sm:w-12"
          onClick={onClear}
          type="button"
        >
          <X aria-hidden="true" className="h-7 w-7" />
        </button>
      </div>
    </div>
  );
}
