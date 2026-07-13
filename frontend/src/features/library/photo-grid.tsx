import {
  ChevronLeft,
  ChevronRight,
  FileImage,
  Star,
  Image,
  Info,
  Minus,
  MoreHorizontal,
  Plus,
  X
} from 'lucide-react';
import {
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type CSSProperties,
  type DragEvent,
  type MouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode
} from 'react';
import {
  assetFileUrl,
  assetPreviewUrl,
  assetThumbnailUrl,
  type AssetBrowseResponse,
  type AssetDetail,
  type AssetSection,
  type AssetSummary
} from '@/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { BROWSE_LAYOUT_HEIGHT_CLASS } from '@/features/browse/browse-sidebar';
import { InlineEditableTitle } from '@/features/library/inline-editable-title';
import { cn } from '@/lib/utils';

export const ASSET_AVAILABILITY_MISSING = 'missing';
export const ASSET_DUPLICATE_BASE_COUNT = 1;
export const ASSET_IDENTITY_PENDING = 'pending';
const ASSET_METADATA_PENDING_LABEL = 'pending';
const ASSET_STATUS_ACTIVE = 'active';
const ASSET_FOCUS_CONTROLS_HIDE_DELAY_MS = 2500;
export const ASSET_FOCUS_MIN_ZOOM = 1;
export const ASSET_FOCUS_MAX_ZOOM = 5;
export const ASSET_FOCUS_ZOOM_STEP = 0.25;
const DETAIL_ROW_COLUMNS_CLASS = 'grid-cols-[6rem_minmax(0,1fr)]';

type PanOffset = { x: number; y: number };

function clampAssetFocusZoom(value: number) {
  return Math.min(ASSET_FOCUS_MAX_ZOOM, Math.max(ASSET_FOCUS_MIN_ZOOM, Math.round(value * 100) / 100));
}
const THUMBNAIL_SIZE_SLIDER_WIDTH_CLASS = 'w-28';
/** Matches Tailwind `gap-1`; used by column-count tile sizes below. */
export const ASSET_GRID_GAP = '0.25rem';
export const ASSET_TILE_SIZE_OPTIONS = [
  { key: 'tiny', minWidth: '5.5rem', imageSource: 'grid' },
  { key: 'compact', minWidth: '7rem', imageSource: 'grid' },
  { key: 'comfortable', minWidth: '11rem', imageSource: 'grid' },
  // Last three steps target ~3 / ~2 / 1 columns across the browse pane.
  {
    key: 'large',
    minWidth: `calc((100% - 2 * ${ASSET_GRID_GAP}) / 3)`,
    imageSource: 'preview'
  },
  {
    key: 'xlarge',
    minWidth: `calc((100% - ${ASSET_GRID_GAP}) / 2)`,
    imageSource: 'preview'
  },
  { key: 'huge', minWidth: '100%', imageSource: 'preview' }
] as const;
export const DEFAULT_ASSET_TILE_SIZE_INDEX = 2;
export const MAX_ASSET_TILE_SIZE_INDEX = ASSET_TILE_SIZE_OPTIONS.length - 1;
export const ASSET_TILE_SIZE_STORAGE_KEY = 'pixierge.assetTileSizeIndex';

export function readStoredAssetTileSizeIndex(): number {
  try {
    const raw = window.localStorage.getItem(ASSET_TILE_SIZE_STORAGE_KEY);
    if (raw == null) {
      return DEFAULT_ASSET_TILE_SIZE_INDEX;
    }
    const parsed = Number(raw);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > MAX_ASSET_TILE_SIZE_INDEX) {
      return DEFAULT_ASSET_TILE_SIZE_INDEX;
    }
    return parsed;
  } catch {
    return DEFAULT_ASSET_TILE_SIZE_INDEX;
  }
}

