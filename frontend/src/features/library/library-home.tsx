import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  FileImage,
  Folder,
  FolderOpen,
  HardDrive,
  Images,
  SlidersHorizontal,
  X
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type ComponentType, type ReactNode } from 'react';
import {
  assetFileUrl,
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
const ASSET_METADATA_PENDING_LABEL = 'pending';
const ASSET_STATUS_ACTIVE = 'active';
const ASSET_FOCUS_DETAIL_COLUMNS_CLASS = 'xl:grid-cols-[minmax(0,1fr)_22rem]';
const ASSET_FOCUS_LAYOUT_CLASS = 'min-h-[calc(100vh-var(--shell-header-height)-4rem)]';
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
  const defaultLibraryId = libraries.length === 1 ? libraries[0]?.id : undefined;
  const [tree, setTree] = useState<LibraryTreeNode[]>([]);
  const [libraryRootAssetCounts, setLibraryRootAssetCounts] = useState<Record<string, number>>({});
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
  const [browseError, setBrowseError] = useState<string | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  const selectedLibrary =
    libraries.find((library) => library.id === selectedLibraryId) ??
    libraries.find((library) => library.id === defaultLibraryId) ??
    libraries[0];
  const browseContextKey = `${selectedLibraryId ?? defaultLibraryId ?? ''}:${selectedFolder ?? ''}:${query}`;
  const librarySections = useMemo(
    () => groupTreeByLibrary(tree, libraries, libraryRootAssetCounts),
    [libraries, libraryRootAssetCounts, tree]
  );

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
        setExpandedPaths(new Set(collectExpandablePaths(response.roots)));
        setExpandedLibraries(new Set(libraries.map((library) => library.id)));
        setTreeLoaded(true);
      })
      .catch(() => {
        if (!ignore) {
          setBrowseError('Folders could not be loaded.');
          setTree([]);
          setLibraryRootAssetCounts({});
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
    const sentinel = loadMoreRef.current;
    const scrollRoot = scrollContainerRef.current;
    if (!sentinel || !scrollRoot || !assets?.hasNext || loadingAssets || loadingMore) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setPage((current) => current + 1);
        }
      },
      { root: scrollRoot, rootMargin: LOAD_MORE_ROOT_MARGIN }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [assets?.hasNext, assets?.sections, loadingAssets, loadingMore]);

  useEffect(() => {
    if (!selectedAssetId) {
      setAssetDetail(null);
      return;
    }

    let ignore = false;
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

  if (selectedAssetId) {
    return (
      <AssetFocus
        asset={assetDetail}
        loading={!assetDetail}
        onClose={() => setSelectedAssetId(null)}
      />
    );
  }

  return (
    <div
      className={cn(
        'grid gap-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[auto_minmax(0,1fr)]',
        !treeCollapsed && 'lg:grid-rows-none lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {!treeCollapsed && (
        <aside
          className={cn(
            'flex min-h-0 w-full flex-col border-b border-border pb-4 lg:w-[var(--browse-tree-width)] lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4',
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
            className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pr-1"
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
            <div className="flex min-w-0 flex-1 flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div className="min-w-0">
                <p className="text-sm text-muted-foreground">{selectedLibrary.name}</p>
                <h2 className="truncate text-2xl font-semibold text-foreground">
                  {selectedFolder ? folderName(selectedFolder) : 'All folders'}
                </h2>
              </div>
              {assets && (
                <span className="shrink-0 self-end text-sm text-muted-foreground">
                  {formatItemCount(assets.totalCount)}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto" ref={scrollContainerRef}>
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
              onOpen={setSelectedAssetId}
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
              depth={0}
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
        <span className="truncate">{label}</span>
        <span className="ml-auto text-xs tabular-nums">{count}</span>
      </button>
    </div>
  );
}

function AssetGrid({
  onOpen,
  sections,
  showSectionHeaders = true
}: {
  onOpen: (assetId: string) => void;
  sections: AssetBrowseResponse['sections'];
  showSectionHeaders?: boolean;
}) {
  return (
    <div className="grid gap-2">
      {sections.map((section) => (
        <section className="grid gap-2" key={section.folderPath}>
          {showSectionHeaders && (
            <div className="sticky top-0 z-10 bg-background py-1">
              <h3 className="text-lg font-semibold text-foreground">{section.folderName}</h3>
            </div>
          )}
          <div className="grid grid-cols-2 gap-1 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
            {section.assets.map((asset) => (
              <AssetTile asset={asset} key={asset.id} onOpen={() => onOpen(asset.id)} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function AssetTile({ asset, onOpen }: { asset: AssetSummary; onOpen: () => void }) {
  return (
    <button
      aria-label={`Open ${asset.fileName}`}
      className="group relative aspect-[4/3] min-w-0 overflow-hidden bg-muted text-left"
      onClick={onOpen}
      type="button"
    >
      {asset.previewable ? (
        <img
          alt=""
          className="h-full w-full object-cover transition-transform group-hover:scale-[1.02]"
          loading="lazy"
          src={assetFileUrl(asset.id)}
        />
      ) : (
        <div className="grid h-full place-items-center">
          <FileImage className="h-8 w-8 text-muted-foreground" aria-hidden />
        </div>
      )}
      <div className="absolute inset-x-0 bottom-0 flex min-h-9 items-end justify-between gap-2 bg-gradient-to-t from-background/85 to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
        <span className="truncate text-xs font-medium text-foreground">{asset.fileName}</span>
        <span className="flex shrink-0 gap-1">
          {asset.availability === ASSET_AVAILABILITY_MISSING && <Badge variant="warning">Missing</Badge>}
          {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && <Badge variant="secondary">{asset.duplicateCount}</Badge>}
        </span>
      </div>
    </button>
  );
}

function AssetFocus({
  asset,
  loading,
  onClose
}: {
  asset: AssetDetail | null;
  loading: boolean;
  onClose: () => void;
}) {
  const activeFile = asset?.files?.find((file) => file.status === ASSET_STATUS_ACTIVE);

  return (
    <div className={cn('grid grid-rows-[auto_minmax(0,1fr)] gap-4', ASSET_FOCUS_LAYOUT_CLASS)}>
      <div className="flex items-center justify-between gap-3">
        <Button onClick={onClose} type="button" variant="secondary">
          <X className="h-4 w-4" aria-hidden />
          Close
        </Button>
        {asset && (
          <div className="flex gap-2">
            {asset.availability === ASSET_AVAILABILITY_MISSING && <Badge variant="warning">Missing</Badge>}
            {asset.duplicateCount > ASSET_DUPLICATE_BASE_COUNT && <Badge variant="secondary">{asset.duplicateCount} files</Badge>}
          </div>
        )}
      </div>

      {loading && <p className="text-sm text-muted-foreground">Loading asset...</p>}
      {asset && (
        <div className={cn('grid min-h-0 gap-5', ASSET_FOCUS_DETAIL_COLUMNS_CLASS)}>
          <div className={cn('grid place-items-center bg-black', ASSET_PREVIEW_MIN_HEIGHT_CLASS)}>
            {activeFile ? (
              <img alt="" className="max-h-full max-w-full object-contain" src={assetFileUrl(asset.id)} />
            ) : (
              <div className="grid justify-items-center gap-3 text-muted-foreground">
                <FileImage className="h-10 w-10" aria-hidden />
                <p>No active file available</p>
              </div>
            )}
          </div>
          <aside className="grid content-start gap-5">
            <div className="grid gap-1">
              <h1 className="break-words text-xl font-semibold text-foreground">{activeFile?.fileName ?? 'Asset'}</h1>
              <p className="break-all text-sm text-muted-foreground">{activeFile?.path ?? asset.contentHash}</p>
            </div>
            <dl className="grid gap-3 text-sm">
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
          </aside>
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
      existing.assets.push(...section.assets);
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
  libraryRootAssetCounts: Record<string, number> = {}
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
    return {
      libraryId: library.id,
      libraryName: library.name,
      totalCount: libraryNodes.reduce((total, node) => total + node.assetCount, 0) + rootAssetCount,
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
