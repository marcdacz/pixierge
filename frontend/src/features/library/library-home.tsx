import {
  AlertTriangle,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  FileImage,
  Folder,
  FolderOpen,
  HardDrive,
  Info,
  Images,
  SlidersHorizontal,
  X
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ComponentType, type ReactNode } from 'react';
import {
  assetFileUrl,
  assetPreviewUrl,
  assetThumbnailUrl,
  fetchAsset,
  fetchAssets,
  fetchLibraryTree,
  type AssetBrowseResponse,
  type AssetDetail,
  type AssetSection,
  type AssetSummary,
  type LibrarySummary,
  type LibraryTreeNode
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

const ASSET_AVAILABILITY_MISSING = 'missing';
const ASSET_DUPLICATE_BASE_COUNT = 1;
const ASSET_IDENTITY_PENDING = 'pending';
const ASSET_METADATA_PENDING_LABEL = 'pending';
const ASSET_STATUS_ACTIVE = 'active';
const ASSET_FOCUS_LAYOUT_CLASS = 'min-h-[calc(100vh-var(--shell-header-height)-4rem)]';
const ASSET_FOCUS_CONTROLS_HIDE_DELAY_MS = 2500;
const ASSET_PREVIEW_MIN_HEIGHT_CLASS = 'min-h-[60vh]';
const BROWSE_LAYOUT_HEIGHT_CLASS =
  'h-[calc(100vh-var(--shell-header-height)-3rem)] max-h-[calc(100vh-var(--shell-header-height)-3rem)] lg:h-[calc(100vh-var(--shell-header-height)-4rem)] lg:max-h-[calc(100vh-var(--shell-header-height)-4rem)]';
const BROWSE_TREE_WIDTH_TOKEN = '[--browse-tree-width:16rem]';
const DETAIL_ROW_COLUMNS_CLASS = 'grid-cols-[6rem_minmax(0,1fr)]';
const FOLDER_TREE_BASE_PADDING_REM = 0.5;
const FOLDER_TREE_INDENT_REM = 0.75;
const LIBRARY_STATUS_ACTIVE = 'active';
const LOAD_MORE_ROOT_MARGIN = '240px';
const PAGE_SIZE = 48;
const THUMBNAIL_SIZE_SLIDER_WIDTH_CLASS = 'w-28';
const ASSET_TILE_SIZE_OPTIONS = [
  { key: 'tiny', minWidth: '5.5rem', imageSource: 'grid' },
  { key: 'compact', minWidth: '7rem', imageSource: 'grid' },
  { key: 'comfortable', minWidth: '11rem', imageSource: 'grid' },
  { key: 'large', minWidth: '15rem', imageSource: 'preview' },
  { key: 'xlarge', minWidth: '20rem', imageSource: 'preview' },
  { key: 'huge', minWidth: '28rem', imageSource: 'preview' }
] as const;
const DEFAULT_ASSET_TILE_SIZE_INDEX = 2;
const MAX_ASSET_TILE_SIZE_INDEX = ASSET_TILE_SIZE_OPTIONS.length - 1;
const ASSET_PLACEHOLDER_HUE_RANGE = 360;
const ASSET_PLACEHOLDER_SECONDARY_HUE_OFFSET = 32;
const ASSET_PLACEHOLDER_TERTIARY_HUE_OFFSET = 68;
const ASSET_PLACEHOLDER_SATURATION_PERCENT = 34;
const ASSET_PLACEHOLDER_LIGHTNESS_BASE_PERCENT = 28;
const ASSET_PLACEHOLDER_LIGHTNESS_RANGE_PERCENT = 18;

type AssetTileSize = (typeof ASSET_TILE_SIZE_OPTIONS)[number];

type LibraryHomeProps = {
  error?: string | null;
  libraries?: LibrarySummary[];
  loading?: boolean;
  onConfigureSources?: () => void;
  searchQuery?: string;
  variant?: 'libraries' | 'albums';
};

export function LibraryHome({
  error = null,
  libraries = [],
  loading = false,
  onConfigureSources,
  searchQuery = '',
  variant = 'libraries'
}: LibraryHomeProps) {
  const isAlbums = variant === 'albums';
  const librariesWithSources = libraries.filter(
    (library) => library.status === LIBRARY_STATUS_ACTIVE && library.sourceCount > 0
  );

  if (isAlbums) {
    return <EmptyPanel icon={Images} title="No albums yet" description="Album creation is not available yet." />;
  }

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading libraries...</p>;
  }

  if (librariesWithSources.length === 0) {
    return (
      <div className="grid gap-8">
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold text-foreground">Libraries</h1>
          <p className="text-sm text-muted-foreground">No library sources have been added yet.</p>
        </div>
        {error && <Alert>{error}</Alert>}
        <EmptyPanel
          action={onConfigureSources ? (
            <Button className="mt-2" onClick={onConfigureSources} type="button">
              <SlidersHorizontal className="h-4 w-4" aria-hidden />
              Configure sources
            </Button>
          ) : null}
          description="Configure named libraries and source folders to start building your catalog."
          icon={FolderOpen}
          title="Empty library"
        />
      </div>
    );
  }

  return <LibraryBrowser error={error} libraries={librariesWithSources} searchQuery={searchQuery} />;
}