export function writeStoredAssetTileSizeIndex(index: number): void {
  if (!Number.isInteger(index) || index < 0 || index > MAX_ASSET_TILE_SIZE_INDEX) {
    return;
  }
  try {
    window.localStorage.setItem(ASSET_TILE_SIZE_STORAGE_KEY, String(index));
  } catch {
    // Ignore quota / private-mode failures; in-memory state still applies for the session.
  }
}
const ASSET_PLACEHOLDER_HUE_RANGE = 360;
const ASSET_PLACEHOLDER_SECONDARY_HUE_OFFSET = 32;
const ASSET_PLACEHOLDER_TERTIARY_HUE_OFFSET = 68;
const ASSET_PLACEHOLDER_SATURATION_PERCENT = 34;
const ASSET_PLACEHOLDER_LIGHTNESS_BASE_PERCENT = 28;
const ASSET_PLACEHOLDER_LIGHTNESS_RANGE_PERCENT = 18;

export type AssetTileSize = (typeof ASSET_TILE_SIZE_OPTIONS)[number];

export function AssetGrid({
  assetTileSize,
  onClearSelection,
  onContextMenu,
  onDragStart,
  onOpen,
  onRenameSection,
  onSelectClick,
  orderedIds,
  selectedIds,
  sections,
  showSectionHeaders = true
}: {
  assetTileSize: AssetTileSize;
  onClearSelection?: () => void;
  onContextMenu?: (asset: AssetSummary, event: MouseEvent) => void;
  onDragStart?: (asset: AssetSummary, event: DragEvent) => void;
  onOpen: (assetId: string) => void;
  onRenameSection?: (folderPath: string, name: string) => Promise<void> | void;
  onSelectClick?: (
    assetId: string,
    index: number,
    modifiers: Pick<MouseEvent, 'metaKey' | 'ctrlKey' | 'shiftKey'>,
    orderedIds: string[]
  ) => void;
  orderedIds?: string[];
  selectedIds?: Set<string>;
  sections: AssetBrowseResponse['sections'];
  showSectionHeaders?: boolean;
}) {
  const gridStyle = assetGridStyle(assetTileSize);

  function renderAssetTile(asset: AssetSummary) {
    return (
      <AssetTile
        asset={asset}
        imageSource={assetTileSize.imageSource}
        key={asset.id}
        onOpen={() => onOpen(asset.id)}
        onSelectClick={
          onSelectClick
            ? (event) => onSelectClick(asset.id, orderedIds?.indexOf(asset.id) ?? -1, event, orderedIds ?? [])
            : undefined
        }
        onContextMenu={onContextMenu ? (event) => onContextMenu(asset, event) : undefined}
        onDragStart={onDragStart ? (event) => onDragStart(asset, event) : undefined}
        selected={selectedIds?.has(asset.id) ?? false}
      />
    );
  }

  if (!showSectionHeaders) {
    return (
      <div
        aria-label="Asset grid"
        className="grid"
        onClick={(event) => {
          if (event.target === event.currentTarget) onClearSelection?.();
        }}
        style={gridStyle}
      >
        {sections.flatMap((section) => section.assets.map(renderAssetTile))}
      </div>
    );
  }

  return (
    <div className="grid gap-2">
      {sections.map((section) => (
        <section className="grid gap-2" key={section.folderPath}>
          <div className="bg-background py-1">
            <div className="flex min-w-0 flex-wrap items-baseline gap-2">
              {onRenameSection ? (
                <InlineEditableTitle
                  aria-label="Folder name"
                  onSave={(name) => onRenameSection(section.folderPath, name)}
                  size="md"
                  value={section.folderName}
                />
              ) : (
                <h3 className="text-lg font-semibold text-foreground">{section.folderName}</h3>
              )}
              <span className="shrink-0 text-sm text-muted-foreground">
                {formatItemCount(section.assets.length)}
              </span>
            </div>
          </div>
          <div
            aria-label="Asset grid"
            className="grid"
            onClick={(event) => {
              if (event.target === event.currentTarget) onClearSelection?.();
            }}
            style={gridStyle}
          >
            {section.assets.map(renderAssetTile)}
          </div>
        </section>
      ))}
    </div>
  );
}

