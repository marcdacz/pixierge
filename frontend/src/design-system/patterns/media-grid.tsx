import { Check, ChevronDown, MoreHorizontal } from 'lucide-react';
import type { CSSProperties } from 'react';
import { createJustifiedRows } from '@/lib/justified-layout';
import { cn } from '@/lib/utils';
import { useMeasuredWidth } from '@/lib/use-measured-width';

export type MediaTileMode = 'normal' | 'wide' | 'selected';

export type MediaAsset = {
  label: string;
  background: string;
  aspectRatio?: number;
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

const MEDIA_GRID_GAP_PX = 4;
const MEDIA_GRID_TARGET_ROW_HEIGHT_PX = 144;

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
          <MediaGridGroup assets={group.assets} />
        </section>
      ))}
    </section>
  );
}

function MediaGridGroup({ assets }: { assets: MediaAsset[] }) {
  const [gridRef, containerWidth] = useMeasuredWidth<HTMLDivElement>();
  const rows =
    containerWidth > 0
      ? createJustifiedRows(
          assets.map((asset) => ({ aspectRatio: mediaAssetAspectRatio(asset), item: asset })),
          {
            containerWidth,
            gap: MEDIA_GRID_GAP_PX,
            targetRowHeight: MEDIA_GRID_TARGET_ROW_HEIGHT_PX
          }
        )
      : [];

  return (
    <div
      className={cn(rows.length > 0 ? 'grid gap-1' : 'flex flex-wrap content-start items-start gap-1')}
      ref={gridRef}
      style={{ '--media-row-height': '9rem' } as CSSProperties}
    >
      {rows.length > 0
        ? rows.map((row, rowIndex) => (
            <div
              className="flex gap-1"
              data-media-row-complete={row.complete}
              key={`${rowIndex}-${row.items[0]?.item.label ?? 'empty'}`}
              style={{ height: row.height }}
            >
              {row.items.map((entry) => (
                <MediaTile
                  asset={entry.item}
                  key={entry.item.label}
                  layoutStyle={{ height: `${entry.height}px`, width: `${entry.width}px` }}
                />
              ))}
            </div>
          ))
        : assets.map((asset) => <MediaTile asset={asset} key={asset.label} />)}
    </div>
  );
}

export function MediaTile({ asset, layoutStyle }: { asset: MediaAsset; layoutStyle?: CSSProperties }) {
  const mode = asset.mode ?? 'normal';
  const aspectRatio = mediaAssetAspectRatio(asset);

  return (
    <article
      aria-label={asset.label}
      className={cn('relative overflow-hidden bg-surface', mode === 'selected' && 'ring-2 ring-focus')}
      role="img"
      style={
        {
          '--media-tile-ratio': aspectRatio,
          backgroundImage: asset.background,
          height: 'var(--media-row-height)',
          width: 'min(100%, calc(var(--media-row-height) * var(--media-tile-ratio)))',
          ...layoutStyle
        } as CSSProperties
      }
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

function mediaAssetAspectRatio(asset: MediaAsset) {
  return asset.aspectRatio ?? (asset.mode === 'wide' ? 16 / 9 : 4 / 3);
}