function LibraryBrowser({
  error,
  libraries,
  searchQuery
}: {
  error: string | null;
  libraries: LibrarySummary[];
  searchQuery: string;
}) {
  const [isLowResolution, setIsLowResolution] = useState(false);
  const defaultLibraryId = libraries.length === 1 ? libraries[0]?.id : undefined;
  const [tree, setTree] = useState<LibraryTreeNode[]>([]);
  const [libraryRootAssetCounts, setLibraryRootAssetCounts] = useState<Record<string, number>>({});
  const [libraryAssetCounts, setLibraryAssetCounts] = useState<Record<string, number>>({});
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null);
  const [selectedLibraryId, setSelectedLibraryId] = useState<string | undefined>(defaultLibraryId);
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => new Set());
  const [expandedLibraries, setExpandedLibraries] = useState<Set<string>>(() => new Set());
  const [treeCollapsed, setTreeCollapsed] = useState(false);
  const query = searchQuery.trim();
  const [page, setPage] = useState(0);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [assetDetail, setAssetDetail] = useState<AssetDetail | null>(null);
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [loadingTree, setLoadingTree] = useState(false);
  const [treeLoaded, setTreeLoaded] = useState(false);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [assetTileSizeIndex, setAssetTileSizeIndex] = useState(DEFAULT_ASSET_TILE_SIZE_INDEX);
  const [browseError, setBrowseError] = useState<string | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);
  const loadMoreRequestedRef = useRef(false);
  const hasNextRef = useRef(false);
  const lastBrowseScrollTopRef = useRef(0);
  const pendingRestoreBrowseScrollRef = useRef(false);
  const focusReturnAssetIdRef = useRef<string | null>(null);
  const pendingNextNavigationRef = useRef(false);

  const selectedLibrary =
    libraries.find((library) => library.id === selectedLibraryId) ??
    libraries.find((library) => library.id === defaultLibraryId) ??
    libraries[0];
  const browseContextKey = `${selectedLibraryId ?? defaultLibraryId ?? ''}:${selectedFolder ?? ''}:${query}`;
  const librarySections = useMemo(
    () => groupTreeByLibrary(tree, libraries, libraryRootAssetCounts, libraryAssetCounts),
    [libraries, libraryAssetCounts, libraryRootAssetCounts, tree]
  );
  const assetTileSize = ASSET_TILE_SIZE_OPTIONS[assetTileSizeIndex];
  const showFloatingTree = isLowResolution && !treeCollapsed;
  const browseAssetIds = useMemo(() => flattenBrowseAssetIds(assets?.sections ?? []), [assets?.sections]);
  const selectedAssetSummary = useMemo(
    () => assets?.sections.flatMap((section) => section.assets).find((asset) => asset.id === selectedAssetId),
    [assets?.sections, selectedAssetId]
  );
  const focusedAssetIndex = selectedAssetId ? browseAssetIds.indexOf(selectedAssetId) : -1;
  const canGoToPreviousAsset = focusedAssetIndex > 0;
  const canGoToNextAsset =
    (focusedAssetIndex >= 0 && focusedAssetIndex < browseAssetIds.length - 1) ||
    (focusedAssetIndex === browseAssetIds.length - 1 && (assets?.hasNext ?? false));

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      setIsLowResolution(false);
      return;
    }

    const mediaQuery = window.matchMedia('(max-width: 1023px)');
    const syncResolution = () => {
      setIsLowResolution(mediaQuery.matches);
      if (mediaQuery.matches) {
        setTreeCollapsed(true);
      }
    };

    syncResolution();
    mediaQuery.addEventListener('change', syncResolution);
    return () => {
      mediaQuery.removeEventListener('change', syncResolution);
    };
  }, []);

  useEffect(() => {
    setSelectedLibraryId(defaultLibraryId);
  }, [defaultLibraryId]);

  useEffect(() => {
    let ignore = false;
    setLoadingTree(true);
    setTreeLoaded(false);
    setBrowseError(null);
    setSelectedFolder(null);
    setExpandedPaths(new Set());
    setExpandedLibraries(new Set(libraries.map((library) => library.id)));

    fetchLibraryTree(defaultLibraryId)
      .then((response) => {
        if (ignore) {
          return;
        }
        setTree(response.roots);
        setLibraryRootAssetCounts(response.libraryRootAssetCounts ?? {});
        setLibraryAssetCounts(response.libraryAssetCounts ?? {});
        setExpandedPaths(new Set(collectExpandablePaths(response.roots)));
        setExpandedLibraries(new Set(libraries.map((library) => library.id)));
        setTreeLoaded(true);
      })
      .catch(() => {
        if (!ignore) {
          setBrowseError('Folders could not be loaded.');
          setTree([]);
          setLibraryRootAssetCounts({});
          setLibraryAssetCounts({});
          setTreeLoaded(true);
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoadingTree(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [defaultLibraryId]);

  useEffect(() => {
    setPage(0);
    setAssets(null);
  }, [browseContextKey]);

  useEffect(() => {
    if (!treeLoaded) {
      return;
    }

    let ignore = false;
    const requestedPage = page;
    const requestedContext = browseContextKey;
    const isInitialLoad = requestedPage === 0;

    if (isInitialLoad) {
      setLoadingAssets(true);
    } else {
      setLoadingMore(true);
    }
    setBrowseError(null);

    fetchAssets({
      libraryId: selectedLibraryId ?? defaultLibraryId,
      folder: selectedFolder ?? undefined,
      includeDescendants: selectedFolder ? true : undefined,
      q: query,
      page: requestedPage,
      pageSize: PAGE_SIZE
    })
      .then((response) => {
        if (ignore || requestedContext !== browseContextKey) {
          return;
        }
        if (requestedPage === 0) {
          setAssets(response);
        } else {
          setAssets((current) =>
            current
              ? {
                  ...response,
                  sections: mergeBrowseSections(current.sections, response.sections)
                }
              : response
          );
        }
      })
      .catch(() => {
        if (!ignore && requestedContext === browseContextKey) {
          setBrowseError('Assets could not be loaded.');
          if (requestedPage === 0) {
            setAssets(null);
          }
        }
      })
      .finally(() => {
        if (!ignore && requestedContext === browseContextKey) {
          if (isInitialLoad) {
            setLoadingAssets(false);
          } else {
            setLoadingMore(false);
          }
        }
      });

    return () => {
      ignore = true;
    };
  }, [browseContextKey, defaultLibraryId, page, query, selectedFolder, selectedLibraryId, treeLoaded]);

  useEffect(() => {
    hasNextRef.current = assets?.hasNext ?? false;
  }, [assets?.hasNext]);

  useEffect(() => {
    if (!loadingMore) {
      loadMoreRequestedRef.current = false;
    }
  }, [loadingMore]);

  useEffect(() => {
    const sentinel = loadMoreRef.current;
    const scrollRoot = scrollContainerRef.current;
    if (!sentinel || !scrollRoot || !assets?.hasNext || loadingAssets || loadingMore) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (
          !entries.some((entry) => entry.isIntersecting) ||
          loadMoreRequestedRef.current ||
          !hasNextRef.current
        ) {
          return;
        }
        loadMoreRequestedRef.current = true;
        setPage((current) => current + 1);
      },
      { root: scrollRoot, rootMargin: LOAD_MORE_ROOT_MARGIN }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [assets?.hasNext, browseContextKey, loadingAssets, loadingMore]);

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

  function toggleLibraryExpanded(libraryId: string) {
    setExpandedLibraries((current) => {
      const next = new Set(current);
      if (next.has(libraryId)) {
        next.delete(libraryId);
      } else {
        next.add(libraryId);
      }
      return next;
    });
  }

  function updateFolder(path: string | null, libraryId?: string) {
    setPage(0);
    setSelectedFolder(path);
    setSelectedLibraryId(libraryId ?? defaultLibraryId);
    setSelectedAssetId(null);
  }

  function toggleExpanded(path: string) {
    setExpandedPaths((current) => {
      const next = new Set(current);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }

  function openAsset(assetId: string) {
    lastBrowseScrollTopRef.current = scrollContainerRef.current?.scrollTop ?? 0;
    focusReturnAssetIdRef.current = null;
    pendingNextNavigationRef.current = false;
    setSelectedAssetId(assetId);
  }

  function closeAssetFocus() {
    focusReturnAssetIdRef.current = selectedAssetId;
    pendingRestoreBrowseScrollRef.current = true;
    setSelectedAssetId(null);
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
    if (assets?.hasNext && !loadingMore) {
      pendingNextNavigationRef.current = true;
      loadMoreRequestedRef.current = true;
      setPage((current) => current + 1);
    }
  }

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
    if (selectedAssetId || !pendingRestoreBrowseScrollRef.current) {
      return;
    }

    const frame = requestAnimationFrame(() => {
      const returnAssetId = focusReturnAssetIdRef.current;
      if (returnAssetId) {
        const tile = scrollContainerRef.current?.querySelector(`[data-asset-id="${returnAssetId}"]`);
        tile?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        focusReturnAssetIdRef.current = null;
      } else if (scrollContainerRef.current) {
        scrollContainerRef.current.scrollTop = lastBrowseScrollTopRef.current;
      }
      pendingRestoreBrowseScrollRef.current = false;
    });

    return () => cancelAnimationFrame(frame);
  }, [selectedAssetId]);

  if (selectedAssetId) {
    return (
      <AssetFocus
        asset={assetDetail}
        cacheKey={selectedAssetSummary?.thumbnailCacheKey}
        hasNext={canGoToNextAsset}
        hasPrevious={canGoToPreviousAsset}
        loading={!assetDetail}
        onClose={closeAssetFocus}
        onNext={goToNextAsset}
        onPrevious={goToPreviousAsset}
      />
    );
  }

  return (
    <div
      className={cn(
        'relative grid gap-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[minmax(0,1fr)]',
        !isLowResolution && !treeCollapsed && 'lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {showFloatingTree && (
        <button
          aria-label="Close folders"
          className="absolute inset-0 z-10 bg-background/40 backdrop-blur-[1px]"
          onClick={() => setTreeCollapsed(true)}
          type="button"
        />
      )}

      {!treeCollapsed && (
        <aside
          className={cn(
            'flex min-h-0 flex-col',
            isLowResolution
              ? 'absolute inset-y-0 left-0 z-20 w-[min(20rem,85vw)] border-r border-border bg-background px-3 py-4 shadow-xl'
              : 'w-full border-b border-border pb-4 lg:w-[var(--browse-tree-width)] lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4',
            BROWSE_TREE_WIDTH_TOKEN
          )}
        >
          <div className="mb-3 flex shrink-0 items-center justify-between gap-2">
            <h2 className="text-sm font-medium text-foreground">Folders</h2>
            <Button aria-label="Hide folders" onClick={() => setTreeCollapsed(true)} size="icon" type="button" variant="ghost">
              <ChevronsLeft className="h-4 w-4" aria-hidden />
            </Button>
          </div>

          <nav
            aria-label="Folders"
            className={cn('flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto', isLowResolution ? 'pr-0' : 'pr-1')}
          >
            {loadingTree && <p className="px-2 py-2 text-sm text-muted-foreground">Loading folders...</p>}
            {!loadingTree && tree.length === 0 && (assets?.totalCount ?? 0) === 0 && (
              <p className="px-2 py-2 text-sm text-muted-foreground">No scanned folders yet.</p>
            )}
            {librarySections.map((section) => (
              <LibraryFolderSection
                expandedLibraries={expandedLibraries}
                expandedPaths={expandedPaths}
                key={section.libraryId}
                libraryId={section.libraryId}
                nodes={section.nodes}
                onClearSelection={() => updateFolder(null, section.libraryId)}
                onSelect={(path) => updateFolder(path, section.libraryId)}
                onToggleExpanded={toggleExpanded}
                onToggleLibraryExpanded={toggleLibraryExpanded}
                selectedFolder={selectedFolder}
                selectedLibraryId={selectedLibraryId}
                title={section.libraryName}
                totalCount={section.totalCount}
              />
            ))}
          </nav>
        </aside>
      )}

      <section className="flex min-h-0 min-w-0 flex-col overflow-hidden px-0 lg:px-6">
        <div className="shrink-0 bg-background pb-4">
          <div className="flex items-end gap-2">
            {treeCollapsed && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    aria-label="Show folders"
                    onClick={() => setTreeCollapsed(false)}
                    size="icon"
                    type="button"
                    variant="ghost"
                  >
                    <ChevronsRight className="h-4 w-4" aria-hidden />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="right">Show folders</TooltipContent>
              </Tooltip>
            )}
            <div className="flex min-w-0 flex-1 items-end justify-between gap-2">
              <div className="min-w-0">
                <p className="text-sm text-muted-foreground">{selectedLibrary.name}</p>
                <div className="flex min-w-0 items-baseline gap-2">
                  <h2 className="truncate text-2xl font-semibold text-foreground">
                    {selectedFolder ? folderName(selectedFolder) : 'All folders'}
                  </h2>
                  {assets && (
                    <span className="shrink-0 text-sm text-muted-foreground">
                      {formatItemCount(assets.totalCount)}
                    </span>
                  )}
                </div>
              </div>
              <ThumbnailSizeControls
                onChange={setAssetTileSizeIndex}
                value={assetTileSizeIndex}
              />
            </div>
          </div>
        </div>

        <div
          className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto [overflow-anchor:none]"
          ref={scrollContainerRef}
        >
          {(error || browseError) && <Alert>{browseError ?? error}</Alert>}
          {loadingAssets && <p className="text-sm text-muted-foreground">Loading assets...</p>}
          {!loadingAssets && assets?.sections.length === 0 && (
            <EmptyPanel
              description="Run a scan from Settings, or adjust the current folder."
              icon={FileImage}
              title="No assets found"
            />
          )}
          {assets && assets.sections.length > 0 && (
            <AssetGrid
              assetTileSize={assetTileSize}
              onOpen={openAsset}
              sections={assets.sections}
              showSectionHeaders={!selectedFolder || assets.sections.length > 1}
            />
          )}
          {assets && assets.hasNext && <div aria-hidden className="h-px shrink-0" ref={loadMoreRef} />}
          {loadingMore && <p className="pb-4 text-sm text-muted-foreground">Loading more...</p>}
        </div>
      </section>
    </div>
  );
}

function LibraryFolderSection({
  expandedLibraries,
  expandedPaths,
  libraryId,
  nodes,
  onClearSelection,
  onSelect,
  onToggleExpanded,
  onToggleLibraryExpanded,
  selectedFolder,
  selectedLibraryId,
  title,
  totalCount
}: {
  expandedLibraries: Set<string>;
  expandedPaths: Set<string>;
  libraryId: string;
  nodes: LibraryTreeNode[];
  onClearSelection: () => void;
  onSelect: (path: string) => void;
  onToggleExpanded: (path: string) => void;
  onToggleLibraryExpanded: (libraryId: string) => void;
  selectedFolder: string | null;
  selectedLibraryId?: string;
  title: string;
  totalCount: number;
}) {
  const hasChildren = nodes.length > 0;
  const expanded = expandedLibraries.has(libraryId);
  const active = selectedFolder === null && selectedLibraryId === libraryId;

  return (
    <section className="grid gap-0.5">
      <FolderTreeRow
        active={active}
        count={totalCount}
        depth={0}
        expanded={expanded}
        hasChildren={hasChildren}
        icon={HardDrive}
        label={title}
        onSelect={onClearSelection}
        onToggleExpanded={() => onToggleLibraryExpanded(libraryId)}
      />
      {hasChildren && expanded && (
        <div className="grid gap-0.5">
          {nodes.map((node) => (
            <FolderTreeNode
              depth={1}
              expandedPaths={expandedPaths}
              key={node.id}
              node={node}
              onSelect={onSelect}
              onToggleExpanded={onToggleExpanded}
              selectedFolder={selectedFolder}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function FolderTreeNode({
  depth,
  expandedPaths,
  node,
  onSelect,
  onToggleExpanded,
  selectedFolder
}: {
  depth: number;
  expandedPaths: Set<string>;
  node: LibraryTreeNode;
  onSelect: (path: string) => void;
  onToggleExpanded: (path: string) => void;
  selectedFolder: string | null;
}) {
  const hasChildren = node.children.length > 0;
  const expanded = expandedPaths.has(node.path);
  const active = selectedFolder === node.path;

  return (
    <div className="grid gap-0.5">
      <FolderTreeRow
        active={active}
        count={node.assetCount}
        depth={depth}
        expanded={expanded}
        hasChildren={hasChildren}
        label={node.name}
        onSelect={() => onSelect(node.path)}
        onToggleExpanded={() => onToggleExpanded(node.path)}
      />
      {hasChildren && expanded && node.children.map((child) => (
        <FolderTreeNode
          depth={depth + 1}
          expandedPaths={expandedPaths}
          key={child.id}
          node={child}
          onSelect={onSelect}
          onToggleExpanded={onToggleExpanded}
          selectedFolder={selectedFolder}
        />
      ))}
    </div>
  );
}

function FolderTreeRow({
  active,
  count,
  depth,
  expanded,
  hasChildren,
  icon,
  label,
  onSelect,
  onToggleExpanded
}: {
  active: boolean;
  count: number;
  depth: number;
  expanded: boolean;
  hasChildren: boolean;
  icon?: ComponentType<{ className?: string }>;
  label: string;
  onSelect: () => void;
  onToggleExpanded: () => void;
}) {
  const DefaultIcon = active ? FolderOpen : Folder;
  const RowIcon = icon ?? DefaultIcon;

  return (
    <div
      className={cn(
        'flex min-h-7 items-center gap-1 rounded-md pr-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
        active && 'bg-muted text-foreground'
      )}
      style={{ paddingLeft: `${FOLDER_TREE_BASE_PADDING_REM + depth * FOLDER_TREE_INDENT_REM}rem` }}
    >
      {hasChildren ? (
        <button
          aria-expanded={expanded}
          aria-label={expanded ? `Collapse ${label}` : `Expand ${label}`}
          className="grid h-6 w-5 shrink-0 place-items-center rounded-sm text-muted-foreground hover:text-foreground"
          onClick={(event) => {
            event.stopPropagation();
            onToggleExpanded();
          }}
          type="button"
        >
          {expanded ? (
            <ChevronDown className="h-3.5 w-3.5" aria-hidden />
          ) : (
            <ChevronRight className="h-3.5 w-3.5" aria-hidden />
          )}
        </button>
      ) : (
        <span className="w-5 shrink-0" aria-hidden />
      )}
      <button
        className="flex min-h-7 min-w-0 flex-1 items-center gap-2 text-left"
        onClick={onSelect}
        type="button"
      >
        <RowIcon className="h-4 w-4 shrink-0" aria-hidden />
        <span className="min-w-0 flex-1 truncate">
          {label} <span className="text-xs tabular-nums">({count})</span>
        </span>
      </button>
    </div>
  );
}

function AssetGrid({
  assetTileSize,
  onOpen,
  sections,
  showSectionHeaders = true
}: {
  assetTileSize: AssetTileSize;
  onOpen: (assetId: string) => void;
  sections: AssetBrowseResponse['sections'];
  showSectionHeaders?: boolean;
}) {
  const gridStyle = assetGridStyle(assetTileSize);

  return (
    <div className="grid gap-2">
      {sections.map((section) => (
        <section className="grid gap-2" key={section.folderPath}>
          {showSectionHeaders && (
            <div className="bg-background py-1">
              <h3 className="text-lg font-semibold text-foreground">{section.folderName}</h3>
            </div>
          )}
          <div aria-label="Asset grid" className="grid gap-1" style={gridStyle}>
            {section.assets.map((asset) => (
              <AssetTile
                asset={asset}
                imageSource={assetTileSize.imageSource}
                key={asset.id}
                onOpen={() => onOpen(asset.id)}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function ThumbnailSizeControls({
  onChange,
  value
}: {
  onChange: (value: number) => void;
  value: number;
}) {
  return (
    <div className="flex h-10 items-center" title="Thumbnail size">
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
    </div>
  );
}

function assetGridStyle(tileSize: AssetTileSize): CSSProperties {
  return {
    '--asset-grid-tile-size': tileSize.minWidth,
    gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, var(--asset-grid-tile-size)), 1fr))'
  } as CSSProperties;
}

function AssetTile({
  asset,
  imageSource,
  onOpen
}: {
  asset: AssetSummary;
  imageSource: AssetTileSize['imageSource'];
  onOpen: () => void;
}) {
  const [tinyThumbnailFailed, setTinyThumbnailFailed] = useState(false);
  const [thumbnailLoaded, setThumbnailLoaded] = useState(false);
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
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
  }, [sharpSrc]);

  return (
    <button
      aria-label={`Open ${asset.fileName}`}
      className="group relative aspect-[4/3] min-w-0 overflow-hidden bg-muted text-left"
      data-asset-id={asset.id}
      onClick={onOpen}
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
            src={sharpSrc}
          />
        </>
      ) : (
        <div className="grid h-full place-items-center">
          <FileImage className="h-8 w-8 text-muted-foreground" aria-hidden />
        </div>
      )}
      <div className="absolute inset-x-0 bottom-0 flex min-h-9 items-end justify-between gap-2 bg-gradient-to-t from-background/85 to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
        <span className="truncate text-xs font-medium text-foreground">{asset.fileName}</span>
        <span className="flex shrink-0 gap-1">
          {asset.identityStatus === ASSET_IDENTITY_PENDING && <Badge variant="secondary">Identity pending</Badge>}
          {asset.availability === ASSET_AVAILABILITY_MISSING && <Badge variant="warning">Missing</Badge>}
          {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && <Badge variant="secondary">{asset.duplicateCount}</Badge>}
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

function AssetFocus({
  asset,
  cacheKey,
  hasNext,
  hasPrevious,
  loading,
  onClose,
  onNext,
  onPrevious
}: {
  asset: AssetDetail | null;
  cacheKey?: string | null;
  hasNext: boolean;
  hasPrevious: boolean;
  loading: boolean;
  onClose: () => void;
  onNext: () => void;
  onPrevious: () => void;
}) {
  const activeFile = asset?.files?.find((file) => file.status === ASSET_STATUS_ACTIVE);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [showMetadata, setShowMetadata] = useState(false);
  const [controlsVisible, setControlsVisible] = useState(false);
  const hideControlsTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const previewSrc = asset ? (previewFailed ? assetFileUrl(asset.id) : assetPreviewUrl(asset.id, cacheKey)) : null;
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

  useEffect(() => {
    setPreviewFailed(false);
  }, [asset?.id]);

  useEffect(() => {
    setShowMetadata(false);
    revealControls();
    return () => {
      if (hideControlsTimeoutRef.current) {
        clearTimeout(hideControlsTimeoutRef.current);
      }
    };
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
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [hasNext, hasPrevious, onClose, onNext, onPrevious]);

  return (
    <div className={cn('grid min-h-0 gap-4', ASSET_FOCUS_LAYOUT_CLASS)}>
      {loading && <p className="text-sm text-muted-foreground">Loading asset...</p>}
      {asset && (
        <div
          className="relative min-h-0"
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
            {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && <Badge variant="secondary">{asset.duplicateCount} files</Badge>}
            <Button
              aria-label={showMetadata ? 'Hide photo metadata' : 'Show photo metadata'}
              onClick={() => setShowMetadata((current) => !current)}
              size="icon"
              type="button"
              variant="secondary"
            >
              <Info className="h-4 w-4" aria-hidden />
            </Button>
          </div>
          <div className={cn('grid min-h-0 place-items-center bg-black', ASSET_PREVIEW_MIN_HEIGHT_CLASS)}>
            {activeFile ? (
              <img
                alt=""
                className="max-h-full max-w-full object-contain"
                onError={() => setPreviewFailed(true)}
                src={previewSrc ?? undefined}
              />
            ) : (
              <div className="grid justify-items-center gap-3 text-muted-foreground">
                <FileImage className="h-10 w-10" aria-hidden />
                <p>No active file available</p>
              </div>
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
                  <div className="grid gap-1">
                    <h1 className="break-words text-xl font-semibold text-foreground">{activeFile?.fileName ?? 'Asset'}</h1>
                    <p className="break-all text-sm text-muted-foreground">{activeFile?.path ?? asset.contentHash}</p>
                  </div>
                  <dl className="grid gap-3 text-sm">
                    <DetailRow label="Identity" value={asset.identityStatus === ASSET_IDENTITY_PENDING ? 'pending' : 'confirmed'} />
                    <DetailRow label="Type" value={asset.metadata.mimeType ?? asset.mediaType} />
                    <DetailRow label="Size" value={asset.metadata.width && asset.metadata.height ? `${asset.metadata.width} x ${asset.metadata.height}` : 'Unknown'} />
                    <DetailRow label="Captured" value={formatDate(asset.metadata.capturedAt)} />
                    <DetailRow label="Metadata" value={asset.metadata.extractionStatus ?? ASSET_METADATA_PENDING_LABEL} />
                  </dl>
                  <div className="grid gap-2">
                    <h2 className="text-sm font-semibold text-foreground">Files</h2>
                    <div className="grid gap-2">
                      {asset.files.map((file) => (
                        <div className="rounded-md border border-border p-3" key={file.id}>
                          <div className="flex items-center justify-between gap-2">
                            <span className="truncate text-sm font-medium text-foreground">{file.fileName}</span>
                            <Badge variant={file.status === ASSET_STATUS_ACTIVE ? 'secondary' : 'warning'}>{file.status}</Badge>
                          </div>
                          <p className="mt-1 break-all text-xs text-muted-foreground">{file.path}</p>
                        </div>
                      ))}
                    </div>
                  </div>
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

function EmptyPanel({
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

function folderName(path: string) {
  const index = path.lastIndexOf('/');
  return index >= 0 ? path.slice(index + 1) : path;
}

function formatItemCount(count: number) {
  return `${count} ${count === 1 ? 'item' : 'items'}`;
}

function flattenBrowseAssetIds(sections: AssetSection[]) {
  return sections.flatMap((section) => section.assets.map((asset) => asset.id));
}

function mergeBrowseSections(current: AssetSection[], next: AssetSection[]) {
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

function collectExpandablePaths(nodes: LibraryTreeNode[]): string[] {
  return nodes.flatMap((node) => [
    ...(node.children.length > 0 ? [node.path] : []),
    ...collectExpandablePaths(node.children)
  ]);
}

function groupTreeByLibrary(
  nodes: LibraryTreeNode[],
  libraries: LibrarySummary[],
  libraryRootAssetCounts: Record<string, number> = {},
  libraryAssetCounts: Record<string, number> = {}
) {
  const nodesByLibrary = new Map<string, LibraryTreeNode[]>();
  for (const node of nodes) {
    const current = nodesByLibrary.get(node.libraryId) ?? [];
    current.push(node);
    nodesByLibrary.set(node.libraryId, current);
  }

  return libraries.map((library) => {
    const libraryNodes = nodesByLibrary.get(library.id) ?? [];
    const rootAssetCount = libraryRootAssetCounts[library.id] ?? 0;
    const totalCount =
      libraryAssetCounts[library.id] ??
      libraryNodes.reduce((total, node) => total + node.assetCount, 0) + rootAssetCount;
    return {
      libraryId: library.id,
      libraryName: library.name,
      totalCount,
      nodes: libraryNodes
    };
  });
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Unknown';
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}