export function ThumbnailSizeControls({
  onChange,
  value
}: {
  onChange: (value: number) => void;
  value: number;
}) {
  return (
    <div className="flex h-10 items-center gap-1.5" title="Thumbnail size">
      <Image aria-hidden className="size-3 shrink-0 text-muted-foreground" />
      <input
        aria-label="Thumbnail size"
        aria-valuemax={MAX_ASSET_TILE_SIZE_INDEX}
        aria-valuemin={0}
        aria-valuenow={value}
        aria-valuetext={ASSET_TILE_SIZE_OPTIONS[value]?.key}
        className={cn(
          'h-1.5 cursor-pointer appearance-none rounded-full bg-muted accent-foreground',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          THUMBNAIL_SIZE_SLIDER_WIDTH_CLASS
        )}
        max={MAX_ASSET_TILE_SIZE_INDEX}
        min={0}
        onChange={(event) => onChange(Number(event.target.value))}
        step={1}
        type="range"
        value={value}
      />
      <Image aria-hidden className="size-4 shrink-0 text-muted-foreground" />
    </div>
  );
}

function assetGridStyle(tileSize: AssetTileSize): CSSProperties {
  return {
    '--asset-grid-gap': ASSET_GRID_GAP,
    '--asset-grid-tile-size': tileSize.minWidth,
    gap: 'var(--asset-grid-gap)',
    // Cap tracks at the selected tile size so sparse sections (e.g. one photo) do not stretch.
    // min(100%, …) still lets tiles shrink on narrow panes.
    gridTemplateColumns:
      'repeat(auto-fill, minmax(min(100%, var(--asset-grid-tile-size)), var(--asset-grid-tile-size)))'
  } as CSSProperties;
}

