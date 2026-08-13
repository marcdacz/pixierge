import { cn } from '@/lib/utils';

export type TimelineRailItem =
  | { kind: 'year' | 'month' | 'active'; label: string; onSelect?: () => void }
  | { kind: 'active-line' | 'line' | 'dots'; label?: never; onSelect?: never };

export type TimelineRailProps = {
  items: TimelineRailItem[];
  className?: string;
};

export function TimelineRail({ className, items }: TimelineRailProps) {
  return (
    <aside
      className={cn('hidden border-l border-border bg-canvas px-2 py-6 text-xs text-content-muted md:grid', className)}
    >
      <div className="grid content-start justify-items-center">
        {items.map((item, index) => (
          <div className="grid min-h-6 justify-items-center" key={`${item.kind}-${item.label ?? index}`}>
            {item.kind === 'year' || item.kind === 'month' || item.kind === 'active' ? (
              <button
                aria-current={item.kind === 'active' ? 'date' : undefined}
                className={cn(
                  'rounded px-1 py-0.5 transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                  item.kind === 'active' && 'font-semibold text-info',
                  item.kind === 'year' && 'text-sm font-semibold text-content',
                  item.kind === 'month' && 'text-content-muted'
                )}
                onClick={item.onSelect}
                type="button"
              >
                {item.label}
              </button>
            ) : null}
            {item.kind === 'active-line' ? (
              <div className="relative my-1 h-14 w-3">
                <div className="mx-auto h-full w-0.5 rounded-full bg-info" />
                <span className="absolute top-1/2 left-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-content-subtle" />
              </div>
            ) : null}
            {item.kind === 'line' ? <div className="my-1 h-8 w-0.5 rounded-full bg-content-subtle/60" /> : null}
            {item.kind === 'dots' ? (
              <div className="my-2 grid gap-1">
                {Array.from({ length: 4 }, (_, dotIndex) => (
                  <span className="h-1 w-1 rounded-full bg-content-subtle" key={dotIndex} />
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </aside>
  );
}
