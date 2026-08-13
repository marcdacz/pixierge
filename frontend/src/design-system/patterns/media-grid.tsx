import { Check, ChevronDown, MoreHorizontal } from 'lucide-react';
import { cn } from '@/lib/utils';

export type MediaTileMode = 'normal' | 'wide' | 'selected';

export type MediaAsset = {
  label: string;
  background: string;
  mode?: MediaTileMode;
  onActions?: () => void;
};

export type MediaGroup = {
  date: string;
  count: string;
  assets: MediaAsset[];
};

export type MediaGridProps = {
  groups: MediaGroup[];
  className?: string;
};

export function MediaGrid({ className, groups }: MediaGridProps) {
  return (
    <section className={cn('grid gap-6', className)}>
      {groups.map((group) => (
        <section className="grid gap-3" key={group.date}>
          <div className="flex items-center gap-2">
            <ChevronDown aria-hidden="true" className="h-4 w-4" />
            <h2 className="text-base font-semibold text-content">{group.date}</h2>
            <span className="text-sm text-content-muted">{group.count}</span>
          </div>
          <div className="grid auto-rows-[9rem] grid-cols-2 gap-1 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
            {group.assets.map((asset) => (
              <MediaTile asset={asset} key={asset.label} />
            ))}
          </div>
        </section>
      ))}
    </section>
  );
}

export function MediaTile({ asset }: { asset: MediaAsset }) {
  const mode = asset.mode ?? 'normal';

  return (
    <article
      aria-label={asset.label}
      className={cn(
        'relative overflow-hidden bg-surface',
        mode === 'wide' && 'col-span-2',
        mode === 'selected' && 'ring-2 ring-focus'
      )}
      role="img"
      style={{ backgroundImage: asset.background }}
    >
      <div className="absolute inset-0 bg-gradient-to-t from-black/72 via-black/8 to-transparent" />
      {mode === 'selected' ? (
        <>
          <span className="absolute top-2 left-2 grid h-6 w-6 place-items-center rounded-full bg-info text-xs font-bold text-content-inverse">
            <Check aria-hidden="true" className="h-4 w-4" strokeWidth={3} />
          </span>
          <button
            aria-label="Asset actions"
            className="absolute top-2 right-2 grid h-7 w-7 place-items-center rounded-md text-white/90 transition-colors hover:bg-black/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            onClick={asset.onActions}
            type="button"
          >
            <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
          </button>
        </>
      ) : null}
      <span className="absolute bottom-2 left-2 max-w-[calc(100%-1rem)] truncate text-xs font-semibold text-white/86">
        {asset.label}
      </span>
    </article>
  );
}