export function AssetTile({
  asset,
  imageSource,
  onContextMenu,
  onDragStart,
  onOpen,
  onSelectClick,
  selected = false
}: {
  asset: AssetSummary;
  imageSource: AssetTileSize['imageSource'];
  onContextMenu?: (event: MouseEvent<HTMLButtonElement>) => void;
  onDragStart?: (event: DragEvent<HTMLButtonElement>) => void;
  onOpen: () => void;
  onSelectClick?: (event: MouseEvent<HTMLButtonElement>) => void;
  selected?: boolean;
}) {
  const [tinyThumbnailFailed, setTinyThumbnailFailed] = useState(false);
  const [thumbnailLoaded, setThumbnailLoaded] = useState(false);
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const sharpImageRef = useRef<HTMLImageElement>(null);
  const showThumbnail = asset.previewable && asset.identityStatus !== ASSET_IDENTITY_PENDING && !thumbnailFailed;
  const placeholderStyle = assetPlaceholderStyle(asset);
  const thumbnailCacheKey = asset.thumbnailCacheKey;
  const sharpSrc =
    imageSource === 'preview'
      ? assetPreviewUrl(asset.id, thumbnailCacheKey)
      : assetThumbnailUrl(asset.id, 'grid', thumbnailCacheKey);

  useEffect(() => {
    setThumbnailLoaded(false);
    setThumbnailFailed(false);
    setTinyThumbnailFailed(false);
    // Cached images can finish before onLoad is attached (e.g. after viewing the same
    // assets in Libraries, then opening Albums). Promote them immediately when complete.
    const sharpImage = sharpImageRef.current;
    if (sharpImage?.complete && sharpImage.naturalWidth > 0) {
      setThumbnailLoaded(true);
    }
  }, [sharpSrc]);

  return (
    <button
      aria-label={selected ? `Selected ${asset.fileName}` : `Select ${asset.fileName}`}
      aria-selected={selected}
      className="group relative aspect-[4/3] min-w-0 overflow-hidden bg-muted text-left"
      data-asset-id={asset.id}
      draggable={Boolean(onDragStart)}
      onClick={(event) => (onSelectClick ? onSelectClick(event) : onOpen())}
      onContextMenu={(event) => {
        event.preventDefault();
        onContextMenu?.(event);
      }}
      onDoubleClick={(event) => {
        event.preventDefault();
        onOpen();
      }}
      onDragStart={onDragStart}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          onOpen();
        }
      }}
      type="button"
    >
      <div
        aria-hidden
        className="absolute inset-0 scale-110 blur-xl"
        data-asset-placeholder={asset.id}
        style={placeholderStyle}
      />
      {showThumbnail ? (
        <>
          {!tinyThumbnailFailed && (
            <img
              alt=""
              className={cn(
                'absolute inset-0 h-full w-full scale-110 object-cover blur-md transition-opacity duration-300',
                thumbnailLoaded && 'opacity-0'
              )}
              decoding="async"
              loading="eager"
              onError={() => setTinyThumbnailFailed(true)}
              src={assetThumbnailUrl(asset.id, 'tiny', thumbnailCacheKey)}
            />
          )}
          <img
            alt=""
            className={cn(
              'absolute inset-0 h-full w-full object-cover transition-[opacity,transform] duration-300 group-hover:scale-[1.02]',
              thumbnailLoaded ? 'opacity-100' : 'opacity-0'
            )}
            decoding="async"
            loading="lazy"
            onError={() => setThumbnailFailed(true)}
            onLoad={() => setThumbnailLoaded(true)}
            ref={sharpImageRef}
            src={sharpSrc}
          />
        </>
      ) : (
        <div className="grid h-full place-items-center">
          <FileImage className="h-8 w-8 text-muted-foreground" aria-hidden />
        </div>
      )}
      {selected && (
        <div aria-hidden className="pointer-events-none absolute inset-0 border-2 border-muted-foreground" />
      )}
      {asset.starred && (
        <span
          aria-label="Starred"
          className="pointer-events-none absolute bottom-1.5 left-1.5 z-[1] grid h-6 w-6 place-items-center rounded-full bg-background/80 text-foreground shadow-sm"
        >
          <Star aria-hidden className="h-3.5 w-3.5 fill-current" />
        </span>
      )}
      <div className="absolute inset-x-0 bottom-0 z-[2] flex min-h-9 items-end justify-between gap-2 bg-gradient-to-t from-background/85 to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
        <span className="truncate text-xs font-medium text-foreground">{asset.fileName}</span>
        <span className="flex shrink-0 gap-1">
          {asset.identityStatus === ASSET_IDENTITY_PENDING && <Badge variant="secondary">Identity pending</Badge>}
          {asset.availability === ASSET_AVAILABILITY_MISSING && <Badge variant="warning">Missing</Badge>}
          {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && (
            <Badge variant="secondary">{asset.duplicateCount}</Badge>
          )}
        </span>
      </div>
    </button>
  );
}

function assetPlaceholderStyle(asset: AssetSummary): CSSProperties {
  if (asset.thumbnailPlaceholder) {
    return {
      background: asset.thumbnailPlaceholder
    };
  }

  const hash = hashString(`${asset.id}:${asset.displayPath}:${asset.fileName}`);
  const hue = hash % ASSET_PLACEHOLDER_HUE_RANGE;
  const secondaryHue = (hue + ASSET_PLACEHOLDER_SECONDARY_HUE_OFFSET) % ASSET_PLACEHOLDER_HUE_RANGE;
  const tertiaryHue = (hue + ASSET_PLACEHOLDER_TERTIARY_HUE_OFFSET) % ASSET_PLACEHOLDER_HUE_RANGE;
  const lightness = ASSET_PLACEHOLDER_LIGHTNESS_BASE_PERCENT + (hash % ASSET_PLACEHOLDER_LIGHTNESS_RANGE_PERCENT);

  return {
    background: `linear-gradient(135deg, hsl(${hue} ${ASSET_PLACEHOLDER_SATURATION_PERCENT}% ${
      lightness + 8
    }%), hsl(${secondaryHue} ${ASSET_PLACEHOLDER_SATURATION_PERCENT}% ${lightness}%) 52%, hsl(${tertiaryHue} ${ASSET_PLACEHOLDER_SATURATION_PERCENT}% ${
      lightness - 6
    }%))`
  };
}

