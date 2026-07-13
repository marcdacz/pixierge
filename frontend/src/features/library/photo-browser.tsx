import { FileImage, Hash, Images, Star, StarOff } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type DragEvent, type MouseEvent, type ReactNode } from 'react';
import {
  addAlbumItems,
  addAssetTags,
  createAlbum,
  createTag,
  fetchAlbums,
  fetchAsset,
  fetchStarred,
  fetchTags,
  removeAlbumItems,
  type AssetBrowseResponse,
  type AssetDetail,
  type AssetSummary,
  type AuthResponse
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { AssignmentPicker, type AssignmentDestination } from '@/features/organizer/assignment-picker';
import {
  AssetContextMenu,
  type AssetContextMenuAction
} from '@/features/organizer/asset-context-menu';
import { writeAssetDragData } from '@/features/organizer/drag-types';
import { useAssetSelection } from '@/features/organizer/use-asset-selection';
import {
  AssetFocus,
  AssetGrid,
  ASSET_TILE_SIZE_OPTIONS,
  EmptyPanel,
  flattenBrowseAssetIds,
  formatItemCount,
  readStoredAssetTileSizeIndex,
  ThumbnailSizeControls,
  writeStoredAssetTileSizeIndex
} from '@/features/library/photo-grid';

const LOAD_MORE_ROOT_MARGIN = '240px';

export type PhotoBrowserSelectionContext = {
  selectedIds: Set<string>;
  selectedAssets: AssetSummary[];
  contextAsset: AssetSummary | null;
  clearSelection: () => void;
};

export type PhotoBrowserProps = {
  auth?: AuthResponse;
  browseContextKey: string;
  title: ReactNode;
  subtitle?: ReactNode;
  description?: ReactNode;
  leadingControls?: ReactNode;
  extraContextActions?: AssetContextMenuAction[] | ((ctx: PhotoBrowserSelectionContext) => AssetContextMenuAction[]);
  assets: AssetBrowseResponse | null;
  loadingAssets: boolean;
  loadingMore?: boolean;
  error?: string | null;
  onLoadMore?: () => void;
  onAssignmentsChanged?: () => void;
  onRenameSection?: (folderPath: string, name: string) => Promise<void> | void;
  showSectionHeaders?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  className?: string;
  onFocusChange?: (focused: boolean) => void;
};

export function PhotoBrowser({
  auth,
  browseContextKey,
  title,
  subtitle,
  description,
  leadingControls,
  extraContextActions,
  assets,
  loadingAssets,
  loadingMore = false,
  error = null,
  onLoadMore,
  onAssignmentsChanged,
  onRenameSection,
  showSectionHeaders = true,
  emptyTitle = 'No assets found',
  emptyDescription = 'Nothing to show here yet.',
  className,
  onFocusChange
}: PhotoBrowserProps) {
  const [assetTileSizeIndex, setAssetTileSizeIndex] = useState(readStoredAssetTileSizeIndex);
  const [assetDetail, setAssetDetail] = useState<AssetDetail | null>(null);
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [browseError, setBrowseError] = useState<string | null>(null);
  const [pickerKind, setPickerKind] = useState<'albums' | 'tags' | null>(null);
  const [pickerDestinations, setPickerDestinations] = useState<AssignmentDestination[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; assetId: string } | null>(null);
  const [starredOverrides, setStarredOverrides] = useState<Map<string, boolean>>(() => new Map());
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);
  const loadMoreRequestedRef = useRef(false);
  const hasNextRef = useRef(false);
  const lastBrowseScrollTopRef = useRef(0);
  const pendingRestoreBrowseScrollRef = useRef(false);
  const focusReturnAssetIdRef = useRef<string | null>(null);
  const pendingNextNavigationRef = useRef(false);

  const assetTileSize = ASSET_TILE_SIZE_OPTIONS[assetTileSizeIndex];
  const displayAssets = useMemo(() => {
    if (!assets) {
      return null;
    }
    if (starredOverrides.size === 0) {
      return assets;
    }
    return {
      ...assets,
      sections: assets.sections.map((section) => ({
        ...section,
        assets: section.assets.map((asset) => {
          const override = starredOverrides.get(asset.id);
          return override === undefined || override === asset.starred
            ? asset
            : { ...asset, starred: override };
        })
      }))
    };
  }, [assets, starredOverrides]);
  const browseAssetIds = useMemo(() => flattenBrowseAssetIds(displayAssets?.sections ?? []), [displayAssets?.sections]);
  const selection = useAssetSelection(browseContextKey);
  const selectedAssets = useMemo(
    () =>
      displayAssets?.sections.flatMap((section) => section.assets).filter((asset) => selection.selectedIds.has(asset.id)) ??
      [],
    [displayAssets?.sections, selection.selectedIds]
  );
  const selectedAssetSummary = useMemo(
    () => displayAssets?.sections.flatMap((section) => section.assets).find((asset) => asset.id === selectedAssetId),
    [displayAssets?.sections, selectedAssetId]
  );
  const contextAsset = useMemo(
    () =>
      displayAssets?.sections.flatMap((section) => section.assets).find((asset) => asset.id === contextMenu?.assetId) ??
      null,
    [displayAssets?.sections, contextMenu?.assetId]
  );
  const focusedAssetIndex = selectedAssetId ? browseAssetIds.indexOf(selectedAssetId) : -1;
  const canGoToPreviousAsset = focusedAssetIndex > 0;
  const canGoToNextAsset =
    (focusedAssetIndex >= 0 && focusedAssetIndex < browseAssetIds.length - 1) ||
    (focusedAssetIndex === browseAssetIds.length - 1 && (displayAssets?.hasNext ?? false));

  useEffect(() => {
    setStarredOverrides(new Map());
  }, [browseContextKey]);

  useEffect(() => {
    hasNextRef.current = displayAssets?.hasNext ?? false;
  }, [displayAssets?.hasNext]);

  useEffect(() => {
    if (!loadingMore) {
      loadMoreRequestedRef.current = false;
    }
  }, [loadingMore]);

  useEffect(() => {
    const sentinel = loadMoreRef.current;
    const scrollRoot = scrollContainerRef.current;
    if (!sentinel || !scrollRoot || !assets?.hasNext || loadingAssets || loadingMore || !onLoadMore) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((entry) => entry.isIntersecting) || loadMoreRequestedRef.current || !hasNextRef.current) {
          return;
        }
        loadMoreRequestedRef.current = true;
        onLoadMore();
      },
      { root: scrollRoot, rootMargin: LOAD_MORE_ROOT_MARGIN }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [assets?.hasNext, browseContextKey, loadingAssets, loadingMore, onLoadMore]);

  useEffect(() => {
    // Keep parent layout in sync for non-interactive clears (e.g. detail fetch failure).
    onFocusChange?.(selectedAssetId !== null);
  }, [onFocusChange, selectedAssetId]);

  useEffect(() => {
    if (!selectedAssetId) {
      setAssetDetail(null);
      return;
    }

    let ignore = false;
    setAssetDetail(null);
    fetchAsset(selectedAssetId)
      .then((response) => {
        if (!ignore) {
          setAssetDetail(response);
        }
      })
      .catch(() => {
        if (!ignore) {
          setBrowseError('Asset detail could not be loaded.');
          setSelectedAssetId(null);
        }
      });

    return () => {
      ignore = true;
    };
  }, [selectedAssetId]);

  useEffect(() => {
    if (!pendingNextNavigationRef.current || !selectedAssetId || loadingMore) {
      return;
    }

    const index = browseAssetIds.indexOf(selectedAssetId);
    if (index >= 0 && index < browseAssetIds.length - 1) {
      pendingNextNavigationRef.current = false;
      setSelectedAssetId(browseAssetIds[index + 1]);
    }
  }, [browseAssetIds, loadingMore, selectedAssetId]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !selectedAssetId) selection.clear();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedAssetId, selection.clear]);

  useEffect(() => {
    if (selectedAssetId || !pendingRestoreBrowseScrollRef.current) {
      return;
    }

    const frame = requestAnimationFrame(() => {
      const returnAssetId = focusReturnAssetIdRef.current;
      if (returnAssetId) {
        const tile = scrollContainerRef.current?.querySelector(`[data-asset-id="${returnAssetId}"]`);
        tile?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
        focusReturnAssetIdRef.current = null;
      } else if (scrollContainerRef.current) {
        scrollContainerRef.current.scrollTop = lastBrowseScrollTopRef.current;
      }
      pendingRestoreBrowseScrollRef.current = false;
    });

    return () => cancelAnimationFrame(frame);
  }, [selectedAssetId]);

  function openAsset(assetId: string) {
    lastBrowseScrollTopRef.current = scrollContainerRef.current?.scrollTop ?? 0;
    focusReturnAssetIdRef.current = null;
    pendingNextNavigationRef.current = false;
    setSelectedAssetId(assetId);
    // Notify synchronously so parent layout (sidebar hide) batches with this render.
    onFocusChange?.(true);
  }

  function closeAssetFocus() {
    focusReturnAssetIdRef.current = selectedAssetId;
    pendingRestoreBrowseScrollRef.current = true;
    setSelectedAssetId(null);
    onFocusChange?.(false);
  }

  function goToPreviousAsset() {
    if (!selectedAssetId || focusedAssetIndex <= 0) {
      return;
    }
    setSelectedAssetId(browseAssetIds[focusedAssetIndex - 1]);
  }

  function goToNextAsset() {
    if (!selectedAssetId || focusedAssetIndex < 0) {
      return;
    }
    if (focusedAssetIndex < browseAssetIds.length - 1) {
      setSelectedAssetId(browseAssetIds[focusedAssetIndex + 1]);
      return;
    }
    if (assets?.hasNext && !loadingMore && onLoadMore) {
      pendingNextNavigationRef.current = true;
      loadMoreRequestedRef.current = true;
      onLoadMore();
    }
  }

  async function openPicker(kind: 'albums' | 'tags') {
    setContextMenu(null);
    setPickerKind(kind);
    setPickerLoading(true);
    try {
      setPickerDestinations(
        kind === 'albums'
          ? (await fetchAlbums()).map((album) => ({ id: album.id, name: album.name }))
          : (await fetchTags()).map((tag) => ({ id: tag.id, name: tag.name }))
      );
    } catch {
      setBrowseError(`Could not load ${kind}.`);
      setPickerKind(null);
    } finally {
      setPickerLoading(false);
    }
  }

  async function addToStarred() {
    if (!auth || selectedAssets.length === 0) {
      return;
    }
    setContextMenu(null);
    try {
      const starred = await fetchStarred();
      const items = selectedAssets.map((asset) => ({ assetId: asset.id, sourceLibraryId: asset.libraryId }));
      await addAlbumItems([starred.id], items, auth.csrfToken);
      setStarredOverrides((current) => {
        const next = new Map(current);
        for (const asset of selectedAssets) {
          next.set(asset.id, true);
        }
        return next;
      });
      onAssignmentsChanged?.();
    } catch {
      setBrowseError('Could not add to starred.');
    }
  }

  async function removeFromStarred() {
    if (!auth || selectedAssets.length === 0) {
      return;
    }
    setContextMenu(null);
    try {
      const starred = await fetchStarred();
      await removeAlbumItems(
        starred.id,
        selectedAssets.map((asset) => asset.id),
        auth.csrfToken
      );
      setStarredOverrides((current) => {
        const next = new Map(current);
        for (const asset of selectedAssets) {
          next.set(asset.id, false);
        }
        return next;
      });
      onAssignmentsChanged?.();
    } catch {
      setBrowseError('Could not remove from starred.');
    }
  }

  function handleTileContextMenu(asset: AssetSummary, event: MouseEvent) {
    event.preventDefault();
    if (!selection.isSelected(asset.id)) {
      selection.selectOnly(asset.id);
    }
    setContextMenu({ x: event.clientX, y: event.clientY, assetId: asset.id });
  }

  function openFocusedAssetActions(x: number, y: number) {
    if (!selectedAssetId) {
      return;
    }
    selection.selectOnly(selectedAssetId);
    setContextMenu({ x, y, assetId: selectedAssetId });
  }

  function handleTileDragStart(asset: AssetSummary, event: DragEvent) {
    const dragging = selection.isSelected(asset.id) ? selectedAssets : [asset];
    if (!selection.isSelected(asset.id)) {
      selection.selectOnly(asset.id);
    }
    writeAssetDragData(event.dataTransfer, {
      assetIds: dragging.map((item) => item.id),
      items: dragging.map((item) => ({ assetId: item.id, sourceLibraryId: item.libraryId }))
    });
  }

  const selectionIsStarred = selectedAssets.length > 0 && selectedAssets.every((asset) => asset.starred);
  const contextMenuActions: AssetContextMenuAction[] = [
    selectionIsStarred
      ? {
          id: 'remove-starred',
          icon: StarOff,
          label: 'Remove from starred',
          onSelect: () => void removeFromStarred()
        }
      : {
          id: 'starred',
          icon: Star,
          label: 'Add to starred',
          onSelect: () => void addToStarred()
        },
    { id: 'albums', icon: Images, label: 'Add to albums…', onSelect: () => void openPicker('albums') },
    { id: 'tags', icon: Hash, label: 'Add tags…', onSelect: () => void openPicker('tags') },
    ...(typeof extraContextActions === 'function'
      ? extraContextActions({
          selectedIds: selection.selectedIds,
          selectedAssets,
          contextAsset: contextAsset ?? selectedAssetSummary ?? null,
          clearSelection: selection.clear
        })
      : (extraContextActions ?? []))
  ];

  const assignmentPicker =
    pickerKind && auth ? (
      <AssignmentPicker
        createVerb={pickerKind === 'albums' ? 'album' : 'tag'}
        destinations={pickerDestinations}
        loading={pickerLoading}
        onApply={async (ids) => {
          const items = selectedAssets.map((asset) => ({ assetId: asset.id, sourceLibraryId: asset.libraryId }));
          if (pickerKind === 'albums') {
            await addAlbumItems(ids, items, auth.csrfToken);
          } else {
            await addAssetTags(ids, items, auth.csrfToken);
          }
          onAssignmentsChanged?.();
        }}
        onClose={() => setPickerKind(null)}
        onCreate={async (name) => {
          const result =
            pickerKind === 'albums'
              ? await createAlbum({ name }, auth.csrfToken)
              : await createTag({ name }, auth.csrfToken);
          const destination = { id: result.id, name: result.name };
          setPickerDestinations((current) => [...current, destination]);
          return destination;
        }}
        open
        title={pickerKind === 'albums' ? 'Add to albums' : 'Add tags'}
      />
    ) : null;

  const assetActionsMenu = (
    <AssetContextMenu
      actions={contextMenuActions}
      onClose={() => setContextMenu(null)}
      open={contextMenu !== null}
      x={contextMenu?.x ?? 0}
      y={contextMenu?.y ?? 0}
    />
  );

  if (selectedAssetId) {
    return (
      <div className="h-full min-h-0 min-w-0 flex-1">
        <AssetFocus
          asset={assetDetail}
          cacheKey={selectedAssetSummary?.thumbnailCacheKey}
          starred={selectedAssetSummary?.starred ?? false}
          hasNext={canGoToNextAsset}
          hasPrevious={canGoToPreviousAsset}
          loading={!assetDetail}
          onClose={closeAssetFocus}
          onContextMenu={(event) => openFocusedAssetActions(event.clientX, event.clientY)}
          onNext={goToNextAsset}
          onOpenActions={({ x, y }) => openFocusedAssetActions(x, y)}
          onPrevious={goToPreviousAsset}
        />
        {assetActionsMenu}
        {assignmentPicker}
      </div>
    );
  }

  return (
    <section className={className ?? 'flex min-h-0 min-w-0 flex-col overflow-hidden px-0 lg:px-6'}>
      <div className="shrink-0 bg-background pb-4">
        <div className="flex items-end gap-2">
          {leadingControls}
          <div className="flex min-w-0 flex-1 items-end justify-between gap-2">
            <div className="min-w-0">
              {subtitle}
              <div className="flex min-w-0 flex-wrap items-baseline gap-2">
                {title}
                {displayAssets && (
                  <span className="shrink-0 text-sm text-muted-foreground">{formatItemCount(displayAssets.totalCount)}</span>
                )}
              </div>
              {description}
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {displayAssets && displayAssets.sections.length > 0 && (
                <ThumbnailSizeControls
                  onChange={(value) => {
                    setAssetTileSizeIndex(value);
                    writeStoredAssetTileSizeIndex(value);
                  }}
                  value={assetTileSizeIndex}
                />
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto [overflow-anchor:none]" ref={scrollContainerRef}>
        {(error || browseError) && <Alert>{browseError ?? error}</Alert>}
        {loadingAssets && <p className="text-sm text-muted-foreground">Loading assets...</p>}
        {!loadingAssets && displayAssets?.sections.length === 0 && (
          <EmptyPanel description={emptyDescription} icon={FileImage} title={emptyTitle} />
        )}
        {displayAssets && displayAssets.sections.length > 0 && (
          <AssetGrid
            assetTileSize={assetTileSize}
            onClearSelection={selection.clear}
            onContextMenu={handleTileContextMenu}
            onDragStart={handleTileDragStart}
            onOpen={openAsset}
            onRenameSection={onRenameSection}
            onSelectClick={selection.selectClick}
            orderedIds={browseAssetIds}
            selectedIds={selection.selectedIds}
            sections={displayAssets.sections}
            showSectionHeaders={showSectionHeaders}
          />
        )}
        {displayAssets && displayAssets.hasNext && onLoadMore && <div aria-hidden className="h-px shrink-0" ref={loadMoreRef} />}
        {loadingMore && <p className="pb-4 text-sm text-muted-foreground">Loading more...</p>}
      </div>

      {assetActionsMenu}
      {assignmentPicker}
    </section>
  );
}