function hashString(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index++) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

export function AssetFocus({
  asset,
  cacheKey,
  starred = false,
  hasNext,
  hasPrevious,
  loading,
  onClose,
  onContextMenu,
  onNext,
  onOpenActions,
  onPrevious
}: {
  asset: AssetDetail | null;
  cacheKey?: string | null;
  starred?: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  loading: boolean;
  onClose: () => void;
  onContextMenu?: (event: MouseEvent<HTMLDivElement>) => void;
  onNext: () => void;
  onOpenActions?: (anchor: { x: number; y: number }) => void;
  onPrevious: () => void;
}) {
  const activeFile = asset?.files?.find((file) => file.status === ASSET_STATUS_ACTIVE);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [showMetadata, setShowMetadata] = useState(false);
  const [controlsVisible, setControlsVisible] = useState(false);
  const [zoom, setZoom] = useState(ASSET_FOCUS_MIN_ZOOM);
  const [pan, setPan] = useState<PanOffset>({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const hideControlsTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const previewSurfaceRef = useRef<HTMLDivElement | null>(null);
  const zoomRef = useRef(zoom);
  const panSessionRef = useRef<{ pointerId: number; originX: number; originY: number; startPan: PanOffset } | null>(
    null
  );
  const previewSrc = asset ? (previewFailed ? assetFileUrl(asset.id) : assetPreviewUrl(asset.id, cacheKey)) : null;
  const canPan = zoom > ASSET_FOCUS_MIN_ZOOM;
  zoomRef.current = zoom;
  const overlayControlsClass = cn(
    'transition-opacity duration-300',
    controlsVisible ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none',
    'focus-visible:opacity-100 focus-visible:pointer-events-auto'
  );

  function revealControls() {
    setControlsVisible(true);
    if (hideControlsTimeoutRef.current) {
      clearTimeout(hideControlsTimeoutRef.current);
    }
    hideControlsTimeoutRef.current = setTimeout(() => {
      setControlsVisible(false);
    }, ASSET_FOCUS_CONTROLS_HIDE_DELAY_MS);
  }

  function hideControls() {
    if (hideControlsTimeoutRef.current) {
      clearTimeout(hideControlsTimeoutRef.current);
      hideControlsTimeoutRef.current = null;
    }
    setControlsVisible(false);
  }

  function resetViewport() {
    setZoom(ASSET_FOCUS_MIN_ZOOM);
    setPan({ x: 0, y: 0 });
    setIsPanning(false);
    panSessionRef.current = null;
  }

  function applyZoom(nextZoom: number) {
    const clamped = clampAssetFocusZoom(nextZoom);
    setZoom(clamped);
    if (clamped <= ASSET_FOCUS_MIN_ZOOM) {
      setPan({ x: 0, y: 0 });
    }
  }

  function zoomIn() {
    applyZoom(zoom + ASSET_FOCUS_ZOOM_STEP);
    revealControls();
  }

  function zoomOut() {
    applyZoom(zoom - ASSET_FOCUS_ZOOM_STEP);
    revealControls();
  }

  useEffect(() => {
    setPreviewFailed(false);
  }, [asset?.id]);

  useEffect(() => {
    setShowMetadata(false);
    resetViewport();
    revealControls();
    return () => {
      if (hideControlsTimeoutRef.current) {
        clearTimeout(hideControlsTimeoutRef.current);
      }
    };
  }, [asset?.id]);

  useEffect(() => {
    const surface = previewSurfaceRef.current;
    if (!surface) {
      return;
    }

    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      revealControls();
      const direction = event.deltaY < 0 ? 1 : -1;
      const nextZoom = clampAssetFocusZoom(zoomRef.current + direction * ASSET_FOCUS_ZOOM_STEP);
      setZoom(nextZoom);
      if (nextZoom <= ASSET_FOCUS_MIN_ZOOM) {
        setPan({ x: 0, y: 0 });
      }
    };

    surface.addEventListener('wheel', onWheel, { passive: false });
    return () => surface.removeEventListener('wheel', onWheel);
  }, [asset?.id]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      revealControls();

      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key === 'ArrowLeft' && hasPrevious) {
        event.preventDefault();
        onPrevious();
        return;
      }
      if (event.key === 'ArrowRight' && hasNext) {
        event.preventDefault();
        onNext();
        return;
      }
      if (event.key === '+' || event.key === '=') {
        event.preventDefault();
        const nextZoom = clampAssetFocusZoom(zoomRef.current + ASSET_FOCUS_ZOOM_STEP);
        setZoom(nextZoom);
        return;
      }
      if (event.key === '-' || event.key === '_') {
        event.preventDefault();
        const nextZoom = clampAssetFocusZoom(zoomRef.current - ASSET_FOCUS_ZOOM_STEP);
        setZoom(nextZoom);
        if (nextZoom <= ASSET_FOCUS_MIN_ZOOM) {
          setPan({ x: 0, y: 0 });
        }
        return;
      }
      if (event.key === '0') {
        event.preventDefault();
        resetViewport();
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [hasNext, hasPrevious, onClose, onNext, onPrevious]);

  function handlePointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    if (!canPan || event.button !== 0) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    panSessionRef.current = {
      pointerId: event.pointerId,
      originX: event.clientX,
      originY: event.clientY,
      startPan: pan
    };
    setIsPanning(true);
    revealControls();
  }

  function handlePointerMove(event: ReactPointerEvent<HTMLDivElement>) {
    const session = panSessionRef.current;
    if (!session || session.pointerId !== event.pointerId) {
      return;
    }
    setPan({
      x: session.startPan.x + (event.clientX - session.originX),
      y: session.startPan.y + (event.clientY - session.originY)
    });
  }

  function endPan(event: ReactPointerEvent<HTMLDivElement>) {
    const session = panSessionRef.current;
    if (!session || session.pointerId !== event.pointerId) {
      return;
    }
    panSessionRef.current = null;
    setIsPanning(false);
  }

  function handleDoubleClick(event: MouseEvent<HTMLDivElement>) {
    event.preventDefault();
    if (zoom > ASSET_FOCUS_MIN_ZOOM) {
      resetViewport();
    } else {
      applyZoom(2);
    }
    revealControls();
  }

  return (
    <div className={cn('grid min-h-0 grid-rows-[minmax(0,1fr)]', BROWSE_LAYOUT_HEIGHT_CLASS)}>
      {loading && <p className="text-sm text-muted-foreground">Loading asset...</p>}
      {asset && (
        <div
          className="relative min-h-0 overflow-hidden"
          onMouseLeave={hideControls}
          onMouseMove={revealControls}
        >
          {hasPrevious && (
            <button
              aria-label="Previous photo"
              className={cn(
                'absolute inset-y-0 left-0 z-10 flex w-1/4 max-w-xs items-center justify-start bg-gradient-to-r from-background/70 to-transparent pl-3',
                overlayControlsClass
              )}
              onClick={onPrevious}
              type="button"
            >
              <span className="grid h-10 w-10 place-items-center rounded-full border border-border bg-background/90 text-foreground shadow-sm">
                <ChevronLeft className="h-5 w-5" aria-hidden />
              </span>
            </button>
          )}
          {hasNext && (
            <button
              aria-label="Next photo"
              className={cn(
                'absolute inset-y-0 right-0 z-10 flex w-1/4 max-w-xs items-center justify-end bg-gradient-to-l from-background/70 to-transparent pr-3',
                overlayControlsClass
              )}
              onClick={onNext}
              type="button"
            >
              <span className="grid h-10 w-10 place-items-center rounded-full border border-border bg-background/90 text-foreground shadow-sm">
                <ChevronRight className="h-5 w-5" aria-hidden />
              </span>
            </button>
          )}
          <div className={cn('absolute left-3 top-3 z-20', overlayControlsClass)}>
            <Button aria-label="Close photo viewer" onClick={onClose} size="icon" type="button" variant="secondary">
              <X className="h-4 w-4" aria-hidden />
            </Button>
          </div>
          <div className={cn('absolute right-3 top-3 z-20 flex items-center gap-2', overlayControlsClass)}>
            {asset.availability === ASSET_AVAILABILITY_MISSING && <Badge variant="warning">Missing</Badge>}
            {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && (
              <Badge variant="secondary">{asset.duplicateCount} files</Badge>
            )}
            <Button
              aria-label="Zoom out"
              disabled={zoom <= ASSET_FOCUS_MIN_ZOOM}
              onClick={zoomOut}
              size="icon"
              type="button"
              variant="secondary"
            >
              <Minus className="h-4 w-4" aria-hidden />
            </Button>
            <Button
              aria-label="Zoom in"
              disabled={zoom >= ASSET_FOCUS_MAX_ZOOM}
              onClick={zoomIn}
              size="icon"
              type="button"
              variant="secondary"
            >
              <Plus className="h-4 w-4" aria-hidden />
            </Button>
            <Button
              aria-label={showMetadata ? 'Hide photo metadata' : 'Show photo metadata'}
              onClick={() => setShowMetadata((current) => !current)}
              size="icon"
              type="button"
              variant="secondary"
            >
              <Info className="h-4 w-4" aria-hidden />
            </Button>
            {onOpenActions && (
              <Button
                aria-label="Photo actions"
                onClick={(event) => {
                  const rect = event.currentTarget.getBoundingClientRect();
                  onOpenActions({ x: rect.left, y: rect.bottom + 4 });
                  revealControls();
                }}
                size="icon"
                type="button"
                variant="secondary"
              >
                <MoreHorizontal className="h-4 w-4" aria-hidden />
              </Button>
            )}
          </div>
          <div
            className={cn(
              'absolute inset-0 overflow-hidden bg-black',
              canPan ? (isPanning ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-default'
            )}
            onContextMenu={(event) => {
              if (!onContextMenu) {
                return;
              }
              event.preventDefault();
              onContextMenu(event);
              revealControls();
            }}
            onDoubleClick={handleDoubleClick}
            onPointerCancel={endPan}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={endPan}
            ref={previewSurfaceRef}
          >
            {activeFile ? (
              <img
                alt=""
                className="h-full w-full object-contain select-none"
                draggable={false}
                onError={() => setPreviewFailed(true)}
                src={previewSrc ?? undefined}
                style={{
                  transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
                  transformOrigin: 'center center',
                  transition: isPanning ? undefined : 'transform 120ms ease-out'
                }}
              />
            ) : (
              <div className="grid h-full place-items-center gap-3 text-muted-foreground">
                <FileImage className="h-10 w-10" aria-hidden />
                <p>No active file available</p>
              </div>
            )}
            {starred && (
              <span
                aria-label="Starred"
                className="pointer-events-none absolute bottom-3 left-3 z-10 grid h-8 w-8 place-items-center rounded-full bg-background/80 text-foreground shadow-sm"
              >
                <Star aria-hidden className="h-4 w-4 fill-current" />
              </span>
            )}
          </div>
          {showMetadata && (
            <>
              <button
                aria-label="Dismiss photo metadata"
                className="absolute inset-0 z-20 bg-background/40 backdrop-blur-[1px]"
                onClick={() => setShowMetadata(false)}
                type="button"
              />
              <aside className="absolute inset-y-0 right-0 z-30 w-full max-w-sm overflow-y-auto border-l border-border bg-background/95 p-4 backdrop-blur-sm">
                <div className="grid content-start gap-5">
                  <div className="flex items-start gap-2">
                    <div className="min-w-0 flex-1 grid gap-1">
                      <h1 className="break-words text-xl font-semibold text-foreground">{activeFile?.fileName ?? 'Asset'}</h1>
                      <p className="break-all text-sm text-muted-foreground">{activeFile?.path ?? asset.contentHash}</p>
                    </div>
                    <Button
                      aria-label="Close photo metadata"
                      className="-mr-2 -mt-1"
                      onClick={() => setShowMetadata(false)}
                      size="icon"
                      type="button"
                      variant="ghost"
                    >
                      <X className="h-4 w-4" aria-hidden />
                    </Button>
                  </div>
                  <dl className="grid gap-3 text-sm">
                    <DetailRow label="Type" value={asset.metadata.mimeType ?? asset.mediaType} />
                    <DetailRow
                      label="Size"
                      value={
                        asset.metadata.width && asset.metadata.height
                          ? `${asset.metadata.width} x ${asset.metadata.height}`
                          : 'Unknown'
                      }
                    />
                    <DetailRow label="Captured" value={formatDate(asset.metadata.capturedAt)} />
                    <DetailRow
                      label="Metadata"
                      value={asset.metadata.extractionStatus ?? ASSET_METADATA_PENDING_LABEL}
                    />
                  </dl>
                  {(asset.tags ?? []).length > 0 && (
                    <div className="grid gap-2">
                      <h2 className="text-sm font-semibold text-foreground">Tags</h2>
                      <div className="flex flex-wrap gap-1">
                        {(asset.tags ?? []).map((tag) => (
                          <Badge key={tag.id} variant="secondary">
                            {tag.name}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </aside>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className={cn('grid gap-3', DETAIL_ROW_COLUMNS_CLASS)}>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words text-foreground">{value}</dd>
    </div>
  );
}

export function EmptyPanel({
  action = null,
  description,
  icon: Icon,
  title
}: {
  action?: ReactNode;
  description: string;
  icon: ComponentType<{ className?: string }>;
  title: string;
}) {
  return (
    <section className="grid min-h-96 place-items-center">
      <div className="grid max-w-md justify-items-center gap-3 text-center">
        <Icon className="h-8 w-8 text-muted-foreground" aria-hidden />
        <h2 className="text-xl font-semibold text-foreground">{title}</h2>
        <p className="text-sm text-muted-foreground">{description}</p>
        {action}
      </div>
    </section>
  );
}

export function formatItemCount(count: number) {
  return `${count} ${count === 1 ? 'item' : 'items'}`;
}

export function flattenBrowseAssetIds(sections: AssetSection[]) {
  return sections.flatMap((section) => section.assets.map((asset) => asset.id));
}

export function mergeBrowseSections(current: AssetSection[], next: AssetSection[]) {
  const sectionsByFolder = new Map<string, AssetSection>();

  for (const section of current) {
    sectionsByFolder.set(section.folderPath, {
      ...section,
      assets: [...section.assets]
    });
  }

  for (const section of next) {
    const existing = sectionsByFolder.get(section.folderPath);
    if (existing) {
      const seenAssetIds = new Set(existing.assets.map((asset) => asset.id));
      for (const asset of section.assets) {
        if (!seenAssetIds.has(asset.id)) {
          existing.assets.push(asset);
          seenAssetIds.add(asset.id);
        }
      }
    } else {
      sectionsByFolder.set(section.folderPath, {
        ...section,
        assets: [...section.assets]
      });
    }
  }

  return [...sectionsByFolder.values()];
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Unknown';
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}
